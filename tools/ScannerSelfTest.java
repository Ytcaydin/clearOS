import com.clearos.ultra.SafeScanner;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Run on any JDK without Android: javac ... && java ScannerSelfTest */
public final class ScannerSelfTest {
    private static void assertThat(boolean condition,String description) {
        if (!condition) throw new AssertionError(description);
    }
    private static File write(Path p,byte[] bytes,long oldDays) throws Exception {
        Files.createDirectories(p.getParent());
        Files.write(p,bytes);
        File f=p.toFile();
        if (oldDays>0) assertThat(f.setLastModified(System.currentTimeMillis()-oldDays*86_400_000L),"setLastModified");
        return f;
    }
    public static void main(String[] args) throws Exception {
        Path root=Files.createTempDirectory("clearos-test-");
        File a=write(root.resolve("DCIM/Camera/photo.jpg"),new byte[1200],0);
        File b=write(root.resolve("Pictures/photo-copy.jpg"),new byte[1200],0);
        File installer=write(root.resolve("Download/test.apk"),new byte[]{1,2,3},15);
        File tmp=write(root.resolve("Download/cache.tmp"),new byte[]{4,5,6},15);
        File personal=write(root.resolve("Android/data/secret.apk"),new byte[]{7,8,9},30);
        File screen=write(root.resolve("Pictures/Screenshots/screenshot_1.png"),new byte[]{2,3,4},0);
        Files.createDirectories(root.resolve("Download/EmptyFolder"));
        SafeScanner.Progress callback=new SafeScanner.Progress() {
            public boolean isCancelled() { return false; }
            public void onUpdate(long count,String path) { }
        };
        SafeScanner.ScanResult r=SafeScanner.scan(Arrays.asList(root.toFile()),callback);
        assertThat(!r.cancelled,"completed scan");
        assertThat(r.visitedFiles==5,"scans 5 accessible files, ignores Android/data");
        boolean duplicate=false,apks=false,temp=false,screenFound=false,empty=false,forbidden=false;
        SafeScanner.Candidate dupe=null;
        for (SafeScanner.Candidate c:r.candidates) {
            if (c.file.equals(a)||c.file.equals(b)) {
                if (c.type==SafeScanner.Type.DUPLICATE) { duplicate=true; dupe=c; }
            }
            if (c.file.equals(installer)) apks=c.type==SafeScanner.Type.INSTALLER&&c.suggested;
            if (c.file.equals(tmp)) temp=c.type==SafeScanner.Type.TEMP&&c.suggested;
            if (c.file.equals(screen)) screenFound=c.type==SafeScanner.Type.SCREENSHOT;
            if (c.type==SafeScanner.Type.EMPTY) empty=true;
            if (c.file.equals(personal)) forbidden=true;
        }
        assertThat(duplicate && apks && temp && screenFound && empty && !forbidden,"categories correct");
        assertThat(dupe!=null && dupe.suggested,"hash verified duplicate suggested");
        assertThat(dupe.duplicateKeep.equals(a),"prefer camera original");
        assertThat(SafeScanner.deleteVerified(dupe),"remove exact duplicate");
        assertThat(a.exists() && !b.exists(),"camera original remains");
        SafeScanner.ScanResult r2=SafeScanner.scan(Arrays.asList(root.toFile()),callback);
        SafeScanner.Candidate oldInstall=null;
        for (SafeScanner.Candidate c:r2.candidates) if (c.file.equals(installer)) oldInstall=c;
        assertThat(oldInstall!=null,"installer found");
        installer.setLastModified(System.currentTimeMillis());
        assertThat(!SafeScanner.deleteVerified(oldInstall),"changed file fails deletion verification");
        assertThat(installer.exists(),"changed file preserved");
        System.out.println("PASS: categories, duplicate SHA256, protected roots, original preservation, changed-file protection.");
    }
}
