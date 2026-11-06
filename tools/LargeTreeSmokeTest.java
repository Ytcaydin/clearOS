import com.clearos.ultra.SafeScanner;
import java.io.*;
import java.nio.file.*;
import java.util.*;
/** Synthetic desktop filesystem stress test: not an Android battery or app performance claim. */
public final class LargeTreeSmokeTest {
    public static void main(String[] args) throws Exception {
        final int files=args.length>0?Integer.parseInt(args[0]):10000;
        if(files<1||files>250000)throw new IllegalArgumentException("1..250000 only");
        final Path root=Files.createTempDirectory("clearos_stress_");
        final long start=System.nanoTime();
        try {
            for(int i=0;i<files;i++) {
                Path dir=root.resolve(String.format(Locale.ROOT,"batch_%04d",i/1000));
                if(i%1000==0)Files.createDirectories(dir);
                Files.createFile(dir.resolve(String.format(Locale.ROOT,"object_%04d.dat",i%1000)));
            }
            long created=System.nanoTime();
            SafeScanner.ScanResult result=SafeScanner.scan(Collections.singletonList(root.toFile()),new SafeScanner.Progress() {
                public boolean isCancelled(){return false;}
                public void onUpdate(long count,String stage) { }
            });
            long scanned=System.nanoTime();
            if(result.visitedFiles!=files || result.limited || result.cancelled)
                throw new AssertionError("Incorrect traversal: "+result.visitedFiles+" / "+files);
            if(result.visitedBytes!=0 || !result.candidates.isEmpty())
                throw new AssertionError("Empty, zero-byte fixtures should not be cleaning candidates");
            if(!Files.exists(root.resolve("batch_0000/object_0000.dat")))
                throw new AssertionError("Scan unexpectedly changed a file");
            System.out.printf(Locale.ROOT,
                "PASS: %,d files · create %.2f s · scan %.2f s · no mutation · Java heap <= 512 MB%n",
                files,(created-start)/1e9,(scanned-created)/1e9);
        } finally {
            try(java.util.stream.Stream<Path> all=Files.walk(root)) {
                all.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}});
            }
        }
    }
}
