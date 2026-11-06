import com.clearos.ultra.*;
import java.io.File;
import java.nio.file.*;
import java.util.*;

/** Standalone JVM regression tests. Cross-mount behavior still requires Android device QA. */
public final class QuarantineRegressionTest {
    static void expect(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
    static SafeScanner.Candidate find(File root,File target) {
        SafeScanner.ScanResult result=SafeScanner.scan(Arrays.asList(root),new SafeScanner.Progress() {
            public boolean isCancelled(){return false;}
            public void onUpdate(long n,String p){}
        });
        for(SafeScanner.Candidate c:result.candidates)if(c.file.equals(target))return c;
        throw new AssertionError("test candidate not found: "+target);
    }
    public static void main(String[] ignored) throws Exception {
        Path temp=Files.createTempDirectory("clearos-regression-");
        File internal=temp.resolve("emulated-0").toFile();
        File sd=temp.resolve("sdcard-X").toFile();
        expect(internal.mkdirs() && sd.mkdirs(),"storage directories");
        File source=new File(internal,"Download/old.apk");
        expect(source.getParentFile().mkdirs(),"download dir");
        Files.write(source.toPath(),new byte[]{1,2,3});
        source.setLastModified(System.currentTimeMillis()-11L*86400000);
        File sourceSd=new File(sd,"Download/old2.apk");
        expect(sourceSd.getParentFile().mkdirs(),"download dir sd");
        Files.write(sourceSd.toPath(),new byte[]{4,5,6});
        sourceSd.setLastModified(System.currentTimeMillis()-11L*86400000);
        QuarantineManager vault=new QuarantineManager(Arrays.asList(internal,sd));
        TrashStore.Entry a=vault.move(find(internal,source));
        TrashStore.Entry b=vault.move(find(sd,sourceSd));
        expect(a.payload.getCanonicalPath().startsWith(internal.getCanonicalPath()+File.separator+QuarantineManager.FOLDER+File.separator),"internal vault on same root");
        expect(b.payload.getCanonicalPath().startsWith(sd.getCanonicalPath()+File.separator+QuarantineManager.FOLDER+File.separator),"sd vault on same root");
        expect(new File(internal,QuarantineManager.FOLDER+"/.nomedia").isFile(),"nomedia exists");
        expect(vault.list().size()==2 && vault.containedBytes()==6,"both volumes listed");
        SafeScanner.ScanResult scanned=SafeScanner.scan(Arrays.asList(internal,sd),new SafeScanner.Progress(){
            public boolean isCancelled(){return false;}
            public void onUpdate(long n,String p){}
        });
        expect(scanned.visitedFiles==0,"scanner does not descend into quarantine");
        expect(vault.restore(a) && source.isFile(),"restore across selected vault");
        expect(vault.purge(b) && !sourceSd.exists(),"purge on selected vault");
        expect(vault.list().isEmpty(),"no active entries remain");
        ExcludedPaths onlyDCIM=new ExcludedPaths(true,false,false);
        expect(onlyDCIM.excludes(new File(internal,"DCIM/Camera/pic.jpg")),"dcim excluded");
        expect(!onlyDCIM.excludes(new File(internal,"Download/ok.jpg")),"other folders remain");
        ExcludedPaths downloads=new ExcludedPaths(false,false,true);
        expect(downloads.excludes(new File(sd,"Downloads/file.zip")),"downloads spelling excluded");
        System.out.println("PASS: volume-specific quarantine, recovery, purge, scan exclusion, nomedia, filter snapshot");
    }
}
