package com.clearos.ultra;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Local-only diagnostic history; no analytics, networking or file content. */
public final class ActionLog {
    private ActionLog(){}
    public static synchronized void add(Context context,String action,String result) {
        SharedPreferences prefs=context.getSharedPreferences("action_log",0);
        String stamp=new SimpleDateFormat("dd.MM.yyyy HH:mm:ss",Locale.getDefault()).format(new Date());
        String msg=stamp+" · "+safe(action)+" · "+safe(result);
        String old=prefs.getString("events","");
        StringBuilder next=new StringBuilder(msg);
        int count=1;
        for(String line:old.split("\\n")) {
            if(count++>=180)break;
            if(!line.isEmpty())next.append('\n').append(line);
        }
        prefs.edit().putString("events",next.toString()).apply();
    }
    public static String read(Context context) {return context.getSharedPreferences("action_log",0).getString("events","");}
    private static String safe(String data) {
        if(data==null)return "";
        return data.replace('\n',' ').replace('\r',' ').substring(0,Math.min(data.length(),260));
    }
    public static void clear(Context c){c.getSharedPreferences("action_log",0).edit().remove("events").apply();}
}
