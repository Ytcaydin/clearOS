package com.clearos.ultra;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
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
        Snapshot(File file, File root) {
            this.file = file; this.root = root;
            this.size = file.length(); this.modified = file.lastModified();
        }
    }

    private static final class Directory {
        final File file;
        final File root;
        final int depth;
        Directory(File f, File r, int d) { file=f; root=r; depth=d; }
    }

    public static ScanResult scan(List<File> roots, Progress progress) {
        ScanResult out = new ScanResult();
        Map<Long, List<Snapshot>> lengths = new HashMap<>();
        ArrayList<Snapshot> ordinary = new ArrayList<>();
        ArrayList<Candidate> empty = new ArrayList<>();
        Set<String> visitedDirs = new HashSet<>();
        ArrayDeque<Directory> pending = new ArrayDeque<>();
        long now = System.currentTimeMillis();

        for (File r : roots) {
            if (r != null && r.isDirectory()) pending.add(new Directory(r, r, 0));
        }
        while (!pending.isEmpty()) {
            if (progress.isCancelled()) { out.cancelled=true; break; }
            Directory directory = pending.removeLast();
            try {
                if (Files.isSymbolicLink(directory.file.toPath())) continue;
                String canonical = directory.file.getCanonicalPath();
                if (!underRoot(directory.root, directory.file)) continue;
                if (!visitedDirs.add(canonical)) continue;
                if (directory.depth > MAX_DEPTH || forbidden(canonical) || progress.exclude(directory.file)) continue;
                File[] children = directory.file.listFiles();
                if (children == null) { out.inaccessibleDirectories++; continue; }
                if (children.length == 0 && directory.depth >= 1 && empty.size() < MAX_RESULTS) {
                    empty.add(new Candidate(directory.file, directory.root, 0L,
                            directory.file.lastModified(), Type.EMPTY, false,
                            "Boş klasör; yalnızca seçersen silinir", null, null));
                }
                for (File child : children) {
                    if (progress.isCancelled()) { out.cancelled = true; break; }
                    if (out.visitedFiles >= MAX_VISITS) { out.limited=true; break; }
                    if (Files.isSymbolicLink(child.toPath())) continue;
                    if (child.isDirectory()) {
                        if (!forbidden(child.getAbsolutePath())) pending.add(new Directory(child, directory.root, directory.depth+1));
                    } else if (child.isFile()) {
                        if (forbidden(child.getAbsolutePath()) || progress.exclude(child)) continue;
                        Snapshot s = new Snapshot(child, directory.root);
                        ordinary.add(s);
                        out.visitedFiles++;
                        out.visitedBytes += s.size;
                        String folder = folderGroup(directory.root, child);
                        if (folder != null) out.folderBytes.merge(folder, s.size, Long::sum);
                        if (s.size >= 1024L) lengths.computeIfAbsent(s.size, k -> new ArrayList<>()).add(s);
                        if (out.visitedFiles % 300 == 0) progress.onUpdate(out.visitedFiles, child.getParent());
                    }
                }
                if (out.cancelled || out.limited) break;
            } catch (SecurityException | IOException ignored) {
                out.inaccessibleDirectories++;
            }
        }
        if (out.cancelled) return out;
        progress.onUpdate(out.visitedFiles, "Birebir aynı dosyalar kontrol ediliyor");

        // Fast sample first, full SHA-256 only for files with matching sizes and samples.
        Set<String> duplicatePaths = new HashSet<>();
        Set<String> preservedOriginals = new HashSet<>();
        for (List<Snapshot> sizeGroup : lengths.values()) {
            if (progress.isCancelled()) { out.cancelled = true; return out; }
            if (sizeGroup.size() < 2) continue;
            Map<String, List<Snapshot>> samples = new HashMap<>();
            for (Snapshot s : sizeGroup) {
                if (progress.isCancelled()) { out.cancelled=true; return out; }
                try {
                    String sample = sampleHash(s.file, progress);
                    samples.computeIfAbsent(sample, k -> new ArrayList<>()).add(s);
                } catch (IOException | SecurityException ignored) { /* changed or unreadable */ }
            }
            for (List<Snapshot> sampleGroup : samples.values()) {
                if (sampleGroup.size() < 2) continue;
                Map<String, List<Snapshot>> hashes = new HashMap<>();
                for (Snapshot s : sampleGroup) {
                    if (progress.isCancelled()) { out.cancelled=true; return out; }
                    try {
                        String hash = sha256(s.file, progress);
                        if (s.file.length() == s.size && s.file.lastModified() == s.modified)
                            hashes.computeIfAbsent(hash, k -> new ArrayList<>()).add(s);
                    } catch (IOException | SecurityException ignored) { }
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
