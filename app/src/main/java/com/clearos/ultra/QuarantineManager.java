package com.clearos.ultra;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    public boolean purge(TrashStore.Entry entry) {
        for (File root:roots) if (store(root).purge(entry)) return true;
        return false;
    }

    public long containedBytes() { long sum=0;for(TrashStore.Entry e:list())sum+=e.size;return sum; }
}
