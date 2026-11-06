package com.clearos.ultra;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/** A reversible vault on EACH source storage volume, rather than Android/data. */
public final class QuarantineManager {
    public static final String FOLDER = ".ClearOS_Quarantine";
    private final List<File> roots;

    public QuarantineManager(List<File> storageRoots) {
        roots = new ArrayList<>();
        for (File r:storageRoots) {
            if (r==null || !r.isDirectory()) continue;
            try {
                File canonical=r.getCanonicalFile();
                if (!roots.contains(canonical)) roots.add(canonical);
            } catch (IOException ignored) { }
        }
    }

    private TrashStore store(File root) { return new TrashStore(new File(root,FOLDER)); }

    /** The scanner-provided root MUST be a mounted root configured for this manager. */
    public TrashStore.Entry move(SafeScanner.Candidate candidate) throws IOException {
        File root=candidate.root.getCanonicalFile();
        if (!roots.contains(root)) throw new IOException("Kaynak birimi artık bağlı değil");
        File file=candidate.file.getCanonicalFile();
        if (!file.toPath().startsWith(root.toPath()) || file.equals(root))
            throw new IOException("Dosya izinli depolama kökünün dışında");
        return store(root).move(candidate);
    }

    public List<TrashStore.Entry> list() {
        List<TrashStore.Entry> result=new ArrayList<>();
        for (File root:roots) result.addAll(store(root).list());
        result.sort((a,b)->Long.compare(b.timestamp,a.timestamp));
        return Collections.unmodifiableList(result);
    }

    public boolean restore(TrashStore.Entry entry) {
        for (File root:roots) if (store(root).restore(entry)) return true;
        return false;
    }

    /** Restore many entries reading each volume's index once. Returns restored entry ids. */
    public java.util.Set<String> restoreBatch(List<TrashStore.Entry> entries) {
        java.util.Set<String> restored=new java.util.HashSet<>();
        List<TrashStore.Entry> pending=new ArrayList<>(entries);
        for (File root:roots) {
            if (pending.isEmpty()) break;
            java.util.Set<String> done=store(root).restoreBatch(pending);
            restored.addAll(done);
            pending.removeIf(e->e==null||done.contains(e.id));
        }
        return restored;
    }

    public boolean purge(TrashStore.Entry entry) {
        for (File root:roots) if (store(root).purge(entry)) return true;
        return false;
    }

    /** Group by volume; each volume is indexed only once for a batch. */
    public List<TrashStore.PurgeResult> purgeBatch(List<TrashStore.Entry> entries) {
        Map<File,List<TrashStore.Entry>> perVolume=new HashMap<>();
        for (TrashStore.Entry entry:entries) {
            boolean accepted=false;
            for(File root:roots) {
                try {
                    File folder=new File(root,FOLDER).getCanonicalFile();
                    if(entry!=null && entry.payload.getCanonicalPath().startsWith(
                            new File(folder,"items").getCanonicalPath()+File.separator)) {
                        perVolume.computeIfAbsent(root,k->new ArrayList<>()).add(entry);
                        accepted=true;break;
                    }
                } catch(IOException ignored) { }
            }
            if(!accepted)perVolume.computeIfAbsent(null,k->new ArrayList<>()).add(entry);
        }
        ArrayList<TrashStore.PurgeResult> results=new ArrayList<>();
        for(Map.Entry<File,List<TrashStore.Entry>> volume:perVolume.entrySet()) {
            if(volume.getKey()==null) {
                for(TrashStore.Entry e:volume.getValue())
                    results.add(new TrashStore.PurgeResult(e,false,"Karantina birimi bağlı değil"));
            } else results.addAll(store(volume.getKey()).purgeBatch(volume.getValue()));
        }
        return results;
    }

    public long containedBytes() { long sum=0;for(TrashStore.Entry e:list())sum+=e.size;return sum; }
}
