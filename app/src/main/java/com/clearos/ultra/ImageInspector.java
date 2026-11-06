package com.clearos.ultra;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;
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
        /** True when the photo list came from Android's media index instead of a folder walk. */
        public boolean fromMediaIndex;
    }

    /** A photo to analyse. width/height are 0 when unknown (then a bounds decode is needed). */
    private static final class Item {
        final File file; final String path; final File root; final int width,height;
        Item(File file,String path,File root,int width,int height){
            this.file=file;this.path=path;this.root=root;this.width=width;this.height=height;}
    }

    /** Supplies photos in batches. */
    private interface Source {
        /** Adds up to n items; returns false when nothing more will come. */
        boolean fill(List<Item> batch,int n);
        boolean hasMore();
        void close();
    }

    /** Per-photo features computed on a worker thread. */
    private static final class Analysis {
        final Item item; final long hash,size,modified; final int width,height;
        final double blurMetric,contrast; final boolean measuredSharpness;
        Analysis(Item item,long hash,long size,long modified,int width,int height,double blurMetric,double contrast,boolean measured){
            this.item=item;this.hash=hash;this.size=size;this.modified=modified;this.width=width;this.height=height;
            this.blurMetric=blurMetric;this.contrast=contrast;this.measuredSharpness=measured;}
    }

    /** Photos analysed in parallel per batch; results are merged in source order, so output is deterministic. */
    private static final int BATCH=96;
    private static final long MIN_BYTES=100*1024, MAX_BYTES=60*SafeScanner.MIB;

    public static Results inspect(List<File> roots, SafeScanner.Progress progress) {
        return inspect(null,roots,progress,6000);
    }
    public static Results inspect(List<File> roots, SafeScanner.Progress progress,int requestedLimit) {
        return inspect(null,roots,progress,requestedLimit);
    }

    /**
     * User-initiated scan. With a Context the photo list comes from MediaStore (one query, newest first,
     * dimensions included) instead of walking every folder of shared storage over FUSE. Folders marked
     * with .nomedia are not in that index; the main file scan still covers them.
     * Falls back to the folder walk when the media index is unavailable.
     */
    public static Results inspect(Context context,List<File> roots, SafeScanner.Progress progress,int requestedLimit) {
        Results result=new Results();
        Map<String,File> canonicalRoots=canonicalRoots(roots);
        Source source=null;
        if(context!=null) {
            source=MediaIndexSource.open(context,canonicalRoots,progress);
            result.fromMediaIndex=source!=null;
        }
        if(source==null) source=new WalkSource(roots,canonicalRoots,progress);
        try {return run(source,progress,requestedLimit,result);}
        finally {source.close();}
    }

    private static Results run(Source source,SafeScanner.Progress progress,int requestedLimit,Results result) {
        Map<String,File> origin=new HashMap<>();
        SimilarityIndex index=new SimilarityIndex();
        final int max=Math.max(1,Math.min(20000,requestedLimit));
        // Decode is CPU-bound with some I/O: use all but one core (max 6) so the UI stays smooth.
        int threads=Math.max(2,Math.min(6,Runtime.getRuntime().availableProcessors()-1));
        ExecutorService pool=Executors.newFixedThreadPool(threads);
        List<Item> batch=new ArrayList<>(BATCH);
        List<Future<Analysis>> futures=new ArrayList<>(BATCH);
        try {
            // Keep one batch decoding while the previous one is merged.
            List<Future<Analysis>> inFlight=submitNext(source,batch,max,0,pool,progress);
            boolean leftover=false;
            while(!inFlight.isEmpty()) {
                futures.clear();futures.addAll(inFlight);
                if(progress.isCancelled()){result.cancelled=true;return result;}
                // Prefetch the next batch while this one is merged (skipped photos never stall the scan).
                inFlight=submitNext(source,batch,max,result.inspected,pool,progress);
                for(Future<Analysis> future:futures) {
                    if(result.inspected>=max)break;
                    Analysis a;
                    try {a=future.get();}
                    catch(ExecutionException e){continue;}
                    catch(InterruptedException e){Thread.currentThread().interrupt();result.cancelled=true;return result;}
                    if (progress.isCancelled()) {result.cancelled=true;return result;}
                    if (a==null) continue;
                    Item item=a.item;
                    // Conservative filter: flat skies and monochrome images are not necessarily blurred.
                    // Still experimental; never auto-select or auto-delete.
                    if(a.measuredSharpness && a.blurMetric<12.0 && a.contrast>35.0
                            && result.blurryCandidates<400 && item.root!=null) {
                        result.candidates.add(new SafeScanner.Candidate(item.file,item.root,a.size,a.modified,
                                SafeScanner.Type.BLURRY,false,
                                "Görüntü netliği düşük olabilir (ölçüm: "+(int)a.blurMetric+"). Görseli incele.",null,null));
                        result.blurryCandidates++;
                    }
                    index.add(new SimilarityIndex.Photo(item.path,a.hash,a.size,a.modified,a.width,a.height));
                    origin.put(item.path,item.root);
                    result.inspected++;
                    if(result.inspected%60==0)progress.onUpdate(result.inspected,"Fotoğraf benzerlikleri karşılaştırılıyor");
                }
                if(result.inspected>=max){leftover=!inFlight.isEmpty();for(Future<Analysis> f:inFlight)f.cancel(true);break;}
            }
            result.limited=result.inspected>=max && (leftover || source.hasMore());
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
                if(root==null || !emitted.add(p.path))continue;
                result.candidates.add(new SafeScanner.Candidate(file,root,p.bytes,p.modified,
                        SafeScanner.Type.SIMILAR,false,
                        "Benzer görüntü; korunması önerilen: "+new File(keep.path).getName()
                                +". Yanlış eşleşme olabilir, mutlaka incele.",new File(keep.path),null));
            }
        }
        return result;
    }

    private static List<Future<Analysis>> submitNext(Source source,List<Item> batch,int max,int done,
                                                     ExecutorService pool,SafeScanner.Progress progress) {
        batch.clear();
        List<Future<Analysis>> out=new ArrayList<>();
        if(done>=max || progress.isCancelled())return out;
        source.fill(batch,BATCH);
        for(Item item:batch) out.add(pool.submit(()->progress.isCancelled()?null:analyse(item)));
        return out;
    }

    // ---------------------------------------------------------------- sources

    /** Android's media index: one query instead of a full-storage walk. */
    private static final class MediaIndexSource implements Source {
        private final Cursor cursor;
        private final Map<String,File> roots;
        private final SafeScanner.Progress progress;
        private final int data,width,height;
        private boolean more;
        private MediaIndexSource(Cursor c,Map<String,File> roots,SafeScanner.Progress progress){
            cursor=c;this.roots=roots;this.progress=progress;
            data=c.getColumnIndex(MediaStore.MediaColumns.DATA);
            width=c.getColumnIndex(MediaStore.MediaColumns.WIDTH);
            height=c.getColumnIndex(MediaStore.MediaColumns.HEIGHT);
            more=data>=0 && c.moveToFirst();
        }
        static Source open(Context context,Map<String,File> roots,SafeScanner.Progress progress) {
            Cursor c=null;
            try {
                Uri uri=MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
                String[] projection={MediaStore.MediaColumns.DATA,MediaStore.MediaColumns.WIDTH,MediaStore.MediaColumns.HEIGHT};
                String selection=MediaStore.MediaColumns.SIZE+">=? AND "+MediaStore.MediaColumns.SIZE+"<=?";
                String[] args={String.valueOf(MIN_BYTES),String.valueOf(MAX_BYTES)};
                c=context.getContentResolver().query(uri,projection,selection,args,
                        MediaStore.MediaColumns.DATE_MODIFIED+" DESC");
                if(c==null)return null;
                MediaIndexSource s=new MediaIndexSource(c,roots,progress);
                if(s.data<0){c.close();return null;}
                return s;
            } catch(RuntimeException e) {
                if(c!=null)try{c.close();}catch(RuntimeException ignored){}
                return null;
            }
        }
        @Override public boolean fill(List<Item> batch,int n) {
            while(more && batch.size()<n) {
                if(progress.isCancelled())return false;
                String path=cursor.getString(data);
                int w=width>=0?cursor.getInt(width):0, h=height>=0?cursor.getInt(height):0;
                more=cursor.moveToNext();
                if(path==null)continue;
                path=path.replace('\\','/');
                String lower=path.toLowerCase(Locale.ROOT);
                if(skipPath(lower)||!isPhoto(lower))continue;
                File root=rootFor(roots,path);
                if(root==null)continue;
                File f=new File(path);
                if(progress.exclude(f))continue;
                batch.add(new Item(f,path,root,w,h));
            }
            return more;
        }
        @Override public boolean hasMore(){return more;}
        @Override public void close(){try{cursor.close();}catch(RuntimeException ignored){}}
    }

    /** Fallback: depth-first walk of the storage roots (previous behaviour). */
    private static final class WalkSource implements Source {
        private final ArrayDeque<File> todo;
        private final Set<String> seen=new HashSet<>();
        private final Map<String,File> roots;
        private final SafeScanner.Progress progress;
        WalkSource(List<File> rootList,Map<String,File> roots,SafeScanner.Progress progress){
            todo=new ArrayDeque<>(rootList);this.roots=roots;this.progress=progress;}
        @Override public boolean fill(List<Item> batch,int n) {
            while(!todo.isEmpty() && batch.size()<n) {
                if(progress.isCancelled())return false;
                File f=todo.removeLast();
                try {
                    if (Files.isSymbolicLink(f.toPath())) continue;
                    String path=f.getCanonicalPath().replace('\\','/');
                    String lower=path.toLowerCase(Locale.ROOT);
                    if (skipPath(lower)||progress.exclude(f)) continue;
                    if (f.isDirectory()) {
                        if (!seen.add(path)) continue;
                        File[] children=f.listFiles();
                        if (children!=null) for(File ch:children) todo.addLast(ch);
                        continue;
                    }
                    if(!isPhoto(lower))continue;
                    long len=f.length();
                    if(len<MIN_BYTES||len>MAX_BYTES)continue;
                    batch.add(new Item(f,path,rootFor(roots,path),0,0));
                } catch (SecurityException|IOException ignored) { }
            }
            return !todo.isEmpty();
        }
        @Override public boolean hasMore(){return !todo.isEmpty();}
        @Override public void close(){}
    }

    // ---------------------------------------------------------------- analysis

    /** Decode one photo at <=256 px and compute dHash + sharpness. Returns null when skipped. */
    private static Analysis analyse(Item item) {
        try {
            // One lstat for exact size + mtime (used later to verify the file before any move).
            BasicFileAttributes attrs=Files.readAttributes(item.file.toPath(),BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if(!attrs.isRegularFile())return null;
            long size=attrs.size(), modified=attrs.lastModifiedTime().toMillis();
            if(size<MIN_BYTES||size>MAX_BYTES)return null;
            BitmapFactory.Options opts=new BitmapFactory.Options();
            int width=item.width,height=item.height;
            if(width<=0||height<=0) {
                // Unknown dimensions (folder walk, some HEIC rows): read the header only.
                opts.inJustDecodeBounds=true;
                BitmapFactory.decodeFile(item.path,opts);
                width=opts.outWidth;height=opts.outHeight;
                opts.inJustDecodeBounds=false;
            }
            if (width<200||height<200) return null;
            opts.inSampleSize=1;
            while (Math.max(width,height)/opts.inSampleSize>256) opts.inSampleSize*=2;
            opts.inPreferredConfig=Bitmap.Config.RGB_565;
            Bitmap decoded=BitmapFactory.decodeFile(item.path,opts);
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
                return new Analysis(item,hash,size,modified,width,height,blur,contrast,measured);
            } finally {decoded.recycle();}
        } catch (SecurityException|IOException|IllegalArgumentException|UnsupportedOperationException|OutOfMemoryError ignored) {
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

    // ---------------------------------------------------------------- helpers

    private static boolean skipPath(String lower) {
        return lower.contains("/android/data/")||lower.endsWith("/android/data")
                ||lower.contains("/android/obb/")||lower.endsWith("/android/obb")
                ||lower.contains("/.trash/")||lower.contains("/.thumbnails/")
                ||lower.contains("/.clearos_quarantine/")||lower.endsWith("/.clearos_quarantine");
    }
    private static int lum(int color) { return ((color>>16)&255)*30/100+((color>>8)&255)*59/100+(color&255)*11/100; }
    private static boolean isPhoto(String lower) {
        return lower.endsWith(".jpg")||lower.endsWith(".jpeg")||lower.endsWith(".png")
                ||lower.endsWith(".webp")||lower.endsWith(".heic")||lower.endsWith(".heif");
    }
    /** Canonical root path -> root File, computed once per scan (no per-photo getCanonicalPath). */
    private static Map<String,File> canonicalRoots(List<File> roots) {
        Map<String,File> out=new HashMap<>();
        for(File r:roots) {
            try{out.put(r.getCanonicalPath().replace('\\','/'),r);}
            catch(IOException|SecurityException ignored){}
        }
        return out;
    }
    private static File rootFor(Map<String,File> roots,String canonicalPath) {
        for(Map.Entry<String,File> r:roots.entrySet())
            if(canonicalPath.startsWith(r.getKey()+"/"))return r.getValue();
        return null;
    }
}
