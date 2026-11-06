package com.clearos.ultra;

import android.content.Context;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Mounted shared-storage roots. Never points to app-private Android/data. */
public final class StorageRoots {
    private StorageRoots() { }
    public static List<File> available(Context context) {
        ArrayList<File> roots=new ArrayList<>();
        add(roots,Environment.getExternalStorageDirectory());
        try {
            StorageManager mgr=(StorageManager)context.getSystemService(Context.STORAGE_SERVICE);
            if (mgr!=null) for (StorageVolume vol:mgr.getStorageVolumes()) {
                if (Environment.MEDIA_MOUNTED.equals(vol.getState())) add(roots,vol.getDirectory());
            }
        } catch (RuntimeException ignored) { }
        return roots;
    }
    private static void add(List<File> roots,File dir) {
        try {
            if(dir!=null && dir.isDirectory()) {
                File f=dir.getCanonicalFile();
                if(!roots.contains(f)) roots.add(f);
            }
        } catch (IOException ignored) { }
    }
}
