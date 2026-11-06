import com.clearos.ultra.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class V5RecoverySelfTest {
    private static void check(boolean v,String msg) {if(!v)throw new AssertionError(msg);}
    static SafeScanner.Candidate find(File root,File file) {
        SafeScanner.ScanResult found=SafeScanner.scan(Collections.singletonList(root),new SafeScanner.Progress() {
            public boolean isCancelled(){return false;}
            public void onUpdate(long count,String stage){}
        });
        for(SafeScanner.Candidate item:found.candidates)if(item.file.equals(file))return item;
        throw new AssertionError("Expected scanner result for "+file);
    }
    public static void main(String[] args)throws Exception {
        Path root=Files.createTempDirectory("clearos_v5_");
        try {
            File storage=root.toFile();
            File original=root.resolve("Download/old.apk").toFile();
            Files.createDirectories(original.getParentFile().toPath());
            Files.write(original.toPath(),new byte[]{1,2,3,4});
            original.setLastModified(System.currentTimeMillis()-12L*86400000);
            SafeScanner.Candidate c=find(storage,original);
            QuarantineManager m=new QuarantineManager(Collections.singletonList(storage));
            TrashStore.Entry e=m.move(c);
            check(!original.exists(),"Moved into quarantine");
            Files.write(original.toPath(),new byte[]{9,9,9});
            File recovered=m.restoreWithNewName(e);
            check(recovered!=null && recovered.isFile(),"Recovered to alternate name");
            check(Arrays.equals(Files.readAllBytes(original.toPath()),new byte[]{9,9,9}),"Collision never overwritten");
            check(Arrays.equals(Files.readAllBytes(recovered.toPath()),new byte[]{1,2,3,4}),"Recovered payload intact");
            check(m.list().isEmpty(),"Recovered entry removed");
            Files.delete(original.toPath());
            Files.write(original.toPath(),new byte[]{1,2,3,4});
            original.setLastModified(System.currentTimeMillis()-12L*86400000);
            SafeScanner.Candidate next=find(storage,original);
            TrashStore.Entry p=m.move(next);
            final int[] updates={0};
            List<TrashStore.PurgeResult> outcomes=m.purgeBatch(Collections.singletonList(p),(done,total)->{
                check(done==1 && total==1,"Accurate progress");updates[0]++;
            });
            check(updates[0]==1 && outcomes.size()==1 && outcomes.get(0).deleted,"Verified delete and progress callback");
            check(m.list().isEmpty(),"No payload remains");
            check(DisplayPaths.label("0/DCIM/Camera").equals("Dahili depolama / DCIM/Camera"),"Formatted relative path");
            check(DisplayPaths.label("/storage/emulated/0/Download").equals("Dahili depolama / Download"),"Formatted absolute path");
            System.out.println("V5RecoverySelfTest PASS");
        } finally {
            try(java.util.stream.Stream<Path> stream=Files.walk(root)) {
                stream.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}});
            }
        }
    }
}
