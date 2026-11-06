package com.clearos.ultra;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Optional, user-initiated perceptual image scan. Review-only; never suggested for auto-deletion. */
public final class ImageInspector {
    private ImageInspector() {}
    public static final class Results {
        public final List<SafeScanner.Candidate> candidates=new ArrayList<>();
        public int inspected;
        public boolean limited, cancelled;
        public int similarGroups;
        public int blurryCandidates;
    }
    public static Results inspect(List<File> roots, SafeScanner.Progress progress) {
        return inspect(roots,progress,6000);
    }
    /** Per-photo features computed on a worker thread. */
    private static final class Analysis {
        final File file; final String path; final long hash; final int width,height;
        final double blurMetric,contrast; final boolean measuredSharpness;
        Analysis(File file,String path,long hash,int width,int height,double blurMetric,double contrast,boolean measured){
            this.file=file;this.path=path;this.hash=hash;this.width=width;this.height=height;
            this.blurMetric=blurMetric;this.contrast=contrast;this.measuredSharpness=measured;}
    }

    /** Photos analysed in parallel per batch; results are merged in walk order, so output is deterministic. */
    private static final int BATCH=60;

    /** User-initiated scan. Limit bounds memory and battery use; no automatic deletion. */
    public static Results inspect(List<File> roots, SafeScanner.Progress progress,int requestedLimit) {
        Results result=new Results();
        ArrayDeque<File> todo=new ArrayDeque<>(roots);
        Set<String> seen=new HashSet<>();
        Map<String,File> origin=new HashMap<>();
        SimilarityIndex index=new SimilarityIndex();
        final int max=Math.max(1,Math.min(20000,requestedLimit));
        // Decoding is CPU-bound: use most cores but leave one for the UI.
        int threads=Math.max(1,Math.min(4,Runtime.getRuntime().availableProcessors()-1));
        ExecutorService pool=Executors.newFixedThreadPool(threads);
        List<File> batch=new ArrayList<>(BATCH);
        try {
            while(result.inspected<max) {
                // 1) Walk (cheap) until a batch of photo-like files is collected.
                batch.clear();
                int room=max-result.inspected;
                while(!todo.isEmpty() && batch.size()<Math.min(BATCH,room)) {
                    if (progress.isCancelled()) {result.cancelled=true;return result;}
                    File f=todo.removeLast();
                    try {
                        if (Files.isSymbolicLink(f.toPath())) continue;
                        String path=f.getCanonicalPath().replace('\\','/');
                        String lower=path.toLowerCase(Locale.ROOT);
                        if (lower.contains("/android/data/")||lower.endsWith("/android/data")
                                ||lower.contains("/android/obb/")||lower.endsWith("/android/obb")
                                ||lower.contains("/.trash/")||lower.contains("/.thumbnails/")
                                ||lower.contains("/.clearos_quarantine/")||lower.endsWith("/.clearos_quarantine")
                                ||progress.exclude(f)) continue;
                        if (f.isDirectory()) {
                            if (!seen.add(path)) continue;
                            File[] children=f.listFiles();
                            if (children!=null) for(File ch:children) todo.addLast(ch);
                            continue;
                        }
                        if(!isPhoto(f.getName())||f.length()<100*1024||f.length()>60*SafeScanner.MIB)continue;
                        batch.add(f);
                    } catch (SecurityException|IOException ignored) { }
                }
                if(batch.isEmpty())break;
                // 2) Decode + measure the batch in parallel.
                List<Future<Analysis>> futures=new ArrayList<>(batch.size());
                for(File f:batch) futures.add(pool.submit(()->progress.isCancelled()?null:analyse(f)));
                // 3) Merge sequentially, in walk order.
                for(Future<Analysis> future:futures) {
                    Analysis a;
                    try {a=future.get();}
                    catch(ExecutionException e){continue;}
                    catch(InterruptedException e){Thread.currentThread().interrupt();result.cancelled=true;return result;}
                    if (progress.isCancelled()) {result.cancelled=true;return result;}
                    if (a==null) continue;
                    try {
                        File root=rootFor(roots,a.file);
                        // Conservative filter: flat skies and monochrome images are not necessarily blurred.
                        // Still experimental; never auto-select or auto-delete.
                        if(a.measuredSharpness && a.blurMetric<12.0 && a.contrast>35.0
                                && result.blurryCandidates<400 && root!=null) {
                            result.candidates.add(new SafeScanner.Candidate(a.file,root,a.file.length(),a.file.lastModified(),
                                    SafeScanner.Type.BLURRY,false,
                                    "Görüntü netliği düşük olabilir (ölçüm: "+(int)a.blurMetric+"). Görseli incele.",null,null));
                            result.blurryCandidates++;
                        }
                        index.add(new SimilarityIndex.Photo(a.path,a.hash,a.file.length(),a.file.lastModified(),a.width,a.height));
                        origin.put(a.path,root);
                        result.inspected++;
                        if(result.inspected%60==0)progress.onUpdate(result.inspected,"Fotoğraf benzerlikleri karşılaştırılıyor");
                    } catch (SecurityException|IOException ignored) { }
                    if(result.inspected>=max)break;
                }
            }
            result.limited=result.inspected>=max && !todo.isEmpty();
        } finally {
            pool.shutdownNow();
        }
        Set<String> emitted=new HashSet<>();
        for(List<SimilarityIndex.Photo> group:index.groups()) {
            result.similarGroups++;
            SimilarityIndex.Photo keep=group.get(0); // highest resolution, never auto deleted
            for(int i=1;i<group.size();i++) {
                SimilarityIndex.Photo p=group.get(i);
                File file=new File(p.path), root=origin.get(p.path);
                if(root==null || !file.isFile() || !emitted.add(p.path))continue;
                result.candidates.add(new SafeScanner.Candidate(file,root,p.bytes,p.modified,
                        SafeScanner.Type.SIMILAR,false,
                        "Benzer görüntü; korunması önerilen: "+new File(keep.path).getName()
                                +". Yanlış eşleşme olabilir, mutlaka incele.",new File(keep.path),null));
            }
        }
        return result;
    }

    /** Decode one photo at <=256 px and compute dHash + sharpness. Returns null when skipped. */
    private static Analysis analyse(File f) {
        try {
            String path=f.getCanonicalPath().replace('\\','/');
            BitmapFactory.Options bounds=new BitmapFactory.Options();
            bounds.inJustDecodeBounds=true;
            BitmapFactory.decodeFile(path,bounds);
            if (bounds.outWidth<200||bounds.outHeight<200) return null;
            int width=bounds.outWidth,height=bounds.outHeight;
            bounds.inJustDecodeBounds=false;
            bounds.inSampleSize=1;
            while (Math.max(width,height)/bounds.inSampleSize>256) bounds.inSampleSize*=2;
            bounds.inPreferredConfig=Bitmap.Config.RGB_565;
            Bitmap decoded=BitmapFactory.decodeFile(path,bounds);
            if(decoded==null)return null;
            try {
                Bitmap thumb=Bitmap.createScaledBitmap(decoded,9,8,true);
                int[] t=new int[72];
                thumb.getPixels(t,0,9,0,0,9,8); // one bulk read instead of 128 getPixel() JNI calls
                thumb.recycle();
                long hash=0;
                for(int y=0;y<8;y++) for(int x=0;x<8;x++) {
                    int l=lum(t[y*9+x]), r=lum(t[y*9+x+1]);
                    hash=(hash<<1)|(l>r?1L:0L);
                }
                double blur=10000,contrast=0;boolean measured=false;
                if (decoded.getWidth()>=80 && decoded.getHeight()>=80) {
                    Bitmap sharpSample=Bitmap.createScaledBitmap(decoded,160,160,true);
                    int[] px=new int[160*160];
                    sharpSample.getPixels(px,0,160,0,0,160,160); // ~128k getPixel() calls -> 1
                    sharpSample.recycle();
                    int[] l=new int[px.length];
                    for(int i=0;i<px.length;i++) l[i]=lum(px[i]);
                    blur=laplacianVariance(l,160,160);
                    contrast=luminanceVariation(l,160,160);
                    measured=true;
                }
                return new Analysis(f,path,hash,width,height,blur,contrast,measured);
            } finally {decoded.recycle();}
        } catch (SecurityException|IOException|IllegalArgumentException|OutOfMemoryError ignored) {
            return null;
        }
    }
    /** Same sampling as before (every 4th pixel in both axes). */
    private static double luminanceVariation(int[] l,int w,int h) {
        double sum=0,square=0;int count=0;
        for(int y=0;y<h;y+=4) for(int x=0;x<w;x+=4) {
            int value=l[y*w+x]; sum+=value;square+=(long)value*value;count++;
        }
        double mean=sum/count;
        return Math.sqrt(Math.max(0,square/count-mean*mean));
    }
    private static double laplacianVariance(int[] l,int w,int h) {
        double sum=0,squares=0;int count=0;
        for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++) {
            int i=y*w+x;
            int value=4*l[i]-l[i-1]-l[i+1]-l[i-w]-l[i+w];
            sum+=value;squares+=(long)value*value;count++;
        }
        if(count==0)return 10000;
        return squares/count-(sum/count)*(sum/count);
    }
    private static int lum(int color) { return ((color>>16)&255)*30/100+((color>>8)&255)*59/100+(color&255)*11/100; }
    private static boolean isPhoto(String s) {
        String lower=s.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg")||lower.endsWith(".jpeg")||lower.endsWith(".png")
                ||lower.endsWith(".webp")||lower.endsWith(".heic")||lower.endsWith(".heif");
    }
    private static File rootFor(List<File> roots,File f) throws IOException {
        String p=f.getCanonicalPath();
        for(File r:roots) {String root=r.getCanonicalPath();if(p.startsWith(root+File.separator))return r;}
        return null;
    }
}
