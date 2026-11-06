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
    /** User-initiated scan. Limit bounds memory and battery use; no automatic deletion. */
    public static Results inspect(List<File> roots, SafeScanner.Progress progress,int requestedLimit) {
        Results result=new Results();
        ArrayDeque<File> todo=new ArrayDeque<>(roots);
        Set<String> seen=new HashSet<>();
        Map<String,File> origin=new HashMap<>();
        SimilarityIndex index=new SimilarityIndex();
        final int max=Math.max(1,Math.min(20000,requestedLimit));
        while(!todo.isEmpty() && result.inspected<max) {
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
                BitmapFactory.Options bounds=new BitmapFactory.Options();
                bounds.inJustDecodeBounds=true;
                BitmapFactory.decodeFile(path,bounds);
                if (bounds.outWidth<200||bounds.outHeight<200) continue;
                bounds.inJustDecodeBounds=false;
                bounds.inSampleSize=1;
                while (Math.max(bounds.outWidth,bounds.outHeight)/bounds.inSampleSize>256) bounds.inSampleSize*=2;
                bounds.inPreferredConfig=Bitmap.Config.RGB_565;
                Bitmap decoded=BitmapFactory.decodeFile(path,bounds);
                if(decoded==null)continue;
                Bitmap thumb=Bitmap.createScaledBitmap(decoded,9,8,true);
                long hash=0;
                for(int y=0;y<8;y++) for(int x=0;x<8;x++) {
                    int l=lum(thumb.getPixel(x,y)), r=lum(thumb.getPixel(x+1,y));
                    hash=(hash<<1)|(l>r?1L:0L);
                }
                thumb.recycle();
                if (decoded.getWidth()>=80 && decoded.getHeight()>=80) {
                    Bitmap sharpSample=Bitmap.createScaledBitmap(decoded,160,160,true);
                    double blurMetric=laplacianVariance(sharpSample);
                    double contrast=luminanceVariation(sharpSample);
                    sharpSample.recycle();
                    // Conservative filter: flat skies and monochrome images are not necessarily blurred.
                    // Still experimental; never auto-select or auto-delete.
                    if(blurMetric<12.0 && contrast>35.0
                            && result.blurryCandidates<400) {
                        File root=rootFor(roots,f);
                        if(root!=null) {
                            result.candidates.add(new SafeScanner.Candidate(f,root,f.length(),f.lastModified(),
                                    SafeScanner.Type.BLURRY,false,
                                    "Görüntü netliği düşük olabilir (ölçüm: "+(int)blurMetric+"). Görseli incele.",null,null));
                            result.blurryCandidates++;
                        }
                    }
                }
                decoded.recycle();
                SimilarityIndex.Photo p=new SimilarityIndex.Photo(path,hash,f.length(),f.lastModified(),
                        bounds.outWidth,bounds.outHeight);
                index.add(p);
                origin.put(path,rootFor(roots,f));
                result.inspected++;
                if(result.inspected%60==0)progress.onUpdate(result.inspected,"Fotoğraf benzerlikleri karşılaştırılıyor");
            } catch (SecurityException|IOException|IllegalArgumentException|OutOfMemoryError ignored) { }
        }
        result.limited=result.inspected>=max && !todo.isEmpty();
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
    private static double luminanceVariation(Bitmap bmp) {
        double sum=0,square=0;int count=0;
        for(int y=0;y<bmp.getHeight();y+=4) for(int x=0;x<bmp.getWidth();x+=4) {
            int value=lum(bmp.getPixel(x,y)); sum+=value;square+=(long)value*value;count++;
        }
        double mean=sum/count;
        return Math.sqrt(Math.max(0,square/count-mean*mean));
    }
    private static double laplacianVariance(Bitmap bmp) {
        int w=bmp.getWidth(),h=bmp.getHeight();
        double sum=0,squares=0;int count=0;
        for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++) {
            int center=lum(bmp.getPixel(x,y));
            int value=4*center-lum(bmp.getPixel(x-1,y))-lum(bmp.getPixel(x+1,y))
                    -lum(bmp.getPixel(x,y-1))-lum(bmp.getPixel(x,y+1));
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
