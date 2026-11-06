package com.clearos.ultra;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.nio.file.FileSystemException;
import java.util.Properties;
import java.util.UUID;

/**
 * Per-volume reversible quarantine on the shared storage root (outside Android/data).
 * Never copies across mount points; an unsuccessful rename leaves the original untouched.
 * Uninstalling the app does not automatically remove this directory.
 */
public final class TrashStore {
    private final File base;
    private final File items;
    private final File index;

    public TrashStore(File base) {
        this.base=base;
        items=new File(base,"items");
        index=new File(base,"index");
    }
    public static final class Entry {
        public final String id;
        public final File original;
        public final File payload;
        public final long size;
        public final long timestamp;
        public Entry(String id,File original,File payload,long size,long timestamp) {
            this.id=id;this.original=original;this.payload=payload;this.size=size;this.timestamp=timestamp;
        }
    }
    private boolean setup() {
        try {
            // Another app with broad storage access could create symlink traps.
            if (Files.isSymbolicLink(base.toPath()) || Files.isSymbolicLink(items.toPath())
                    || Files.isSymbolicLink(index.toPath())) return false;
            if (!(items.isDirectory() || items.mkdirs()) || !(index.isDirectory() || index.mkdirs())) return false;
            File nomedia=new File(base,".nomedia");
            if (!nomedia.exists()) nomedia.createNewFile();
            return nomedia.isFile();
        } catch (IOException|SecurityException e) { return false; }
    }

    public synchronized Entry move(SafeScanner.Candidate c) throws IOException {
        if (!SafeScanner.verifyCandidate(c)) throw new IOException("Dosya değişmiş veya korunuyor");
        if (c.type==SafeScanner.Type.EMPTY) throw new IOException("Boş klasörü karantinaya taşımak desteklenmiyor");
        if (!setup()) throw new IOException("Karantina oluşturulamadı");
        if (c.file.getCanonicalPath().startsWith(base.getCanonicalPath()+File.separator))
            throw new IOException("Karantina içinden tekrar taşınamaz");
        String id=UUID.randomUUID().toString();
        File payload=new File(items,id+".data");
        long now=System.currentTimeMillis();
        Entry entry=new Entry(id,c.file,payload,c.size,now);
        // Write recovery metadata BEFORE the move so a process crash doesn't lose the source path.
        persist(entry);
        if (!c.file.renameTo(payload)) {
            new File(index,id+".properties").delete();
            throw new IOException("Taşıma başarısız: izinleri veya birim bağlamasını kontrol et; orijinal korunuyor");
        }
        return entry;
    }
    private void persist(Entry e) throws IOException {
        Properties props=new Properties();
        props.setProperty("original",e.original.getCanonicalPath());
        props.setProperty("size",Long.toString(e.size));
        props.setProperty("timestamp",Long.toString(e.timestamp));
        File temp=new File(index,e.id+".pending");
        File dest=new File(index,e.id+".properties");
        try (FileOutputStream f=new FileOutputStream(temp)) {
            props.store(f,"ClearOS Ultra safe quarantine; do not edit"); f.getFD().sync();
        }
        if (!temp.renameTo(dest)) { temp.delete();throw new IOException("Kayıt yazılamadı"); }
    }
    public synchronized List<Entry> list() {
        if (Files.isSymbolicLink(base.toPath()) || Files.isSymbolicLink(items.toPath())
                || Files.isSymbolicLink(index.toPath()) || !index.isDirectory()) return Collections.emptyList();
        File[] files=index.listFiles((dir,name)->name.matches("[a-f0-9-]{36}\\.properties"));
        if (files==null) return Collections.emptyList();
        List<Entry> result=new ArrayList<>();
        for (File f:files) {
            String id=f.getName().replace(".properties","");
            try (FileInputStream in=new FileInputStream(f)) {
                Properties p=new Properties();p.load(in);
                File original=new File(p.getProperty("original",""));
                File payload=new File(items,id+".data");
                long size=Long.parseLong(p.getProperty("size","0"));
                long timestamp=Long.parseLong(p.getProperty("timestamp","0"));
                // Never trust an edited shared-storage metadata file to escape its storage root.
                File volume=base.getParentFile().getCanonicalFile();
                String orig=original.getCanonicalPath();
                if (original.isAbsolute() && orig.startsWith(volume.getPath()+File.separator)
                        && !orig.startsWith(base.getCanonicalPath()+File.separator)
                        && !Files.isSymbolicLink(payload.toPath())
                        && payload.isFile() && payload.length()==size && size>=0)
                    result.add(new Entry(id,original,payload,size,timestamp));
            } catch (Exception ignored) { /* don't touch unknown data */ }
        }
        result.sort((a,b)->Long.compare(b.timestamp,a.timestamp));
        return result;
    }
    private boolean known(Entry entry) {
        if (entry==null) return false;
        for (Entry e:list()) if (e.id.equals(entry.id) && e.original.equals(entry.original)
                && e.payload.equals(entry.payload)) return true;
        return false;
    }
    public synchronized boolean restore(Entry e) {
        if (!known(e)) return false;
        return restoreVerified(e);
    }
    /**
     * Batch restore from ONE verified index snapshot. restore() re-reads every metadata file per
     * call, which is O(N²) over FUSE for a large quarantine (456 entries ≈ 200k reads).
     * Returns the ids that were restored.
     */
    public synchronized java.util.Set<String> restoreBatch(List<Entry> requested) {
        Map<String,Entry> current=new HashMap<>();
        for (Entry item:list()) current.put(item.id,item);
        java.util.Set<String> restored=new java.util.HashSet<>();
        for (Entry supplied:requested) {
            if (supplied==null) continue;
            Entry e=current.get(supplied.id);
            if (e==null || !e.original.equals(supplied.original) || !e.payload.equals(supplied.payload)) continue;
            current.remove(e.id); // consume only on a verified match; duplicates then no-op
            if (restoreVerified(e)) restored.add(e.id);
        }
        return restored;
    }
    private boolean restoreVerified(Entry e) {
        if (e.original.exists() || !e.payload.isFile()) return false;
        File folder=e.original.getParentFile();
        // Don't recreate unknown directories; only restore to the original existing parent.
        if (folder==null || !folder.isDirectory()) return false;
        if (!e.payload.renameTo(e.original)) return false;
        new File(index,e.id+".properties").delete();
        return true;
    }
    /** Detailed result so a failed disk deletion is never reported as a success. */
    public static final class PurgeResult {
        public final Entry entry;
        public final boolean deleted;
        public final String reason;
        public PurgeResult(Entry e,boolean ok,String reason) {
            entry=e;deleted=ok;this.reason=reason;
        }
    }

    /** One verified snapshot, avoiding hundreds of repeated O(N) metadata reads. */
    public synchronized List<PurgeResult> purgeBatch(List<Entry> requested) {
        Map<String,Entry> current=new HashMap<>();
        for (Entry item:list()) current.put(item.id,item);
        List<PurgeResult> outcomes=new ArrayList<>();
        for (Entry supplied:requested) {
            Entry e=supplied==null?null:current.remove(supplied.id);
            if (e==null || supplied==null || !e.original.equals(supplied.original)
                    || !e.payload.equals(supplied.payload) || e.size!=supplied.size) {
                outcomes.add(new PurgeResult(supplied,false,"Karantina kaydı bulunamadı veya değişti"));
                continue;
            }
            try {
                if (!e.payload.isFile() || Files.isSymbolicLink(e.payload.toPath())
                        || e.payload.length()!=e.size) {
                    outcomes.add(new PurgeResult(e,false,"Dosya yok, değişmiş veya boyutu uyuşmuyor"));
                    continue;
                }
                // A regular file is all we will delete. Never follow an external symlink.
                Files.delete(e.payload.toPath());
                if (Files.exists(e.payload.toPath())) {
                    outcomes.add(new PurgeResult(e,false,"Dosya silme sonrası hâlâ mevcut"));
                    continue;
                }
                // Metadata deletion is best effort: it is filtered from list() if payload is gone.
                try {Files.deleteIfExists(new File(index,e.id+".properties").toPath());}
                catch (IOException | SecurityException ignored) { }
                outcomes.add(new PurgeResult(e,true,"Dosya diskten silindi"));
            } catch (FileSystemException ex) {
                String detail=ex.getReason()==null?"Depolama erişimi reddedildi veya dosya meşgul":ex.getReason();
                outcomes.add(new PurgeResult(e,false,detail));
            } catch (IOException | SecurityException ex) {
                outcomes.add(new PurgeResult(e,false,ex.getClass().getSimpleName()+": "+
                        (ex.getMessage()==null?"Erişim hatası":ex.getMessage())));
            }
        }
        return outcomes;
    }

    public synchronized boolean purge(Entry e) {
        List<PurgeResult> result=purgeBatch(Collections.singletonList(e));
        return !result.isEmpty() && result.get(0).deleted;
    }
    public long containedBytes() {
        long size=0; for (Entry e:list()) size+=e.size; return size;
    }
}
