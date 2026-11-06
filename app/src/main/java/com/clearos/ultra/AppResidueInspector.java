package com.clearos.ultra;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Environment;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Only Android/media package folder candidates, never automatically removed. */
public final class AppResidueInspector {
    private AppResidueInspector() {}
    public static final class Residue {
        public final String packageId;
        public final File folder;
        public final long bytes;
        public final int files;
        Residue(String id,File f,long b,int n){packageId=id;folder=f;bytes=b;files=n;}
    }
    public static List<Residue> inspect(Context context) {
        Set<String> installed=new HashSet<>();
        for(ApplicationInfo a:context.getPackageManager().getInstalledApplications(0))installed.add(a.packageName);
        File media=new File(Environment.getExternalStorageDirectory(),"Android/media");
        ArrayList<Residue> results=new ArrayList<>();
        File[] dirs=media.listFiles();if(dirs==null)return results;
        for(File dir:dirs) {
            if(!dir.isDirectory() || installed.contains(dir.getName()))continue;
            // Package folder name must look like a full reverse-domain name; unknown is only a hint.
            if(!dir.getName().matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*){1,8}"))continue;
            long bytes=0;int files=0;int visits=0;
            ArrayDeque<File> queue=new ArrayDeque<>();queue.add(dir);
            while(!queue.isEmpty() && visits++<10000) {
                File f=queue.removeLast();
                if(java.nio.file.Files.isSymbolicLink(f.toPath()))continue;
                if(f.isFile()){bytes+=f.length();files++;}
                else if(f.isDirectory()){
                    File[] children=f.listFiles();if(children!=null)for(File child:children)queue.add(child);
                }
            }
            if(files>0) results.add(new Residue(dir.getName(),dir,bytes,files));
        }
        results.sort((a,b)->Long.compare(b.bytes,a.bytes));
        return results;
    }
}
