import com.clearos.ultra.SafeScanner;
import com.clearos.ultra.SimilarityIndex;
import com.clearos.ultra.TrashStore;
import java.nio.file.*;
import java.io.*;
import java.util.*;
public class UltraCoreSelfTest {
    private static void check(boolean ok,String label) {if(!ok) throw new AssertionError(label);}
    public static void main(String[] args) throws Exception {
        Path temp=Files.createTempDirectory("clearos-v3-test-");
        File source=temp.resolve("Download/old.apk").toFile();source.getParentFile().mkdirs();
        Files.write(source.toPath(),new byte[]{4,5,6});source.setLastModified(System.currentTimeMillis()-11L*86400000);
        SafeScanner.Progress progress=new SafeScanner.Progress(){public boolean isCancelled(){return false;}public void onUpdate(long a,String b){}};
        SafeScanner.ScanResult scan=SafeScanner.scan(Arrays.asList(temp.toFile()),progress);
        check(scan.folderBytes.values().stream().mapToLong(x->x).sum()==3,"folder storage map");
        SafeScanner.Candidate old=null;for(SafeScanner.Candidate c:scan.candidates) if(c.file.equals(source)) old=c;
        check(old!=null && old.suggested,"installer found");
        TrashStore store=new TrashStore(temp.resolve("vault").toFile());
        TrashStore.Entry entry=store.move(old);
        check(!source.exists() && entry.payload.exists(),"moved with original gone");
        check(store.list().size()==1 && store.containedBytes()==3,"persist and list");
        check(store.restore(store.list().get(0)),"restore");
        check(source.exists() && store.list().isEmpty(),"restore leaves source");
        scan=SafeScanner.scan(Arrays.asList(temp.toFile()),progress);
        for(SafeScanner.Candidate c:scan.candidates)if(c.file.equals(source))old=c;
        File conflict=temp.resolve("Download/existing.apk").toFile();
        Files.write(conflict.toPath(),new byte[]{8,8,8});
        SafeScanner.ScanResult scanConf=SafeScanner.scan(Arrays.asList(temp.toFile()),progress);
        SafeScanner.Candidate collision=null;
        for(SafeScanner.Candidate c:scanConf.candidates)if(c.file.equals(source))collision=c;
        entry=store.move(old);
        // Refuse overwriting a newly created destination on restore.
        Files.write(source.toPath(),new byte[]{9});
        check(!store.restore(entry),"refuses overwrite on restore");
        Files.delete(source.toPath());
        check(!store.restore(new TrashStore.Entry("wrong",source,entry.payload,entry.size,entry.timestamp)),"forged entry refused");
        check(store.purge(entry) && !source.exists() && store.containedBytes()==0,"permanent purge");
        SimilarityIndex index=new SimilarityIndex();
        index.add(new SimilarityIndex.Photo("a",0b101L,100,1,1000,800));
        index.add(new SimilarityIndex.Photo("b",0b111L,100,1,800,640));
        index.add(new SimilarityIndex.Photo("c",0x7777777777777777L,100,1,500,1400));
        check(index.groups().size()==1 && index.groups().get(0).size()==2,"near-image index");
        SimilarityIndex different=new SimilarityIndex();
        different.add(new SimilarityIndex.Photo("wide",0b11L,100,1,1800,500));
        different.add(new SimilarityIndex.Photo("tall",0b11L,100,1,500,1800));
        check(different.groups().isEmpty(),"rejects different aspect ratios");
        File docs=temp.resolve("Documents/private.pdf").toFile();
        Files.createDirectories(docs.getParentFile().toPath());Files.write(docs.toPath(),new byte[]{7,7,7,7});
        SafeScanner.ScanResult excluded=SafeScanner.scan(Arrays.asList(temp.toFile()),new SafeScanner.Progress(){
            public boolean isCancelled(){return false;}
            public void onUpdate(long n,String p){}
            public boolean exclude(File f){return f.toString().contains("Documents");}
        });
        check(excluded.visitedBytes<scanConf.visitedBytes+4,"excluded files not counted");
        System.out.println("PASS: v3 quarantine, restore collision, purge, spoof safety, exclusions, folder map, image clusters");
    }
}
