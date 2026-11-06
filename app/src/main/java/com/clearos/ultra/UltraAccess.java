package com.clearos.ultra;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;
import rikka.shizuku.Shizuku;

/** Genuine user-authorized Shizuku/Porter-compatible ADB feature.
 * Not a claim to access another app's private data. */
public final class UltraAccess {
    public interface Callback {void done(String message);}
    private final Context context;
    private IUltraService service;
    private ServiceConnection connection;
    private static final int REQUEST=44771;
    public UltraAccess(Context c) {context=c;}
    public boolean running() {
        try {return Shizuku.pingBinder();}catch(Throwable ignored){return false;}
    }
    public boolean approved() {
        try {return running() && Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED;}
        catch(Throwable ignored){return false;}
    }
    public void request() {
        try {if(running()&&!approved())Shizuku.requestPermission(REQUEST);}catch(Throwable ignored) { }
    }
    public void trimCaches(Callback cb) {
        if(!approved()) {cb.done("Shizuku/Porter çalışmıyor veya izni verilmemiş.");return;}
        Shizuku.UserServiceArgs args=new Shizuku.UserServiceArgs(
                new ComponentName(context,UltraUserService.class))
                .daemon(false).processNameSuffix("ultra").version(3);
        connection=new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name,IBinder binder) {
                service=IUltraService.Stub.asInterface(binder);
                new Thread(()->{
                    String result;
                    try {result=service.trimCaches();}
                    catch(RemoteException|RuntimeException e){result="ADB işlemi başarısız: "+e.getMessage();}
                    String done=result;
                    android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
                    main.post(()->{
                        cb.done(done);
                        try {Shizuku.unbindUserService(args,connection,true);}catch(Throwable ignored){}
                        service=null;
                    });
                },"ultra-cache-cleaner").start();
            }
            @Override public void onServiceDisconnected(ComponentName name) {service=null;}
        };
        try {Shizuku.bindUserService(args,connection);}
        catch(Throwable e) {cb.done("Servis başlatılamadı: "+e.getClass().getSimpleName());}
    }
}
