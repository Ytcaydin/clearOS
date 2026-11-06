package com.clearos.ultra;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure Java locality index for 64-bit difference hashes (computed on Android). */
public final class SimilarityIndex {
    public static final class Photo {
        public final String path;
        public final long hash;
        public final long bytes;
        public final long modified;
        public final int width, height;
        public Photo(String path,long hash,long bytes,long modified,int width,int height) {
            this.path=path;this.hash=hash;this.bytes=bytes;this.modified=modified;
            this.width=width;this.height=height;
        }
    }
    private final ArrayList<Photo> photos=new ArrayList<>();
    private final Map<Integer,List<Integer>> buckets=new HashMap<>();
    private final int[] parents=new int[20000];
    public boolean add(Photo p) {
        if (photos.size()>=parents.length || p.width<64 || p.height<64) return false;
        int i=photos.size();parents[i]=i;photos.add(p);
        Set<Integer> inspected=new HashSet<>();
        for (int k=0;k<4;k++) {
            int key=k*65536+(int)((p.hash >>> (k*16)) & 0xFFFFL);
            List<Integer> prior=buckets.get(key);
            if (prior!=null) for (int j:prior) if (inspected.add(j)) {
                Photo q=photos.get(j);
                double ar1=(double)p.width/p.height, ar2=(double)q.width/q.height;
                if (ar1/ar2 > 1.14 || ar2/ar1 > 1.14) continue;
                // Require close perceptual hashes; never automatically delete results.
                if (Long.bitCount(p.hash^q.hash)<=5) union(i,j);
            }
            buckets.computeIfAbsent(key,a->new ArrayList<>()).add(i);
        }
        return true;
    }
    private int root(int i) { while(parents[i]!=i) {parents[i]=parents[parents[i]];i=parents[i];}return i; }
    private void union(int a,int b) { parents[root(a)]=root(b); }
    public List<List<Photo>> groups() {
        Map<Integer,List<Photo>> groups=new HashMap<>();
        for(int i=0;i<photos.size();i++) groups.computeIfAbsent(root(i),x->new ArrayList<>()).add(photos.get(i));
        ArrayList<List<Photo>> found=new ArrayList<>();
        for(List<Photo> group:groups.values()) if(group.size()>=2) {
            group.sort((a,b)->Long.compare((long)b.width*b.height,(long)a.width*a.height));
            found.add(group);
        }
        found.sort((a,b)->Integer.compare(b.size(),a.size()));
        return found;
    }
    public int count() {return photos.size();}
}
