package com.clearos.ultra;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Charging-only weekly report; no file mutations. Uses the same exclusion rules as foreground. */
public class BackgroundScanService extends JobService {
    private static final int JOB_ID=303001;
    private static final String CHANNEL="weekly_scan";
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Future<?> task;
    public static void schedule(Context c,boolean enable) {
        JobScheduler jobs=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(jobs==null)return;
        if(!enable) {jobs.cancel(JOB_ID);return;}
        JobInfo info=new JobInfo.Builder(JOB_ID,new ComponentName(c,BackgroundScanService.class))
                .setPeriodic(7L*24*60*60*1000L)
                .setRequiresCharging(true)
                .setRequiresBatteryNotLow(true)
                .setPersisted(true)
                .build();
        jobs.schedule(info);
    }
    @Override public boolean onStartJob(JobParameters parameters) {
        if(!Environment.isExternalStorageManager()) return false;
        SharedPreferences p=getSharedPreferences("excluded",0);
        ExcludedPaths exclusions=new ExcludedPaths(p.getBoolean("DCIM",false),
                p.getBoolean("Documents",false),p.getBoolean("Download",false));
        task=worker.submit(()->{
            try {
                SafeScanner.ScanResult scan=SafeScanner.scan(StorageRoots.available(this),
                        new SafeScanner.Progress() {
                            public boolean isCancelled() {return Thread.currentThread().isInterrupted();}
                            public void onUpdate(long n,String path) { }
                            public boolean exclude(File f) {return exclusions.excludes(f);}
                        });
                if(!scan.cancelled) {
                    getSharedPreferences("background_scan",0).edit()
                            .putLong("time",System.currentTimeMillis())
                            .putLong("suggestions",scan.suggestedBytes)
                            .putInt("count",scan.candidates.size()).apply();
                    notifyReport(scan);
                }
            } finally {jobFinished(parameters,false);}
        });
        return true;
    }
    private void notifyReport(SafeScanner.ScanResult scan) {
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                !=PackageManager.PERMISSION_GRANTED) return;
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(nm==null)return;
        NotificationChannel channel=new NotificationChannel(CHANNEL,"Haftalık depolama taraması",
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Sadece tarama raporu; dosya silinmez");
        nm.createNotificationChannel(channel);
        Intent open=new Intent(this,MainActivity.class);
        PendingIntent click=PendingIntent.getActivity(this,JOB_ID,open,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        String detail=scan.candidates.size()+" aday incelenebilir; hiçbir dosya silinmedi.";
        Notification notification=new Notification.Builder(this,CHANNEL)
                .setSmallIcon(R.drawable.ic_clearos_status)
                .setContentTitle("ClearOS Ultra: haftalık tarama tamamlandı")
                .setContentText(detail)
                .setAutoCancel(true)
                .setContentIntent(click)
                .build();
        nm.notify(JOB_ID,notification);
    }
    @Override public boolean onStopJob(JobParameters parameters) {
        if(task!=null)task.cancel(true);
        return true;
    }
    @Override public void onDestroy() {worker.shutdownNow();super.onDestroy();}
}
