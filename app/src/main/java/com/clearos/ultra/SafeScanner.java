package com.clearos.ultra;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Offline-only, non-root scanner. Never deletes anything during scanning. */
public final class SafeScanner {
    private SafeScanner() {}

    public static final long MIB = 1024L * 1024L;
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;
    private static final int MAX_VISITS = 250_000;
    private static final int MAX_RESULTS = 20_000;
    private static final int MAX_DEPTH = 32;

    public enum Type {
        DUPLICATE("Birebir aynı dosya"),
        TEMP("Geçici dosya"),
        INSTALLER("Eski kurulum paketi"),
        ARCHIVE("Eski arşiv"),
        THUMBNAIL("Küçük resim önbelleği"),
        MESSENGER("Eski mesajlaşma medyası"),
        SCREENSHOT("Ekran görüntüsü"),
        LARGE("Büyük dosya"),
        EMPTY("Boş klasör"),
        LEFTOVER("Uygulama kalıntısı"),
        SIMILAR("Benzer fotoğraf"),
        BLURRY("Bulanık fotoğraf (olası)");
        public final String label;
        Type(String label) { this.label = label; }
    }

    public static final class Candidate {
        public final File file;
        public final File root;
        public final long size;
        public final long modified;
        public final Type type;
        public final boolean suggested;
        public final String reason;
        public final File duplicateKeep;
        public final String duplicateHash;

        Candidate(File file, File root, long size, long modified, Type type,
                  boolean suggested, String reason, File keep, String hash) {
            this.file = file;
            this.root = root;
            this.size = size;
            this.modified = modified;
            this.type = type;
            this.suggested = suggested;
            this.reason = reason;
            this.duplicateKeep = keep;
            this.duplicateHash = hash;
        }

        public String key() { return file.getAbsolutePath(); }
    }

    public static final class ScanResult {
        public final List<Candidate> candidates = new ArrayList<>();
        public long visitedFiles;
        public long visitedBytes;
        public long inaccessibleDirectories;
        public boolean limited;
        public boolean cancelled;
        public long suggestedBytes;
        /** Aggregate bytes for the first 2 directory levels below each scan root. */
        public final Map<String, Long> folderBytes = new HashMap<>();
    }

    public interface Progress {
        boolean isCancelled();
        void onUpdate(long files, String currentPath);
        default boolean exclude(File file) { return false; }
    }

    private static final class Snapshot {
        final File file;
        final File root;
        final long size;
        final long modified;
        Snapshot(File file, File root, long size, long modified) {
            this.file = file; this.root = root; this.size = size; this.modified = modified;
        }
    }

    private static final class Directory {
        final File file;
        final File root;
        /** Canonical path. Children are never symlinks (lstat-checked), so parent + "/" + name stays canonical. */
        final String path;
        final int depth;
        Directory(File f, File r, String p, int d) { file=f; root=r; path=p; depth=d; }
    }

    /** One child as seen by a single lstat: replaces isSymbolicLink + isDirectory + isFile + length + lastModified. */
    private static final class Entry {
        final File file; final boolean directory; final long size, modified;
        Entry(File f, boolean d, long s, long m) { file=f; directory=d; size=s; modified=m; }
    }

    private static final class Listing {
        final List<Entry> entries; final boolean failed, empty; final long modified;
        Listing(List<Entry> e, boolean failed, boolean empty, long modified) {
            entries=e; this.failed=failed; this.empty=empty; this.modified=modified;
        }
    }

    /** I/O-bound over FUSE: more threads than cores still helps, but stay modest for battery/heat. */
    private static int ioThreads() { return Math.max(2, Math.min(6, Runtime.getRuntime().availableProcessors())); }

    /** Runs on a worker thread. Pure I/O: no shared state. */
    private static Listing list(Directory d, Progress progress) {
        File[] children;
        try { children = d.file.listFiles(); } catch (SecurityException e) { children = null; }
        if (children == null) return new Listing(Collections.emptyList(), true, false, 0L);
        if (children.length == 0) return new Listing(Collections.emptyList(), false, true, d.file.lastModified());
        List<Entry> out = new ArrayList<>(children.length);
        for (File child : children) {
            if (progress.isCancelled()) break;
            try {
                BasicFileAttributes a = Files.readAttributes(child.toPath(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (a.isSymbolicLink()) continue;
                if (a.isDirectory()) out.add(new Entry(child, true, 0L, 0L));
                else if (a.isRegularFile()) out.add(new Entry(child, false, a.size(), a.lastModifiedTime().toMillis()));
            } catch (IOException | SecurityException | UnsupportedOperationException ignored) { /* vanished or unreadable */ }
        }
        return new Listing(out, false, false, 0L);
    }

    public static ScanResult scan(List<File> roots, Progress progress) {
        ExecutorService pool = Executors.newFixedThreadPool(ioThreads());
        try { return scan(roots, progress, pool); }
        finally { pool.shutdownNow(); }
    }

    private static ScanResult scan(List<File> roots, Progress progress, ExecutorService pool) {
        ScanResult out = new ScanResult();
        Map<Long, List<Snapshot>> lengths = new HashMap<>();
        ArrayList<Snapshot> ordinary = new ArrayList<>();
        ArrayList<Candidate> empty = new ArrayList<>();
        Set<String> visitedDirs = new HashSet<>();
        ArrayDeque<Directory> pending = new ArrayDeque<>();
        long now = System.currentTimeMillis();

        for (File r : roots) {
            try {
                if (r != null && r.isDirectory() && !Files.isSymbolicLink(r.toPath()))
                    pending.add(new Directory(r, r, r.getCanonicalPath(), 0));
            } catch (IOException | SecurityException ignored) { out.inaccessibleDirectories++; }
        }
        // Directories are listed in parallel "waves"; results are merged on this thread only.
        // Visit order differs from a strict DFS, but every collection below is order-independent
        // or explicitly sorted, so the final result is the same.
        final int wave = ioThreads() * 3;
        List<Directory> batch = new ArrayList<>(wave);
        List<Future<Listing>> listings = new ArrayList<>(wave);
        walk:
        while (!pending.isEmpty()) {
            if (progress.isCancelled()) { out.cancelled=true; break; }
            batch.clear(); listings.clear();
            while (!pending.isEmpty() && batch.size() < wave) {
                Directory d = pending.removeLast();
                if (!visitedDirs.add(d.path)) continue;
                if (d.depth > MAX_DEPTH || forbidden(d.path) || progress.exclude(d.file)) continue;
                batch.add(d);
            }
            for (Directory d : batch) listings.add(pool.submit(() -> list(d, progress)));
            for (int i = 0; i < batch.size(); i++) {
                Directory directory = batch.get(i);
                Listing listing;
                try { listing = listings.get(i).get(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); out.cancelled=true; break walk; }
                catch (ExecutionException e) { out.inaccessibleDirectories++; continue; }
                if (progress.isCancelled()) { out.cancelled=true; break walk; }
                if (listing.failed) { out.inaccessibleDirectories++; continue; }
                if (listing.empty) {
                    if (directory.depth >= 1 && empty.size() < MAX_RESULTS)
                        empty.add(new Candidate(directory.file, directory.root, 0L, listing.modified, Type.EMPTY, false,
                                "Boş klasör; yalnızca seçersen silinir", null, null));
                    continue;
                }
                for (Entry child : listing.entries) {
                    if (out.visitedFiles >= MAX_VISITS) { out.limited=true; break walk; }
                    if (child.directory) {
                        if (!forbidden(child.file.getAbsolutePath()))
                            pending.add(new Directory(child.file, directory.root,
                                    directory.path + File.separator + child.file.getName(), directory.depth+1));
                    } else {
                        if (forbidden(child.file.getAbsolutePath()) || progress.exclude(child.file)) continue;
                        Snapshot s = new Snapshot(child.file, directory.root, child.size, child.modified);
                        ordinary.add(s);
                        out.visitedFiles++;
                        out.visitedBytes += s.size;
                        String folder = folderGroup(directory.root, child.file);
                        if (folder != null) out.folderBytes.merge(folder, s.size, Long::sum);
                        if (s.size >= 1024L) lengths.computeIfAbsent(s.size, k -> new ArrayList<>()).add(s);
                        if (out.visitedFiles % 300 == 0) progress.onUpdate(out.visitedFiles, child.file.getParent());
                    }
                }
            }
        }
        if (out.cancelled) return out;
        progress.onUpdate(out.visitedFiles, "Birebir aynı dosyalar kontrol ediliyor");

        // Fast sample first, full SHA-256 only for files with matching sizes and samples.
        // Both hashing passes run in parallel; grouping is done afterwards in the same order as before.
        Map<Snapshot, Future<String>> sampleJobs = new HashMap<>();
        for (List<Snapshot> sizeGroup : lengths.values()) {
            if (sizeGroup.size() < 2) continue;
            for (Snapshot s : sizeGroup) sampleJobs.put(s, pool.submit(() -> sampleHash(s.file, progress)));
        }
        Map<Snapshot, Future<String>> fullJobs = new HashMap<>();
        List<Map<String, List<Snapshot>>> sampleGroupsBySize = new ArrayList<>();
        for (List<Snapshot> sizeGroup : lengths.values()) {
            if (progress.isCancelled()) { out.cancelled = true; return out; }
            if (sizeGroup.size() < 2) continue;
            Map<String, List<Snapshot>> samples = new HashMap<>();
            for (Snapshot s : sizeGroup) {
                String sample = result(sampleJobs.get(s));
                if (progress.isCancelled()) { out.cancelled=true; return out; }
                if (sample != null) samples.computeIfAbsent(sample, k -> new ArrayList<>()).add(s);
            }
            for (List<Snapshot> sampleGroup : samples.values())
                if (sampleGroup.size() >= 2)
                    for (Snapshot s : sampleGroup) fullJobs.put(s, pool.submit(() -> sha256(s.file, progress)));
            sampleGroupsBySize.add(samples);
        }
        Set<String> duplicatePaths = new HashSet<>();
        Set<String> preservedOriginals = new HashSet<>();
        for (Map<String, List<Snapshot>> samples : sampleGroupsBySize) {
            for (List<Snapshot> sampleGroup : samples.values()) {
                if (sampleGroup.size() < 2) continue;
                Map<String, List<Snapshot>> hashes = new HashMap<>();
                for (Snapshot s : sampleGroup) {
                    String hash = result(fullJobs.get(s));
                    if (progress.isCancelled()) { out.cancelled=true; return out; }
                    if (hash != null && s.file.length() == s.size && s.file.lastModified() == s.modified)
                        hashes.computeIfAbsent(hash, k -> new ArrayList<>()).add(s);
                }
                for (Map.Entry<String,List<Snapshot>> group : hashes.entrySet()) {
                    List<Snapshot> same = group.getValue();
                    if (same.size() < 2) continue;
                    same.sort(Comparator.comparingInt((Snapshot s) -> preserveScore(s.file))
                            .thenComparingLong(s -> s.modified)
                            .thenComparing(s -> s.file.getAbsolutePath()));
                    Snapshot keep = same.get(0);
                    preservedOriginals.add(keep.file.getAbsolutePath());
                    for (int i=1; i<same.size() && out.candidates.size()<MAX_RESULTS; i++) {
                        Snapshot s = same.get(i);
                        duplicatePaths.add(s.file.getAbsolutePath());
                        out.candidates.add(new Candidate(s.file,s.root,s.size,s.modified,
                                Type.DUPLICATE,true,"Aynı içerik; korunacak dosya: " + keep.file.getName(),
                                keep.file,group.getKey()));
                    }
                }
            }
        }
        for (Snapshot s : ordinary) {
            if (progress.isCancelled()) { out.cancelled=true; return out; }
            if (out.candidates.size() >= MAX_RESULTS) { out.limited=true; break; }
            if (duplicatePaths.contains(s.file.getAbsolutePath())) continue;
            Candidate c = classify(s,now);
            if (c != null) {
                if (c.suggested && preservedOriginals.contains(c.key()))
                    c = new Candidate(c.file,c.root,c.size,c.modified,c.type,false,
                            "Aynı dosyaların korunan kopyası; otomatik öneriye dahil değil",null,null);
                out.candidates.add(c);
            }
        }
        for (Candidate c : empty) {
            if (out.candidates.size() >= MAX_RESULTS) { out.limited=true; break; }
            out.candidates.add(c);
        }
        out.candidates.sort(Comparator.comparing((Candidate c) -> !c.suggested)
                .thenComparing((Candidate c) -> -c.size).thenComparing(c -> c.file.getAbsolutePath()));
        for (Candidate c : out.candidates) if (c.suggested) out.suggestedBytes += c.size;
        progress.onUpdate(out.visitedFiles,"Tarama tamamlandı");
        return out;
    }

    /** Future result, or null when the file changed, was unreadable or the scan was cancelled. */
    private static String result(Future<String> f) {
        if (f == null) return null;
        try { return f.get(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return null; }
        catch (ExecutionException | java.util.concurrent.CancellationException e) { return null; }
    }

    private static Candidate classify(Snapshot s, long now) {
        String name = s.file.getName().toLowerCase(Locale.ROOT);
        String path = s.file.getAbsolutePath().replace('\\','/').toLowerCase(Locale.ROOT);
        long age = s.modified > 0 ? Math.max(0,now-s.modified) : 0;
        boolean downloads = path.contains("/download/") || path.contains("/downloads/");
        boolean screenshots = path.contains("/screenshots/") || name.startsWith("screenshot") || name.startsWith("screen_shot");
        boolean thumb = path.contains("/.thumbnails/") || path.contains("/thumbnails/");
        boolean messenger = path.contains("/android/media/com.whatsapp/") || path.contains("/android/media/org.telegram.messenger/")
                || path.contains("/whatsapp/media/") || path.contains("/telegram/");
        Type kind = null;
        boolean recommended = false;
        String explanation = "Gözden geçirerek temizleyebilirsin";
        boolean orphan = isPossibleLeftover(path, age);
        if (orphan) {
            kind=Type.LEFTOVER; recommended=false;
            explanation="Olası uygulama kalıntısı: uygulama yüklü mü kontrol et; otomatik silinmez";
        } else if (thumb && age > 14*DAY_MS) {
            kind=Type.THUMBNAIL; recommended=false;
            explanation="Yeniden oluşturulabilen küçük resim olabilir; kontrol et";
        } else if (downloads && age > 7*DAY_MS && ends(name,".tmp",".temp",".log",".bak",".old",".crdownload",".partial",".download")) {
            kind=Type.TEMP; recommended=true; explanation="İndirilenler içinde 7 günden eski geçici dosya";
        } else if (ends(name,".apk",".xapk",".apks",".apkm") && age>7*DAY_MS) {
            kind=Type.INSTALLER; recommended=downloads;
            explanation="7 günden eski kurulum paketi";
        } else if (ends(name,".zip",".rar",".7z",".tar",".gz",".bz2") && age>30*DAY_MS) {
            kind=Type.ARCHIVE; explanation="30 günden eski arşiv; içeriğine bak";
        } else if (messenger && s.size>=10*MIB && age>30*DAY_MS) {
            kind=Type.MESSENGER; explanation="30 günden eski büyük mesajlaşma dosyası";
        } else if (screenshots) {
            kind=Type.SCREENSHOT; explanation="Ekran görüntüsü; silmeden önce kontrol et";
        } else if (s.size>=100*MIB) {
            kind=Type.LARGE; explanation="100 MB üzeri dosya";
        }
        if (kind==null) return null;
        return new Candidate(s.file,s.root,s.size,s.modified,kind,recommended,explanation,null,null);
    }

    private static boolean isPossibleLeftover(String path, long age) {
        if (age < 60*DAY_MS) return false;
        return path.contains("/download/.cache/") || path.contains("/downloads/.cache/")
                || path.contains("/backup/oldapps/") || path.contains("/backups/oldapps/");
    }

    private static String folderGroup(File root, File child) {
        try {
            String relative = root.toPath().relativize(child.toPath()).toString().replace('\\','/');
            String[] parts = relative.split("/");
            if (parts.length <= 1) return root.getName() + "/(kök)";
            StringBuilder b = new StringBuilder(root.getName());
            for (int i=0;i<Math.min(2,parts.length-1);i++) b.append("/").append(parts[i]);
            return b.toString();
        } catch (IllegalArgumentException ex) { return null; }
    }

    private static boolean ends(String s, String... suffixes) {
        for (String ext : suffixes) if (s.endsWith(ext)) return true;
        return false;
    }

    private static int preserveScore(File file) {
        String p = file.getAbsolutePath().toLowerCase(Locale.ROOT).replace('\\','/');
        if (p.contains("/dcim/camera/")) return 0;
        if (p.contains("/dcim/")) return 1;
        if (p.contains("/pictures/")) return 2;
        if (p.contains("/movies/")) return 3;
        return 4;
    }

    private static boolean forbidden(String path) {
        String p = path.replace('\\','/').toLowerCase(Locale.ROOT);
        return p.contains("/android/data/") || p.endsWith("/android/data")
                || p.contains("/android/obb/") || p.endsWith("/android/obb")
                || p.contains("/android/.trash/") || p.contains("/.trash/")
                || p.contains("/clearos_ultra_private/")
                || p.contains("/.clearos_quarantine/") || p.endsWith("/.clearos_quarantine");
    }

    private static boolean underRoot(File root, File candidate) throws IOException {
        String r = root.getCanonicalPath();
        String c = candidate.getCanonicalPath();
        return c.equals(r) || c.startsWith(r + File.separator);
    }

    private static String sampleHash(File f, Progress progress) throws IOException {
        long len = f.length();
        MessageDigest digest = digest();
        byte[] buf = new byte[64*1024];
        try (FileInputStream stream = new FileInputStream(f)) {
            int n = stream.read(buf);
            if (n > 0) digest.update(buf,0,n);
            if (len > 64*1024) {
                long endStart = Math.max(64*1024,len - 64*1024);
                stream.getChannel().position(endStart);
                n = stream.read(buf);
                if (n > 0) digest.update(buf,0,n);
            }
        }
        if (progress.isCancelled()) throw new IOException("Cancelled");
        return hex(digest.digest());
    }

    private static String sha256(File f, Progress progress) throws IOException {
        MessageDigest digest = digest();
        byte[] buf = new byte[128*1024];
        try (FileInputStream in = new FileInputStream(f)) {
            int n;
            while ((n=in.read(buf))!=-1) {
                if (progress.isCancelled()) throw new IOException("Cancelled");
                digest.update(buf,0,n);
            }
        }
        return hex(digest.digest());
    }

    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static String hex(byte[] bytes) {
        char[] digits="0123456789abcdef".toCharArray();
        char[] result = new char[bytes.length*2];
        for (int i=0;i<bytes.length;i++) {
            result[i*2]=digits[(bytes[i]>>4)&15];
            result[i*2+1]=digits[bytes[i]&15];
        }
        return new String(result);
    }

    /** Revalidates file identity, path and duplicate originals before any mutation. */
    public static boolean verifyCandidate(Candidate c) {
        try {
            if (Files.isSymbolicLink(c.file.toPath()) || !underRoot(c.root,c.file)
                    || c.file.equals(c.root) || forbidden(c.file.getCanonicalPath())) return false;
            if (c.type==Type.EMPTY) {
                if (c.file.lastModified()!=c.modified) return false;
                File[] children=c.file.listFiles();
                return c.file.isDirectory() && children!=null && children.length==0;
            }
            if (c.file.length()!=c.size || c.file.lastModified()!=c.modified) return false;
            if (!c.file.isFile()) return false;
            if (c.type==Type.DUPLICATE) {
                if (c.duplicateKeep == null || c.duplicateHash == null || !c.duplicateKeep.isFile()
                        || !underRoot(c.root,c.file)) return false;
                Progress noCancel = new Progress() {
                    @Override public boolean isCancelled() { return false; }
                    @Override public void onUpdate(long files,String path) { }
                };
                if (!sha256(c.file,noCancel).equals(c.duplicateHash)
                        || !sha256(c.duplicateKeep,noCancel).equals(c.duplicateHash)) return false;
            }
            return true;
        } catch (SecurityException | IOException ignored) { return false; }
    }

    /** Permanent delete is available only after a separate explicit confirmation. */
    public static boolean deleteVerified(Candidate c) {
        return verifyCandidate(c) && c.file.delete();
    }
}
