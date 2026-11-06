import com.clearos.ultra.*;
import java.io.File;
import java.nio.file.*;
import java.util.*;

/** Exercises the V4 batch deletion path with missing/modified entries and repeat attempts. */
public final class BatchPurgeSelfTest {
    static void ok(boolean value,String why) {if(!value)throw new AssertionError(why);}
    static SafeScanner.Candidate candidate(File root,File file) {
        SafeScanner.ScanResult scan=SafeScanner.scan(Collections.singletonList(root),new SafeScanner.Progress(){
            public boolean isCancelled(){return false;}
            public void onUpdate(long n,String p){}
        });
        for(SafeScanner.Candidate c:scan.candidates)if(c.file.equals(file))return c;
        throw new AssertionError("Candidate missing "+file);
    }
    static File create(File root,String name,byte[] data) throws Exception {
        File file=new File(root,"Download/"+name);
        Files.createDirectories(file.getParentFile().toPath());Files.write(file.toPath(),data);
        file.setLastModified(System.currentTimeMillis()-12L*86400000);return file;
    }
    public static void main(String[] args) throws Exception {
        File root=Files.createTempDirectory("clearos4-batch-").toFile();
        File a=create(root,"a.apk",new byte[]{1,2,3});
        File b=create(root,"b.apk",new byte[]{4,5,6,7});
        File c=create(root,"c.apk",new byte[]{8,9});
        QuarantineManager vault=new QuarantineManager(Collections.singletonList(root));
        TrashStore.Entry e1=vault.move(candidate(root,a));
        TrashStore.Entry e2=vault.move(candidate(root,b));
        TrashStore.Entry e3=vault.move(candidate(root,c));
        ok(vault.list().size()==3,"3 entries");
        List<TrashStore.PurgeResult> done=vault.purgeBatch(Arrays.asList(e1,e2,e3));
        ok(done.size()==3,"batch size");
        for(TrashStore.PurgeResult result:done)ok(result.deleted,"valid entry deleted: "+result.reason);
        ok(vault.list().isEmpty(),"no stale entries");
        ok(!e1.payload.exists()&&!e2.payload.exists()&&!e3.payload.exists(),"payloads actually removed");
        List<TrashStore.PurgeResult> repeated=vault.purgeBatch(Collections.singletonList(e1));
        ok(repeated.size()==1&&!repeated.get(0).deleted,"deleted entry cannot be deleted twice");
        File d=create(root,"d.apk",new byte[]{1,8,3});
        TrashStore.Entry real=vault.move(candidate(root,d));
        TrashStore.Entry forged=new TrashStore.Entry(real.id,new File(root,"other.apk"),real.payload,real.size,real.timestamp);
        List<TrashStore.PurgeResult> spoof=vault.purgeBatch(Collections.singletonList(forged));
        ok(spoof.size()==1&&!spoof.get(0).deleted,"metadata spoof rejected");
        ok(real.payload.exists(),"spoof cannot delete real payload");
        ok(vault.restore(real)&&d.exists(),"restore still works after rejected spoof");
        // Batch restore: valid entries restored once, spoofed/duplicate/conflicting ones rejected.
        File f1=create(root,"f1.apk",new byte[]{5,5,5}),f2=create(root,"f2.apk",new byte[]{6,6,6,6});
        File f3=create(root,"f3.apk",new byte[]{7});
        TrashStore.Entry r1=vault.move(candidate(root,f1)),r2=vault.move(candidate(root,f2)),r3=vault.move(candidate(root,f3));
        Files.write(f3.toPath(),new byte[]{9,9}); // original path re-occupied -> must not overwrite
        TrashStore.Entry fake=new TrashStore.Entry(r2.id,new File(root,"elsewhere.apk"),r2.payload,r2.size,r2.timestamp);
        Set<String> back=vault.restoreBatch(Arrays.asList(r1,fake,r2,r2,r3));
        ok(back.size()==2&&back.contains(r1.id)&&back.contains(r2.id),"batch restore set: "+back);
        ok(f1.exists()&&f2.exists()&&f1.length()==3&&f2.length()==4,"batch restored originals");
        ok(Files.readAllBytes(f3.toPath()).length==2&&r3.payload.exists(),"conflict not overwritten");
        ok(vault.list().size()==1,"only conflicting entry left");
        // Same conflicting entry, now with rename-on-conflict + progress: restored beside, never overwritten.
        final int[] last={0,0};
        Set<String> renamed=vault.restoreBatch(Arrays.asList(r3,r3),true,(n,t)->{last[0]=n;last[1]=t;});
        File side=new File(f3.getParentFile(),"f3 (recovered 1).apk");
        ok(renamed.size()==1&&renamed.contains(r3.id),"rename batch restored once: "+renamed);
        ok(last[0]==2&&last[1]==2,"progress reaches total: "+last[0]+"/"+last[1]);
        ok(side.isFile()&&Files.readAllBytes(side.toPath()).length==1,"recovered copy intact");
        ok(Files.readAllBytes(f3.toPath()).length==2,"original target still untouched");
        ok(vault.list().isEmpty(),"quarantine empty after rename restore");
        System.out.println("PASS: batch purge, repeated delete, spoof reject, restoration, batch restore, payload verification");
    }
}
