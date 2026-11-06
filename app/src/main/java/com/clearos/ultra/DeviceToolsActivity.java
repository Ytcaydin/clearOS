package com.clearos.ultra;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A guided, non-root, read-only device tools suite with explicit user actions. */
public final class DeviceToolsActivity extends Activity {
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private LinearLayout root,content;
    private String section;
    private UltraAccess access;
    private List<AppInsights.App> appSnapshot;
    private int appPage=0;
    @Override public void onCreate(Bundle state) {super.onCreate(state);
        section=getIntent().getStringExtra("section");if(section==null)section="apps";
        access=new UltraAccess(this);build();}
    @Override protected void onResume() {super.onResume();if(root!=null&&section.equals("ultra"))build();}
    @Override protected void onDestroy(){executor.shutdownNow();super.onDestroy();}
    private void build() {
        root=ModernUi.column(this);root.setBackgroundColor(ModernUi.BG);
        root.setPadding(ModernUi.dp(this,18),ModernUi.dp(this,18),ModernUi.dp(this,18),0);
        root.setOnApplyWindowInsetsListener((v,w)->{Insets b=w.getInsets(WindowInsets.Type.systemBars());
            root.setPadding(ModernUi.dp(this,18)+b.left,ModernUi.dp(this,18)+b.top,
                    ModernUi.dp(this,18)+b.right,b.bottom);return w;});setContentView(root);
        String heading=section.equals("ultra")?"Ultra Mod":section.equals("residue")?"Uygulama kalıntıları":
                section.equals("settings")?"Otomasyon & güvenlik":
                section.equals("diagnostics")?"Cihaz tanılaması":"Uygulama analizi";
        root.addView(ModernUi.header(this,heading,"Yerel, güvenli ve izinli işlemler"));
        ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        content=ModernUi.column(this);content.setPadding(0,ModernUi.dp(this,14),0,ModernUi.dp(this,25));scroll.addView(content);
        switch(section){case "ultra":ultra();break;case "residue":residue();break;
            case "settings":settings();break;case "diagnostics":diagnostics();break;default:apps();}
    }
    private void add(View child,int top){content.addView(child,ModernUi.space(this,-1,-2,top));}
    private void note(String msg){add(ModernUi.muted(this,msg,12),9);}
    private void alert(String title,String msg){if(isFinishing()||isDestroyed())return;
        new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("Tamam",null).show();}

    private static final int REQ_SYSTEM_CACHE=611;
    private long systemCacheFreeBefore=-1, systemCacheOpenedAt=0;

    /** Free bytes on the internal data volume (where app caches live). */
    private static long freeBytes(){
        try{return new android.os.StatFs(Environment.getDataDirectory().getPath()).getAvailableBytes();}
        catch(RuntimeException e){return -1;}
    }
    private static String freedText(long before,long after){
        if(before<0||after<0)return "Boş alan ölçülemedi.";
        long diff=after-before;
        String now="Şu an boş: "+ModernUi.bytes(after);
        if(diff>=1024*1024)return "Açılan alan: yaklaşık "+ModernUi.bytes(diff)+"\n"+now;
        return "Ölçülebilir bir alan açılmadı (önbellekler zaten küçük olabilir).\n"+now;
    }

    /** ADB trim with a visible, non-cancelable progress dialog and a before/after free-space report. */
    private void runAdbTrim(){
        long before=freeBytes();
        android.widget.TextView status=ModernUi.muted(this,"Hazırlanıyor…",14);
        int pad=(int)(22*getResources().getDisplayMetrics().density);
        status.setPadding(pad,pad/2,pad,pad/2);
        AlertDialog waiting=new AlertDialog.Builder(this).setTitle("ADB önbellek temizliği")
                .setView(status).setCancelable(false).show();
        access.trimCaches(message->status.setText(message),result->{
            if(waiting.isShowing())try{waiting.dismiss();}catch(RuntimeException ignored){}
            String clean=result==null?"":result.trim();
            boolean ok=clean.startsWith("pm trim-caches çıkış kodu: 0");
            // Only report freed space when the command actually ran; on errors it would mislead.
            String duration=ok&&clean.contains("Süre: ")?clean.substring(clean.indexOf("Süre: ")).split("\n")[0]+"\n":"";
            // Freed space only when the command ran (or may still have run, on timeout).
            String report=ok?"Android önbellek daraltmayı tamamladı.\n"+duration+"\n"+freedText(before,freeBytes())
                    :clean.contains("zaman aşımı")?clean+"\n\n"+freedText(before,freeBytes()):clean;
            ActionLog.add(this,"ADB önbellek",report.replace('\n',' '));
            alert(ok?"Tamamlandı":"İşlem sonucu",report);
        });
    }

    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=REQ_SYSTEM_CACHE)return;
        long before=systemCacheFreeBefore;systemCacheFreeBefore=-1;
        long shownMs=android.os.SystemClock.elapsedRealtime()-systemCacheOpenedAt;
        android.content.SharedPreferences device=getSharedPreferences("device",0);
        // Closed by itself almost instantly = the phone does not provide this screen (e.g. Nothing OS).
        // A person cancelling takes longer; a later success clears the flag again.
        if(result!=RESULT_OK && shownMs<1500) device.edit().putBoolean("system_cache_screen_missing",true).apply();
        if(result==RESULT_OK) device.edit().remove("system_cache_screen_missing").apply();
        String report;
        if(result==RESULT_OK) report="Android harici önbellek temizliğini onayladı.\n\n"+freedText(before,freeBytes());
        else report="Android ekranı iptal edildi ya da bu telefonda gösterilmedi.\n\n"
                +"Bazı telefonlar temizlenecek harici önbellek yoksa ekranı hiç açmadan kapatır. "
                +"Ultra Mod'daki ADB önbellek temizliği daha kapsamlıdır.";
        ActionLog.add(this,"Sistem önbellek ekranı",report.replace('\n',' '));
        alert(result==RESULT_OK?"Tamamlandı":"Temizlik yapılmadı",report);
    }
    private void apps(){
        add(ModernUi.section(this,"Uygulama envanteri","Sistem uygulamaları silinmez; kaldırma sistem ayarlarında yapılır."),2);
        if(!AppInsights.hasUsageAccess(this)){
            add(ModernUi.actionCard(this,"!","Kullanım erişimi ver","Son kullanım ve depolama istatistiklerini görmek için",
                    ()->{try{startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));}catch(Exception e){alert("Ayar açılamadı",e.toString());}}),14);
        }
        add(ModernUi.muted(this,"Uygulamalar yükleniyor…",13),18);
        executor.execute(()->{
            List<AppInsights.App> apps=AppInsights.getApps(this);
            runOnUiThread(()->{if(isDestroyed())return;
                appSnapshot=apps;appPage=0;showAppPage();});
        });
    }
    private void showAppPage(){
        if(appSnapshot==null)return;
        content.removeAllViews();
        int start=appPage*50,end=Math.min(appSnapshot.size(),start+50);
        if(appSnapshot.isEmpty()){
            add(ModernUi.section(this,"Uygulama bulunamadı",
                    "Uygulama görünürlüğü ve kullanım erişimi iznini kontrol et."),0);return;
        }
        add(ModernUi.section(this,appSnapshot.size()+" uygulama",(start+1)+"–"+end+
                " gösteriliyor · Uygulama bilgileri salt okunur"),0);
        for(int i=start;i<end;i++){
            AppInsights.App app=appSnapshot.get(i);
            add(ModernUi.actionCard(this,"▦",app.name,(app.system?"Sistem · ":"")+
                    ModernUi.bytes(app.storage)+" · "+(app.lastUsed<=0?"Son kullanım bilinmiyor":
                    "Son kullanım: "+new java.text.SimpleDateFormat("dd.MM.yy",java.util.Locale.getDefault())
                            .format(new java.util.Date(app.lastUsed))),()->showApp(app)),8);
        }
        LinearLayout nav=ModernUi.row(this);
        Button prev=ModernUi.button(this,"Önceki 50",false),next=ModernUi.button(this,"Sonraki 50",false);
        nav.addView(prev,new LinearLayout.LayoutParams(0,ModernUi.dp(this,47),1));
        LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,ModernUi.dp(this,47),1);
        right.leftMargin=ModernUi.dp(this,8);nav.addView(next,right);
        prev.setEnabled(appPage>0);next.setEnabled(end<appSnapshot.size());
        prev.setOnClickListener(v->{appPage--;showAppPage();});
        next.setOnClickListener(v->{appPage++;showAppPage();});
        add(nav,16);
    }
    private void showApp(AppInsights.App app){
        new AlertDialog.Builder(this).setTitle(app.name)
                .setMessage(app.packageName+"\nDepolama: "+ModernUi.bytes(app.storage)+
                        "\n"+(app.system?"Sistem uygulaması: kaldırılması önerilmez.":"Kaldırmadan önce uygulama verilerini yedekle."))
                .setNegativeButton("Kapat",null)
                .setPositiveButton("Sistem ayarları",(d,w)->{
                    Intent intent=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+app.packageName));
                    try{startActivity(intent);}catch(Exception ignored){alert("Ayar açılamadı",app.packageName);}
                }).show();
    }
    private void residue(){
        add(ModernUi.section(this,"Olası kalıntılar","Android/media içinde kurulu uygulamayla eşleşmeyen klasörler"),2);
        note("Bunlar kesinlikle gereksiz olduğu doğrulanmış dosyalar değildir. Özellikle yedek veya dışa aktarılmış veriler olabilir. Otomatik silme yok.");
        Button scan=ModernUi.button(this,"Kalıntı klasörlerini bul",true);add(scan,18);
        scan.setOnClickListener(v->{scan.setEnabled(false);scan.setText("Klasörler inceleniyor…");
            executor.execute(()->{
                List<AppResidueInspector.Residue> found=AppResidueInspector.inspect(this);
                runOnUiThread(()->{if(isDestroyed())return;
                    content.removeAllViews();add(ModernUi.section(this,found.size()+" olası kalıntı", "Silme öncesi dosyaları mutlaka incele."),2);
                    for(AppResidueInspector.Residue r:found)
                        add(ModernUi.actionCard(this,"◇",r.packageId,r.files+" dosya · "+ModernUi.bytes(r.bytes),
                                ()->alert("Olası kalıntı",r.folder.getAbsolutePath()+
                                        "\n\nBu klasör güvenle silinebilir diye doğrulanmadı. Otomatik işlem yapılmadı.")),8);
                });
            });
        });
    }
    private boolean showSetup=false;
    private void ultra(){
        boolean running=access.running(),approved=access.approved();
        LinearLayout status=ModernUi.card(this);
        LinearLayout head=ModernUi.row(this);
        head.addView(ModernUi.dot(this,approved?ModernUi.TEXT:ModernUi.ACCENT,10));
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-2,1);hp.leftMargin=ModernUi.dp(this,10);
        TextView state=ModernUi.label(this,approved?"Bağlandı":running?"İzin bekleniyor":"Bağlı değil",13,
                approved?ModernUi.TEXT:ModernUi.ACCENT_TEXT);state.setTypeface(ModernUi.monoFont(this,true));
        head.addView(state,hp);
        head.addView(ModernUi.label(this,"Shizuku · ADB",10,ModernUi.MUTED));
        status.addView(head);
        status.addView(ModernUi.muted(this,"Yalnızca önbellek daraltma komutu çalışır. Root veya serbest kabuk komutu kullanılmaz.",13),
                ModernUi.space(this,-1,-2,12));
        LinearLayout facts=ModernUi.row(this);facts.setGravity(android.view.Gravity.TOP);
        fact(facts,"Servis",running?"Çalışıyor":"Kapalı");
        fact(facts,"İzin",approved?"Verildi":"Yok");
        fact(facts,"Kurulum",approved?"Tamam":"Gerekli");
        status.addView(facts,ModernUi.space(this,-1,-2,16));
        add(status,16);
        add(ModernUi.section(this,"Önbellek",null),26);
        boolean systemScreenMissing=getSharedPreferences("device",0).getBoolean("system_cache_screen_missing",false);
        LinearLayout systemCard=ModernUi.actionCard(this,"◉","Android sistem ekranı",
                systemScreenMissing?"Bu telefonda desteklenmiyor · ADB temizliğini kullan":"Harici önbellek · senin onayınla",()->{
            if(!Environment.isExternalStorageManager()) {alert("İzin gerekli","Önce Tüm dosyalara erişim izni ver.");return;}
            systemCacheFreeBefore=freeBytes();systemCacheOpenedAt=android.os.SystemClock.elapsedRealtime();
            try{startActivityForResult(new Intent(StorageManager.ACTION_CLEAR_APP_CACHE),REQ_SYSTEM_CACHE);}
            catch(Exception e){systemCacheFreeBefore=-1;
                ActionLog.add(this,"Sistem önbellek ekranı","Açılamadı: "+e.getClass().getSimpleName());
                alert("Desteklenmiyor","Telefon bu ekranı sunmuyor.");}
        });
        if(systemScreenMissing)systemCard.setAlpha(.45f);
        add(systemCard,0);
        add(ModernUi.actionCard(this,"⚡","ADB önbelleğini daralt","Tüm uygulamaların önbelleği · ~1 dk",()->{
            if(!access.approved()){alert("İzin yok","Önce Shizuku servisi ve uygulama izni gerekli.");return;}
            new AlertDialog.Builder(this).setTitle("ADB önbellek temizliği")
                    .setMessage("Android'in desteklediği önbellekleri azaltmayı dener. Bazı sistemler komutu reddedebilir. Devam edilsin mi?")
                    .setNegativeButton("Vazgeç",null).setPositiveButton("Çalıştır",(d,w)->runAdbTrim()).show();
        }),0);
        if(approved && !showSetup){
            android.widget.Button more=ModernUi.button(this,"Kurulum adımlarını göster",false);add(more,28);
            more.setOnClickListener(v->{showSetup=true;build();});
            return;
        }
        add(ModernUi.section(this,"Kurulum adımları","Shizuku uygulaması telefonda bulunmalı"),28);
        add(ModernUi.actionCard(this,"1","Shizuku'yu aç","Uygulamayı telefondan başlat ve servis durumunu kontrol et",()->{
            Intent intent=getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
            if(intent!=null)startActivity(intent);else alert("Shizuku bulunamadı","Önce Shizuku'yu kurmalısın.");
        }),6);
        add(ModernUi.actionCard(this,"2","Kablosuz hata ayıklama","Geliştirici seçeneklerinde aç, eşleştir ve Shizuku servisini başlat",()->{
            try{startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));}
            catch(Exception e){alert("Ayar açılamadı","Geliştirici seçeneklerini elle aç.");}
        }),0);
        add(ModernUi.actionCard(this,"3","ClearOS'a izin ver","Shizuku bağlantısı kurulduktan sonra uygulama iznini iste",()->{
            if(!access.running())alert("Önce Shizuku'yu başlat","Servis henüz çalışmıyor.");
            else {access.request();alert("İzin istendi","Shizuku onay ekranını kontrol et, ardından tekrar bu sayfaya dön.");}
        }),0);
        add(ModernUi.actionCard(this,"4","Bağlantıyı kontrol et","Servis ve uygulama iznini yeniden denetle",this::build),0);
    }
    private void fact(LinearLayout row,String key,String value){
        LinearLayout cell=ModernUi.column(this);
        android.graphics.drawable.GradientDrawable top=new android.graphics.drawable.GradientDrawable();
        top.setColor(0);top.setStroke(ModernUi.dp(this,1),ModernUi.BORDER);int o=-ModernUi.dp(this,2);
        cell.setBackground(new android.graphics.drawable.InsetDrawable(top,o,0,o,o));
        cell.setPadding(0,ModernUi.dp(this,10),ModernUi.dp(this,6),0);
        cell.addView(ModernUi.label(this,key,9,ModernUi.MUTED));
        cell.addView(ModernUi.text(this,value,13,ModernUi.TEXT,false),ModernUi.space(this,-1,-2,5));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);
        if(row.getChildCount()>0)p.leftMargin=ModernUi.dp(this,8);
        row.addView(cell,p);
    }
    /** Read-only on-device checks: no shell commands, no file changes and no telemetry. */
    private void diagnostics(){
        add(ModernUi.section(this,"Cihaz kontrol listesi","Çalışma izni ve temizlik ortamı · Salt okunur"),2);
        boolean storage=Environment.isExternalStorageManager();
        diagnosticCard("Paylaşılan dosya erişimi",storage,"Tüm dosyalara erişim izni");
        diagnosticCard("Uygulama kullanım izni",AppInsights.hasUsageAccess(this),
                "Son kullanılan uygulamalar ve boyut istatistikleri");
        boolean shizuku=access.running();
        diagnosticCard("Shizuku/Porter servisi",shizuku,
                "İsteğe bağlı ADB tabanlı temizlik");
        diagnosticCard("Shizuku yetkisi",shizuku&&access.approved(),
                "ClearOS uygulama yetkilendirmesi");
        List<File> roots=StorageRoots.available(this);
        diagnosticCard("Depolama birimleri",!roots.isEmpty(),roots.size()+" birim görülebiliyor");
        add(ModernUi.actionCard(this,"♻","Karantina envanteri",
                "Dosyalara dokunmadan karantina kayıtlarını incele",()->{
                    executor.execute(()->{
                        QuarantineManager manager=new QuarantineManager(StorageRoots.available(this));
                        int count=manager.list().size();long bytes=manager.containedBytes();
                        runOnUiThread(()->{if(!isDestroyed())alert("Karantina kaydı",
                            count+" dosya · "+ModernUi.bytes(bytes)+
                            "\nBu işlem hiçbir dosyayı silmedi.");});
                    });
                }),16);
        add(ModernUi.actionCard(this,"≡","İşlem günlüğü",
                "Başarısız işlemlerin yerel raporunu aç",()->
                startActivity(new Intent(this,ActionLogActivity.class))),9);
        note("Tanılama sadece erişim ve servis durumlarını okur. Başarı gösterilmesi, tüm Android API işlemlerinin test edildiği anlamına gelmez.");
    }
    private void diagnosticCard(String title,boolean ok,String explanation){
        LinearLayout card=ModernUi.card(this);
        LinearLayout head=ModernUi.row(this);
        head.addView(ModernUi.text(this,title,15,ModernUi.TEXT,true),new LinearLayout.LayoutParams(0,-2,1));
        head.addView(ModernUi.dot(this,ok?ModernUi.TEXT:ModernUi.ACCENT,8));
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-2,-2);sp.leftMargin=ModernUi.dp(this,8);
        head.addView(ModernUi.label(this,ok?"Hazır":"Gerekli",10,ok?ModernUi.TEXT:ModernUi.ACCENT_TEXT),sp);
        card.addView(head);
        card.addView(ModernUi.muted(this,explanation,12),ModernUi.space(this,-1,-2,8));
        add(card,8);
    }
    private void settings(){
        add(ModernUi.section(this,"Haftalık kontrol","Yalnızca şarjdayken çalışır · otomatik silme yok"),2);
        CheckBox weekly=new CheckBox(this);weekly.setText("Haftada bir tarama ve bildirim");weekly.setTextColor(ModernUi.TEXT);
        weekly.setChecked(getSharedPreferences("v4_settings",0).getBoolean("weekly",false));
        add(weekly,18);weekly.setOnCheckedChangeListener((b,enabled)->{
            BackgroundScanService.schedule(this,enabled);
            getSharedPreferences("v4_settings",0).edit().putBoolean("weekly",enabled).apply();
            if(enabled&&Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    !=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},420);}
        });
        add(ModernUi.section(this,"Hariç tutulacak klasörler","Tercihler hem elle hem otomatik taramada kullanılır"),24);
        for(String key:new String[]{"DCIM","Documents","Download"}){
            CheckBox check=new CheckBox(this);check.setText(key+" klasörünü tarama");check.setTextColor(ModernUi.TEXT);
            check.setChecked(getSharedPreferences("excluded",0).getBoolean(key,false));add(check,9);
            check.setOnCheckedChangeListener((button,enabled)->getSharedPreferences("excluded",0).edit().putBoolean(key,enabled).apply());
        }
        add(ModernUi.actionCard(this,"✓","Cihaz tanılaması",
                "Dosya izinlerini, Shizuku yetkisini ve karantina durumunu kontrol et",()->
                startActivity(new Intent(this,DeviceToolsActivity.class).putExtra("section","diagnostics"))),18);
        add(ModernUi.actionCard(this,"◷","Son haftalık tarama","Önceki taramanın özetini göster",()->{
            long time=getSharedPreferences("background_scan",0).getLong("time",0);
            long bytes=getSharedPreferences("background_scan",0).getLong("suggestions",0);
            alert("Haftalık rapor",time==0?"Henüz zamanlanmış tarama yok.":
                    new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm",java.util.Locale.getDefault()).format(new java.util.Date(time))
                            +"\nÖnerilen: "+ModernUi.bytes(bytes));
        }),19);
        note("Uygulama kaldırılırsa paylaşılan depolamadaki .ClearOS_Quarantine klasörü kalabilir. Kalıcı silme yalnızca ayrı onayla yapılır.");
    }
}
