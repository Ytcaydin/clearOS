package com.clearos.ultra;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.StatFs;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Daily on-device ring buffer; no telemetry. */
public final class StorageHistory {
    private StorageHistory() {}
    public static final class Reading {
        public final long day, used, total;
        Reading(long d,long u,long t) {day=d;used=u;total=t;}
    }
    public static long todayLocal() { return LocalDate.now().toEpochDay(); }
    public static void record(Context c) {
        StatFs stat=new StatFs(Environment.getExternalStorageDirectory().getAbsolutePath());
        long total=stat.getTotalBytes(),used=total-stat.getAvailableBytes();
        long today=todayLocal();
        SharedPreferences p=c.getSharedPreferences("usage_history",0);
        List<Reading> entries=list(c);
        entries.removeIf(r->r.day==today || r.day<today-365);
        entries.add(new Reading(today,used,total));
        StringBuilder b=new StringBuilder();
        for(Reading r:entries) b.append(r.day).append(',').append(r.used).append(',').append(r.total).append(';');
        p.edit().putString("points",b.toString()).apply();
    }
    public static List<Reading> list(Context c) {
        ArrayList<Reading> entries=new ArrayList<>();
        String data=c.getSharedPreferences("usage_history",0).getString("points","");
        for(String line:data.split(";"))try {
            String[] parts=line.split(",");
            if(parts.length==3)entries.add(new Reading(Long.parseLong(parts[0]),Long.parseLong(parts[1]),Long.parseLong(parts[2])));
        } catch(Exception ignored) { }
        return entries;
    }
}
