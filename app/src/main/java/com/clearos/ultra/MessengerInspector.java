package com.clearos.ultra;

import android.os.Environment;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/** Analyzes only shareable, accessible files; never touches message databases. */
public final class MessengerInspector {
    public static final class Item {
        public final File file;
        public final String kind;
        public final long size, modified;
        Item(File f,String kind) {file=f;this.kind=kind;size=f.length();modified=f.lastModified();}
    }
    public static final class Result {
        public long total;
        public final Map<String,Long> byType=new HashMap<>();
        public final List<Item> large=new ArrayList<>();
        public int count;
    }
    private MessengerInspector() { }
    public static Result scan() {
        File storage=Environment.getExternalStorageDirectory();
        String[] folders={"Android/media/com.whatsapp/WhatsApp/Media",
                "Android/media/com.whatsapp.w4b/WhatsApp Business/Media",
                "Android/media/org.telegram.messenger/Telegram",
                "Android/media/org.thunderdog.challegram/Telegram",
                "WhatsApp/Media", "Telegram"};
        Result result=new Result();
        Set<String> seen=new HashSet<>();
        ArrayDeque<File> pending=new ArrayDeque<>();
        for(String s:folders){File root=new File(storage,s);if(root.isDirectory())pending.add(root);}
        while(!pending.isEmpty()&&result.count<100000) {
            File entry=pending.removeLast();
            try {
                if(Files.isSymbolicLink(entry.toPath()))continue;
                if(entry.isDirectory()) {
                    if(!seen.add(entry.getCanonicalPath()))continue;
                    File[] children=entry.listFiles();if(children!=null)for(File f:children)pending.add(f);
                }else if(entry.isFile()){
                    String lower=entry.getName().toLowerCase(Locale.ROOT);
                    String kind= lower.matches(".*\\.(jpg|jpeg|png|webp|heic|gif)")?"Fotoğraf":
                            lower.matches(".*\\.(mp4|mov|mkv|avi|webm|3gp)")?"Video":
                            lower.matches(".*\\.(mp3|opus|ogg|m4a|aac|wav|amr)")?"Ses": "Belge / diğer";
                    result.count++;result.total+=entry.length();
                    result.byType.merge(kind,entry.length(),Long::sum);
                    if(entry.length()>=8*SafeScanner.MIB)result.large.add(new Item(entry,kind));
                }
            }catch(Exception ignored){}
        }
        result.large.sort(Comparator.comparingLong((Item a)->-a.size));
        if(result.large.size()>100)result.large.subList(100,result.large.size()).clear();
        return result;
    }
}
