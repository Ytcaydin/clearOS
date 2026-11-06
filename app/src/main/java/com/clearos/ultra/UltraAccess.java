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
    /** Optional stage updates for the UI ("Servis başlatılıyor…", "Önbellekler daraltılıyor…"). */
    public interface Stage {void update(String message);}

    // Generous: binding a cold user-service process + pm walking every package can take ~1 min.
    private static final long CONNECT_TIMEOUT_MS=20_000, TOTAL_TIMEOUT_MS=120_000;

    public void trimCaches(Callback cb) {trimCaches(null,cb);}

    /**
     * Always calls cb exactly once on the main thread: with the result, an error, or a timeout.
     * Previously a user-service that never connected left the UI silent forever.
     */
    public void trimCaches(Stage stage,Callback cb) {
        if(!approved()) {cb.done("Shizuku/Porter çalışmıyor veya izni verilmemiş.");return;}
        android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
        java.util.concurrent.atomic.AtomicBoolean finished=new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicBoolean connected=new java.util.concurrent.atomic.AtomicBoolean(false);
        Shizuku.UserServiceArgs args=new Shizuku.UserServiceArgs(
                new ComponentName(context,UltraUserService.class))
                .daemon(false).processNameSuffix("ultra").version(4);
        java.util.function.Consumer<String> finish=message->main.post(()->{
            if(!finished.compareAndSet(false,true))return;
            cb.done(message);
            try {Shizuku.unbindUserService(args,connection,true);}catch(Throwable ignored){}
            service=null;
        });
        connection=new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name,IBinder binder) {
                if(binder==null||!binder.pingBinder()){finish.accept("Shizuku servisi bağlandı ama yanıt vermiyor. Shizuku'yu yeniden başlatıp tekrar dene.");return;}
                connected.set(true);
                service=IUltraService.Stub.asInterface(binder);
                if(stage!=null)main.post(()->stage.update("Önbellekler daraltılıyor… (1 dakikaya kadar sürebilir)"));
                IUltraService bound=service;
                new Thread(()->{
                    String result;
                    try {result=bound.trimCaches();}
                    catch(RemoteException|RuntimeException e){result="ADB işlemi başarısız: "+e.getMessage();}
                    finish.accept(result);
                },"ultra-cache-cleaner").start();
            }
            @Override public void onServiceDisconnected(ComponentName name) {
                service=null;
                finish.accept("Shizuku servisi işlem sırasında kapandı.");
            }
        };
        if(stage!=null)stage.update("Shizuku servisi başlatılıyor…");
        main.postDelayed(()->{if(!connected.get())finish.accept(
                "Shizuku servisi 20 saniyede bağlanamadı. Shizuku uygulamasında servisin çalıştığını kontrol edip tekrar dene.");},
                CONNECT_TIMEOUT_MS);
        main.postDelayed(()->finish.accept("İşlem 2 dakikada tamamlanmadı (zaman aşımı)."),TOTAL_TIMEOUT_MS);
        try {Shizuku.bindUserService(args,connection);}
        catch(Throwable e) {finish.accept("Servis başlatılamadı: "+e.getClass().getSimpleName()+
                (e.getMessage()==null?"":" — "+e.getMessage()));}
    }
}
