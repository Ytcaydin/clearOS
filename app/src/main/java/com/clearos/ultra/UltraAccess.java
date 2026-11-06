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

    /** Harmless probe: proves the Shizuku shell pipe works end-to-end before the real command. */
    private static final String[] PROBE={"/system/bin/id"};
    private static final long PROBE_TIMEOUT_MS=15_000, TRIM_TIMEOUT_MS=300_000;

    /** Outcome of one remote command. Written by helper threads, read after join(). */
    private static final class Run {
        volatile boolean started,timedOut;volatile int code=-1;volatile Throwable error;
        final StringBuffer out=new StringBuffer(),err=new StringBuffer();
        String text(){String o=out.toString().trim(),e=err.toString().trim();
            return (o+(o.isEmpty()||e.isEmpty()?"":"\n")+e).trim();}
    }
    private static Thread drain(java.io.InputStream in,StringBuffer into){
        Thread t=new Thread(()->{
            try(java.io.InputStream s=in){byte[] b=new byte[2048];int n;
                while((n=s.read(b))!=-1) if(into.length()<8192)into.append(new String(b,0,n,java.nio.charset.StandardCharsets.UTF_8));}
            catch(Throwable ignored){}
        },"ultra-drain");t.setDaemon(true);t.start();return t;
    }
    /**
     * Completion = the process exiting (waitFor), not stdout EOF; stdout and stderr are drained
     * on their own threads and stdin is closed at once so nothing can wait on it.
     */
    private static Run run(java.lang.reflect.Method newProcess,String[] command,long timeoutMs){
        Run r=new Run();java.lang.Process p;
        try{p=(java.lang.Process)newProcess.invoke(null,command,null,null);r.started=true;}
        catch(Throwable e){r.error=e instanceof java.lang.reflect.InvocationTargetException&&e.getCause()!=null?e.getCause():e;return r;}
        try{p.getOutputStream().close();}catch(Throwable ignored){}
        Thread out=drain(p.getInputStream(),r.out),err=drain(p.getErrorStream(),r.err);
        Thread waiter=new Thread(()->{try{r.code=p.waitFor();}catch(Throwable e){r.error=e;}},"ultra-wait");
        waiter.setDaemon(true);waiter.start();
        try{waiter.join(timeoutMs);}catch(InterruptedException e){Thread.currentThread().interrupt();}
        if(waiter.isAlive()){r.timedOut=true;try{p.destroy();}catch(Throwable ignored){}}
        try{out.join(1500);err.join(1500);}catch(InterruptedException e){Thread.currentThread().interrupt();}
        return r;
    }
    private static String describe(Throwable e){
        return e.getClass().getSimpleName()+(e.getMessage()==null?"":" — "+e.getMessage());
    }

    /**
     * Primary path: run inside Shizuku's own shell process (Shizuku.newProcess, reached by reflection
     * because it is not public in API 13). A separate user-service process never came up on some
     * devices (Nothing Phone 2a, Shizuku 13.5). If newProcess cannot be used, the user service is tried.
     * Steps and timings go to the on-device action log.
     */
    public void trimCaches(Stage stage,Callback cb) {
        if(!approved()) {cb.done("Shizuku/Porter çalışmıyor veya izni verilmemiş.");return;}
        android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
        java.lang.reflect.Method newProcess;
        try {
            newProcess=Shizuku.class.getDeclaredMethod("newProcess",String[].class,String[].class,String.class);
            newProcess.setAccessible(true);
        } catch(Throwable e) {ActionLog.add(context,"ADB önbellek","newProcess yok, UserService deneniyor: "+describe(e));
            trimViaUserService(stage,cb);return;}
        java.util.concurrent.atomic.AtomicBoolean finished=new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.function.Consumer<String> finish=message->main.post(()->{
            if(finished.compareAndSet(false,true))cb.done(message);
        });
        long t0=android.os.SystemClock.elapsedRealtime();
        if(stage!=null)stage.update("Shizuku kabuğu test ediliyor…");
        // Live elapsed-time ticker while the trim runs.
        Runnable[] tick=new Runnable[1];
        java.util.concurrent.atomic.AtomicBoolean trimming=new java.util.concurrent.atomic.AtomicBoolean(false);
        tick[0]=()->{
            if(finished.get())return;
            if(trimming.get()&&stage!=null){
                long sec=(android.os.SystemClock.elapsedRealtime()-t0)/1000;
                stage.update("Önbellekler daraltılıyor · "+sec+" sn\n(5 dakikaya kadar sürebilir)");
            }
            main.postDelayed(tick[0],1000);
        };
        main.post(tick[0]);
        // Last-resort watchdog in case a helper thread is stuck in a remote call.
        main.postDelayed(()->finish.accept("İşlem yanıt vermedi (zaman aşımı)."),TRIM_TIMEOUT_MS+PROBE_TIMEOUT_MS+30_000);
        new Thread(()->{
            Run probe=run(newProcess,PROBE,PROBE_TIMEOUT_MS);
            long probeMs=android.os.SystemClock.elapsedRealtime()-t0;
            if(!probe.started){
                ActionLog.add(context,"ADB önbellek","newProcess başlatılamadı ("+describe(probe.error)+"), UserService deneniyor");
                main.post(()->{if(finished.compareAndSet(false,true))trimViaUserService(stage,cb);});
                return;
            }
            String probeText=probe.text();
            ActionLog.add(context,"ADB önbellek","id testi "+probeMs+" ms · kod "+probe.code+(probe.timedOut?" · zaman aşımı":"")+" · "+probeText);
            if(probe.timedOut||probe.code!=0){
                finish.accept("Shizuku kabuğu yanıt vermedi.\n\nTest komutu (id): "+(probe.timedOut?"15 sn'de bitmedi":"kod "+probe.code)
                        +(probeText.isEmpty()?"":"\n"+probeText)+"\n\nShizuku uygulamasında servisi durdurup yeniden başlatmayı dene.");
                return;
            }
            trimming.set(true);
            Run trim=run(newProcess,TRIM,TRIM_TIMEOUT_MS);
            long sec=(android.os.SystemClock.elapsedRealtime()-t0)/1000;
            String text=trim.text();
            ActionLog.add(context,"ADB önbellek","trim-caches "+sec+" sn · kod "+trim.code+(trim.timedOut?" · zaman aşımı":"")
                    +(trim.error!=null?" · "+describe(trim.error):"")+(text.isEmpty()?"":" · "+text));
            if(trim.timedOut){
                finish.accept("pm trim-caches 5 dakikada bitmedi (zaman aşımı).\nKabuk testi başarılıydı ("+probeText+").");
            } else if(trim.error!=null){
                finish.accept("ADB işlemi başarısız: "+describe(trim.error));
            } else {
                finish.accept("pm trim-caches çıkış kodu: "+trim.code+"\nSüre: "+sec+" sn"+(text.isEmpty()?"":"\n"+text));
            }
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
