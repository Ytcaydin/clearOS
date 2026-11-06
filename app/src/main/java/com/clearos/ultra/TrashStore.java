package com.clearos.ultra;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
        if (!known(e) || e.original.exists() || !e.payload.isFile()) return false;
        File folder=e.original.getParentFile();
        // Don't recreate unknown directories; only restore to the original existing parent.
        if (folder==null || !folder.isDirectory()) return false;
        if (!e.payload.renameTo(e.original)) return false;
        new File(index,e.id+".properties").delete();
        return true;
    }
    public synchronized boolean purge(Entry e) {
        if (!known(e) || !e.payload.isFile() || e.payload.length()!=e.size) return false;
        if (!e.payload.delete()) return false;
        new File(index,e.id+".properties").delete();
        return true;
    }
    public long containedBytes() {
        long size=0; for (Entry e:list()) size+=e.size; return size;
    }
}
