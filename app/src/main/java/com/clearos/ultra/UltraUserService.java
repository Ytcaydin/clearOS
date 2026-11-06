package com.clearos.ultra;

import android.os.Process;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/** Bound by Shizuku/Porter, in a separate ADB-shell identity process.
 * NO arbitrary shell input, root operations or unattended deletions. */
public class UltraUserService extends IUltraService.Stub {
    public UltraUserService() { }
    @Override public String trimCaches() {
        if (Process.myUid()!=2000 && Process.myUid()!=0)
            return "Yetki yok: ADB shell veya root UID bulunamadı";
        // pm trim-caches expects a TARGET FREE SPACE, not bytes to delete.
        // A high target asks Android to trim as much eligible cache as it permits.
        ProcessBuilder shell=new ProcessBuilder("/system/bin/pm","trim-caches","999G","internal");
        shell.redirectErrorStream(true);
        try {
            java.lang.Process command=shell.start();
            if(!command.waitFor(90,TimeUnit.SECONDS)) {
                command.destroyForcibly();return "İşlem zaman aşımına uğradı";
            }
            try (InputStream in=command.getInputStream()) {
                ByteArrayOutputStream buf=new ByteArrayOutputStream();
                byte[] block=new byte[2048];int n;
                while((n=in.read(block))!=-1 && buf.size()<8192)buf.write(block,0,n);
                return "pm trim-caches çıkış kodu: "+command.exitValue()+"\n"+buf.toString("UTF-8");
            }
        } catch(Exception e) {return "İşlem desteklenmedi: "+e.getClass().getSimpleName();}
    }
    @Override public void destroy() {System.exit(0);}
}
