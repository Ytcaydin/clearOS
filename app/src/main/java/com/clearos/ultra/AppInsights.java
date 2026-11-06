package com.clearos.ultra;

import android.app.AppOpsManager;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.storage.StorageManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Installed application inventory, read-only; user controls any app removal in system Settings. */
public final class AppInsights {
    public static final class App {
        public final String packageName, name;
        public final long lastUsed, storage;
        public final boolean system;
        App(String p,String n,long used,long bytes,boolean system) {
            packageName=p;name=n;lastUsed=used;storage=bytes;this.system=system;
        }
    }
    public static boolean hasUsageAccess(Context c) {
        AppOpsManager ops=(AppOpsManager)c.getSystemService(Context.APP_OPS_SERVICE);
        return ops!=null && ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),c.getPackageName())==AppOpsManager.MODE_ALLOWED;
    }
    public static List<App> getApps(Context context) {
        PackageManager pm=context.getPackageManager();
        boolean permission=hasUsageAccess(context);
        Map<String, Long> activity=new HashMap<>();
        if(permission) {
            UsageStatsManager us=(UsageStatsManager)context.getSystemService(Context.USAGE_STATS_SERVICE);
            if(us!=null) {
                long now=System.currentTimeMillis();
                List<UsageStats> stats=us.queryUsageStats(UsageStatsManager.INTERVAL_YEARLY,now-180L*86400000L,now);
                if(stats!=null) for(UsageStats u:stats)
                    activity.merge(u.getPackageName(),u.getLastTimeUsed(),Math::max);
            }
        }
        StorageStatsManager store=(StorageStatsManager)context.getSystemService(Context.STORAGE_STATS_SERVICE);
        ArrayList<App> apps=new ArrayList<>();
        for(ApplicationInfo info:pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
            if(info.packageName.equals(context.getPackageName())) continue;
            boolean system=(info.flags & ApplicationInfo.FLAG_SYSTEM)!=0;
            long last=activity.getOrDefault(info.packageName,0L);
            long bytes=-1;
            if(permission && store!=null) {
                try {
                    StorageStats details=store.queryStatsForPackage(StorageManager.UUID_DEFAULT,
                            info.packageName, Process.myUserHandle());
                    bytes=details.getAppBytes()+details.getDataBytes();
                } catch (Exception ignored) { }
            }
            CharSequence name=pm.getApplicationLabel(info);
            apps.add(new App(info.packageName,name==null?info.packageName:name.toString(),last,bytes,system));
        }
        // Unused first (when usage access is granted), then biggest apps.
        apps.sort(Comparator.comparing((App a)->a.system)
                .thenComparingLong(a->permission ? a.lastUsed : -a.storage)
                .thenComparing(a->a.name));
        return apps;
    }
}
