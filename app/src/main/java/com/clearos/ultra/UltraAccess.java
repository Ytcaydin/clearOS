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

    /** The one fixed command ClearOS ever runs with shell identity. No user input reaches it. */
    private static final String[] TRIM={"/system/bin/pm","trim-caches","999G","internal"};

    /**
     * Primary path: run TRIM inside Shizuku's own shell process (Shizuku.newProcess). It needs no
     * separate user-service process, which on some devices never comes up. newProcess is not public
     * in API 13, so it is reached by reflection; on any failure the user-service path is used.
     */
    public void trimCaches(Stage stage,Callback cb) {
        if(!approved()) {cb.done("Shizuku/Porter çalışmıyor veya izni verilmemiş.");return;}
        android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
        java.lang.reflect.Method newProcess;
        try {
            newProcess=Shizuku.class.getDeclaredMethod("newProcess",String[].class,String[].class,String.class);
            newProcess.setAccessible(true);
        } catch(Throwable e) {trimViaUserService(stage,cb);return;}
        if(stage!=null)stage.update("Önbellekler daraltılıyor… (1 dakikaya kadar sürebilir)");
        java.util.concurrent.atomic.AtomicBoolean finished=new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicReference<java.lang.Process> running=new java.util.concurrent.atomic.AtomicReference<>();
        java.util.function.Consumer<String> finish=message->main.post(()->{
            if(finished.compareAndSet(false,true))cb.done(message);
        });
        main.postDelayed(()->{
            if(finished.get())return;
            java.lang.Process p=running.get();
            if(p!=null)try{p.destroy();}catch(Throwable ignored){}
            finish.accept("İşlem 2 dakikada tamamlanmadı (zaman aşımı).");
        },TOTAL_TIMEOUT_MS);
        new Thread(()->{
            java.lang.Process p;
            try {p=(java.lang.Process)newProcess.invoke(null,TRIM,null,null);}
            catch(Throwable e) {
                // Reflection or remote start failed: fall back to the user service on the main thread.
                main.post(()->{if(finished.compareAndSet(false,true))trimViaUserService(stage,cb);});
                return;
            }
            running.set(p);
            String output;int code;
            try (java.io.InputStream in=p.getInputStream()) {
                java.io.ByteArrayOutputStream buf=new java.io.ByteArrayOutputStream();
                byte[] block=new byte[2048];int n;
                while((n=in.read(block))!=-1) if(buf.size()<8192)buf.write(block,0,n);
                code=p.waitFor();
                output=buf.toString("UTF-8");
            } catch(Throwable e) {
                finish.accept("ADB işlemi başarısız: "+e.getClass().getSimpleName()+(e.getMessage()==null?"":" — "+e.getMessage()));
                return;
            } finally {try{p.destroy();}catch(Throwable ignored){}}
            finish.accept("pm trim-caches çıkış kodu: "+code+"\n"+output);
        },"ultra-cache-cleaner").start();
    }

    /**
     * Fallback: bind UltraUserService in a Shizuku-spawned process. Always calls cb exactly once on
     * the main thread: with the result, an error, or a timeout.
     */
    private void trimViaUserService(Stage stage,Callback cb) {
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
