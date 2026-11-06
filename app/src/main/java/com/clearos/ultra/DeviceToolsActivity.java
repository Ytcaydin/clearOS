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
        root.addView(ModernUi.text(this,heading,23,ModernUi.TEXT,true));
        root.addView(ModernUi.muted(this,"ClearOS Ultra 5.0 · Yerel, güvenli ve izinli işlemler",12),ModernUi.space(this,-1,-2,9));
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
    private long systemCacheFreeBefore=-1;

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
            String report=(ok?"Android önbellek daraltmayı tamamladı.\n\n":clean+"\n\n")+freedText(before,freeBytes());
            ActionLog.add(this,"ADB önbellek",report.replace('\n',' '));
            alert(ok?"Tamamlandı":"İşlem sonucu",report);
        });
    }

    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=REQ_SYSTEM_CACHE)return;
        long before=systemCacheFreeBefore;systemCacheFreeBefore=-1;
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
    private void ultra(){
        boolean running=access.running(),approved=access.approved();
        add(ModernUi.section(this,"Shizuku / Porter bağlantısı",approved?"Yetkilendirme hazır":
                running?"Servis çalışıyor · ClearOS izni gerekiyor":"Servis çalışmıyor · Normal mod aktif"),2);
        LinearLayout status=ModernUi.card(this);
        status.addView(ModernUi.text(this,approved?"● Bağlandı":running?"◉ İzin bekleniyor":"○ Bağlı değil",19,
                approved?ModernUi.TEAL:ModernUi.AMBER,true));
        status.addView(ModernUi.muted(this,"Hiçbir zaman root veya gelişigüzel shell komutu kullanılmaz.",12),ModernUi.space(this,-1,-2,8));
        add(status,16);
        add(ModernUi.section(this,"Kurulum adımları","Shizuku uygulaması telefonda bulunmalı"),22);
        add(ModernUi.actionCard(this,"1","Shizuku'yu aç","Uygulamayı telefondan başlat ve servis durumunu kontrol et",()->{
            Intent intent=getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
            if(intent!=null)startActivity(intent);else alert("Shizuku bulunamadı","Önce Shizuku'yu kurmalısın.");
        }),12);
        add(ModernUi.actionCard(this,"2","Kablosuz hata ayıklama","Geliştirici seçeneklerinde aç, eşleştir ve Shizuku servisini başlat",()->{
            try{startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));}
            catch(Exception e){alert("Ayar açılamadı","Geliştirici seçeneklerini elle aç.");}
        }),8);
        add(ModernUi.actionCard(this,"3","ClearOS'a izin ver","Shizuku bağlantısı kurulduktan sonra uygulama iznini iste",()->{
            if(!access.running())alert("Önce Shizuku'yu başlat","Servis henüz çalışmıyor.");
            else {access.request();alert("İzin istendi","Shizuku onay ekranını kontrol et, ardından tekrar bu sayfaya dön.");}
        }),8);
        add(ModernUi.actionCard(this,"4","Bağlantıyı kontrol et","Servis ve uygulama iznini yeniden denetle",this::build),8);
        add(ModernUi.section(this,"Önbellek işlemleri","Android'in izin verdiği kapsamla sınırlıdır"),24);
        add(ModernUi.actionCard(this,"◉","Android sistem ekranı","Kullanıcı onayıyla harici önbellek temizleme",()->{
            if(!Environment.isExternalStorageManager()) {alert("İzin gerekli","Önce Tüm dosyalara erişim izni ver.");return;}
            systemCacheFreeBefore=freeBytes();
            try{startActivityForResult(new Intent(StorageManager.ACTION_CLEAR_APP_CACHE),REQ_SYSTEM_CACHE);}
            catch(Exception e){systemCacheFreeBefore=-1;
                ActionLog.add(this,"Sistem önbellek ekranı","Açılamadı: "+e.getClass().getSimpleName());
                alert("Desteklenmiyor","Telefon bu ekranı sunmuyor.");}
        }),12);
        add(ModernUi.actionCard(this,"⚡","ADB önbelleğini daralt","pm trim-caches 999G; sonuç cihazdan cihaza değişebilir",()->{
            if(!access.approved()){alert("İzin yok","Önce Shizuku servisi ve uygulama izni gerekli.");return;}
            new AlertDialog.Builder(this).setTitle("ADB önbellek temizliği")
                    .setMessage("Android'in desteklediği önbellekleri azaltmayı dener. Bazı sistemler komutu reddedebilir. Devam edilsin mi?")
                    .setNegativeButton("Vazgeç",null).setPositiveButton("Çalıştır",(d,w)->runAdbTrim()).show();
        }),9);
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
        card.addView(ModernUi.text(this,(ok?"✓  ":"!  ")+title,15,
                ok?ModernUi.TEAL:ModernUi.AMBER,true));
        card.addView(ModernUi.muted(this,explanation,12),ModernUi.space(this,-1,-2,7));
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
