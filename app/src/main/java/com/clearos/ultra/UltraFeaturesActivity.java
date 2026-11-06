package com.clearos.ultra;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.graphics.Insets;
import android.view.WindowInsets;
import android.Manifest;
import android.os.Build;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** ClearOS Ultra 3.1 tools: explicit, private and review-first. */
public final class UltraFeaturesActivity extends Activity {
    private static final int BG=0xFF09111D,CARD=0xFF152436,FG=0xFFEAF5FF,
            MUTED=0xFFA9B8C8,ACCENT=0xFF36D9BC;
    private static final int PICK_PHOTO=201,PICK_VIDEO=202;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean stop=new AtomicBoolean(false);
    private TextView status, shizukuStatus;
    private LinearLayout layout;
    private UltraAccess access;
    private boolean busy=false;
    private int photoJob=0;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        access=new UltraAccess(this);
        build();
    }
    @Override protected void onResume() {super.onResume();if(shizukuStatus!=null)refreshShizuku();}
    @Override protected void onDestroy() {stop.set(true);worker.shutdownNow();super.onDestroy();}
    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
    private GradientDrawable bg(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(15));return d;}
    private TextView label(String s,int size,int color,boolean bold) {
        TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(size);
        t.setTypeface(Typeface.DEFAULT,bold?Typeface.BOLD:Typeface.NORMAL);return t;
    }
    private void addTitle(String t,String sub) {
        TextView heading=label(t,18,FG,true);layout.addView(heading,pad(17));
        TextView desc=label(sub,12,MUTED,false);layout.addView(desc,pad(5));
    }
    private LinearLayout.LayoutParams pad(int top){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(top);return lp;}
    private void control(String title,String sub,Runnable action) {
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14),dp(12),dp(14),dp(12));card.setBackground(bg(CARD));
        card.addView(label(title,14,FG,true));card.addView(label(sub,11,MUTED,false),pad(4));
        layout.addView(card,pad(9));card.setOnClickListener(v->action.run());
    }
    private void build() {
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(17),dp(20),dp(17),dp(30));scroll.addView(layout);
        scroll.setClipToPadding(false);
        scroll.setOnApplyWindowInsetsListener((view,insets)->{
            Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
            scroll.setPadding(bars.left,bars.top,bars.right,bars.bottom);
            return insets;
        });
        setContentView(scroll);
        TextView title=label("CLEAROS ULTRA 3.1",24,ACCENT,true);layout.addView(title);
        status=label("Bütün işlemler cihazında yapılır; otomatik dosya silinmez.",12,MUTED,false);
        layout.addView(status,pad(7));

        addTitle("Kurtarma & analiz","Önce güvenlik, sonra depolama tasarrufu");
        control("♻  Karantina ve geri yükleme","Geri yükle veya tek tek kalıcı olarak sil. Karantina yer açmaz.",this::showTrash);
        control("▥  Depolama haritası","Son taramanın en çok yer kaplayan klasörleri ve geçmişi",this::showStorageMap);
        control("◉  Benzer fotoğrafları tara","Benzerlik + olası bulanıklar, 6.000 fotoğraf sınırı; otomatik silme yok",this::scanPhotos);
        control("▣  Uygulama yöneticisi","Kullanılmayan uygulamalar, disk boyutu ve sistem ayarlarına geçiş",this::showApps);
        control("◇  Olası uygulama kalıntıları","Android/media içindeki kurulu uygulamayla eşleşmeyen klasörleri göster",this::showResidues);
        control("◎  WhatsApp / Telegram medya","Erişilebilen mesajlaşma medyasını tür ve boyuta göre incele",this::showMessenger);

        addTitle("Medya optimizasyonu","Orijinalleri koruyarak daha küçük kopya üret");
        control("▧  Fotoğrafı sıkıştır","JPEG kalite 82, en çok 2560 px; yalnızca daha küçük çıktı saklanır",
                ()->choose("image/*",PICK_PHOTO));
        control("▶  Videoyu sıkıştır","Media3 ile 720p H.264; cihaz desteği gerekir, orijinal korunur",
                ()->choose("video/*",PICK_VIDEO));

        addTitle("Ultra Mod (isteğe bağlı)","Shizuku veya Porter Compatibility ile ADB yetkisi");
        shizukuStatus=label("Yetki durumu denetleniyor",12,MUTED,false);layout.addView(shizukuStatus,pad(7));
        control("🔑  Ultra Mod izni iste","Shizuku/Porter servisini başlatmış olman gerekir",()->{
            if(!access.running())notice("Porter/Shizuku çalışmıyor. Önce uygulamayı açıp kablosuz hata ayıklama ile başlat.");
            else if(access.approved())notice("Ultra Mod zaten yetkili.");
            else {access.request();notice("İzin isteği açıldı. Onaydan sonra tekrar dene.");}
        });
        control("⚙  ADB önbellek temizliği","Yalnızca açık onayla pm trim-caches çalıştırır; sonuç cihazdan cihaza değişir",
                ()->new AlertDialog.Builder(this).setTitle("ADB önbellek işlemi")
                        .setMessage("Sistem geçici önbelleklerinin temizlenmesi bazı uygulamaları yavaşlatabilir."
                                +" Bu işlem uygulama verilerini sıfırlamak için tasarlanmadı. Devam?")
                        .setNegativeButton("Vazgeç",null)
                        .setPositiveButton("Çalıştır",(d,w)->{
                            status.setText("ADB işlemi çalışıyor…");access.trimCaches(message->{status.setText("ADB işlemi tamamlandı");notice(message);});
                        }).show());

        addTitle("Otomasyon & tercihler","Pil dostu; tehlikeli işlemler hiçbir zaman kendiliğinden çalışmaz");
        CheckBox weekly=new CheckBox(this);weekly.setText("Haftada bir, yalnızca şarjdayken tara");
        weekly.setTextColor(FG);weekly.setChecked(getPreferences(0).getBoolean("weekly",false));
        layout.addView(weekly,pad(7));weekly.setOnCheckedChangeListener((v,enabled)->{
            BackgroundScanService.schedule(this,enabled);
            getPreferences(0).edit().putBoolean("weekly",enabled).apply();
            if (enabled && Build.VERSION.SDK_INT>=33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},501);
                status.setText("Tarama açık. Bildirim izni verilmezse sonuçlar yalnızca uygulama içinde görünür.");
            } else status.setText(enabled?"Haftalık tarama ve bildirim açık: otomatik silme yok":"Haftalık tarama kapatıldı");
        });
        addExclude("DCIM", "Kamera ve diğer DCIM klasörlerini taramadan çıkar");
        addExclude("Documents", "Belgeleri taramadan çıkar");
        addExclude("Download", "İndirilenleri taramadan çıkar");
        control("Tarama sonuçlarının özeti","Son otomatik tarama / kullanılabilir seçenekler",()->{
            long t=getSharedPreferences("background_scan",0).getLong("time",0);
            long bytes=getSharedPreferences("background_scan",0).getLong("suggestions",0);
            notice(t==0?"Henüz haftalık tarama yapılmadı.":
                    "Son tarama: "+DateFormat.getDateTimeInstance().format(t)+"\nÖneriler: "+size(bytes));
        });
        refreshShizuku();
    }
    private void addExclude(String key,String caption) {
        CheckBox check=new CheckBox(this);check.setText(caption);check.setTextColor(MUTED);
        check.setTextSize(12);check.setChecked(getSharedPreferences("excluded",0).getBoolean(key,false));
        layout.addView(check,pad(3));check.setOnCheckedChangeListener((button,enabled)->
                getSharedPreferences("excluded",0).edit().putBoolean(key,enabled).apply());
    }
    private void refreshShizuku() {
        shizukuStatus.setText(access.approved()?"✓ Ultra Mod yetkisi hazır":
                access.running()?"Servis çalışıyor • uygulama izni bekleniyor":"Servis çalışmıyor • normal mod etkin");
    }
    private void choose(String mime,int request) {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType(mime);intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {startActivityForResult(intent,request);}catch(Exception e){notice("Dosya seçici açılamadı");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        if(request==PICK_PHOTO) {
            new AlertDialog.Builder(this).setTitle("Fotoğraf kopyası oluşturulsun mu?")
                    .setMessage("Orijinal silinmez. JPEG kalite dönüşümü bazı meta verileri korumayabilir. Sadece daha küçük dosya kaydedilir.")
                    .setNegativeButton("Vazgeç",null).setPositiveButton("Sıkıştır",(d,w)->{
                        status.setText("Fotoğraf sıkıştırılıyor…");worker.execute(()->{
                            String msg;try {msg=MediaCompressor.compressPhoto(this,uri);}catch(Exception e){msg=e.getMessage();}
                            String finalMsg=msg;runOnUiThread(()->{status.setText("Fotoğraf işlemi tamamlandı");notice(finalMsg);});
                        });
                    }).show();
        } else if(request==PICK_VIDEO) {
            new AlertDialog.Builder(this).setTitle("720p video sıkıştırma")
                    .setMessage("Video 720p H.264 ile yeniden kodlanır. Orijinal silinmez."
                            +" Büyük dosyalar uzun sürebilir, işlem sırasında uygulamayı açık bırak.")
                    .setNegativeButton("Vazgeç",null).setPositiveButton("Başlat",(d,w)->{
                        status.setText("Video dönüştürülüyor…");
                        MediaCompressor.compressVideo(this,uri,new MediaCompressor.Listener(){
                            public void success(String msg){status.setText("Video hazır");notice(msg);}
                            public void error(String msg){status.setText("Video dönüştürülemedi");notice(msg);}
                        });
                    }).show();
        }
    }
    private QuarantineManager vault() {
        return new QuarantineManager(StorageRoots.available(this));
    }
    private void showTrash() {
        List<TrashStore.Entry> entries=vault().list();
        if(entries.isEmpty()){notice("Karantina boş. Geri alınabilir temizlikten sonra burada listelenir.");return;}
        String[] names=new String[Math.min(entries.size(),100)];
        for(int i=0;i<names.length;i++){TrashStore.Entry e=entries.get(i);names[i]=e.original.getName()+" • "+size(e.size);}
        new AlertDialog.Builder(this).setTitle("Karantina • "+size(vault().containedBytes())+" tutuluyor")
                .setItems(names,(d,i)->trashActions(entries.get(i)))
                .setNeutralButton("Tümünü kalıcı sil",(d,w)->new AlertDialog.Builder(this)
                        .setTitle("TÜM KARANTİNAYI BOŞALT")
                        .setMessage(entries.size()+" öğe kalıcı olarak silinecek. Geri alınamaz. Karantinadaki önemli dosyaları geri yüklediğinden emin misin?")
                        .setNegativeButton("İptal",null)
                        .setPositiveButton("Evet, hepsini sil",(x,y)->worker.execute(()->{
                            QuarantineManager trash=vault();
                            int success=0;for(TrashStore.Entry e:entries)if(trash.purge(e))success++;
                            int count=success;runOnUiThread(()->notice(count+" öğe kalıcı silindi."));
                        })).show())
                .setPositiveButton("Kapat",null).show();
    }
    private void trashActions(TrashStore.Entry entry) {
        new AlertDialog.Builder(this).setTitle(entry.original.getName())
                .setMessage("Eski konum: "+entry.original+"\nBoyut: "+size(entry.size)+
                        "\n\nKarantina gerçek depolama alanını boşaltmaz. Gizli .ClearOS_Quarantine klasöründe tutulur ve uygulama kaldırılınca otomatik silinmeyebilir.")
                .setNeutralButton("Geri yükle",(d,w)->worker.execute(()->{
                    boolean ok=vault().restore(entry);
                    runOnUiThread(()->notice(ok?"Dosya eski konumuna geri yüklendi.":
                            "Geri yüklenemedi. Eski dizin mevcut mu, dosya adı başka bir öğe tarafından kullanılıyor mu kontrol et."));
                }))
                .setNegativeButton("Vazgeç",null)
                .setPositiveButton("Kalıcı sil",(d,w)->new AlertDialog.Builder(this)
                        .setTitle("GERİ ALINAMAZ")
                        .setMessage("\""+entry.original.getName()+"\" kalıcı olarak silinecek. Emin misin?")
                        .setNegativeButton("Vazgeç",null)
                        .setPositiveButton("Evet, kalıcı sil",(x,y)->worker.execute(()->{
                            boolean ok=vault().purge(entry);
                            runOnUiThread(()->notice(ok?"Karantinadaki dosya kalıcı silindi.":"Silme başarısız; dosya korunmuş olabilir."));
                        })).show()).show();
    }
    private void showStorageMap() {
        String json=getSharedPreferences("folder_map",0).getString("json","[]");
        org.json.JSONArray data;
        try {data=new org.json.JSONArray(json);}catch(Exception e){notice("Depolama haritası açılamadı");return;}
        if(data.length()==0){notice("Önce ana ekranda tarama yap.");return;}
        ScrollView scroll=new ScrollView(this);
        LinearLayout rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(dp(18),dp(7),dp(18),dp(12));scroll.addView(rows);
        long largest=data.optJSONObject(0).optLong("bytes",1);
        for(int i=0;i<Math.min(40,data.length());i++) {
            org.json.JSONObject item=data.optJSONObject(i);if(item==null)continue;
            String name=item.optString("path","");long bytes=item.optLong("bytes",0);
            rows.addView(label(name+"  •  "+size(bytes),12,FG,false),pad(i==0?6:14));
            ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
            bar.setMax(1000);bar.setProgress(largest>0?(int)(1000.0*bytes/largest):0);
            bar.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
            rows.addView(bar,new LinearLayout.LayoutParams(-1,dp(8)));
        }
        List<StorageHistory.Reading> history=StorageHistory.list(this);
        if(history.size()>=2) {
            long delta=history.get(history.size()-1).used-history.get(0).used;
            rows.addView(label("\nDepolama değişimi: "+(delta>=0?"+":"-")+size(Math.abs(delta)),13,MUTED,true));
        }
        new AlertDialog.Builder(this).setTitle("Depolama haritası (son tarama)")
                .setView(scroll).setPositiveButton("Kapat",null).show();
    }
    private void scanPhotos() {
        if(!Environment.isExternalStorageManager()){notice("Önce dosya erişimi izni ver.");return;}
        if(busy)return;
        busy=true;stop.set(false);int id=++photoJob;status.setText("Görsel analizi devam ediyor (en çok 6.000 fotoğraf)…");
        android.content.SharedPreferences p=getSharedPreferences("excluded",0);
        ExcludedPaths exclusions=new ExcludedPaths(p.getBoolean("DCIM",false),
                p.getBoolean("Documents",false),p.getBoolean("Download",false));
        worker.execute(()->{
            ImageInspector.Results found=ImageInspector.inspect(StorageRoots.available(this),new SafeScanner.Progress(){
                public boolean isCancelled(){return stop.get()||Thread.currentThread().isInterrupted();}
                public boolean exclude(File f){return exclusions.excludes(f);}
                public void onUpdate(long count,String path) {
                    runOnUiThread(()->{if(photoJob==id)status.setText(count+" fotoğraf analiz edildi…");});
                }
            });
            runOnUiThread(()->{
                busy=false;
                status.setText(found.inspected+" fotoğraf incelendi, "+found.similarGroups+" benzer grup, "+found.blurryCandidates+" olası bulanık.");
                if(found.cancelled)return;
                if(found.candidates.isEmpty()){notice("Benzer fotoğraf bulunamadı. Kapsam: "+found.inspected+" adet");return;}
                showSimilar(found.candidates);
            });
        });
    }
    private void showSimilar(List<SafeScanner.Candidate> candidates) {
        int count=Math.min(candidates.size(),80);String[] rows=new String[count];
        for(int i=0;i<count;i++){
            SafeScanner.Candidate c=candidates.get(i);rows[i]=c.file.getName()+" • "+size(c.size);
        }
        new AlertDialog.Builder(this).setTitle("Benzer fotoğraflar • "+candidates.size())
                .setMessage("Benzerlik hatalı olabilir. Karşılaştırmadan silme yapma; otomatik seçim yok.")
                .setItems(rows,(d,i)->preview(candidates.get(i))).setPositiveButton("Kapat",null).show();
    }
    private void preview(SafeScanner.Candidate c) {
        ImageView image=new ImageView(this);image.setAdjustViewBounds(true);
        image.setMaxHeight(dp(220));image.setPadding(dp(12),dp(8),dp(12),dp(8));
        BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=8;
        try {image.setImageBitmap(BitmapFactory.decodeFile(c.file.getAbsolutePath(),options));}
        catch(Exception ignored) {}
        LinearLayout comparison=new LinearLayout(this);comparison.setOrientation(LinearLayout.VERTICAL);
        comparison.addView(label("İncelenen dosya",12,MUTED,true));comparison.addView(image);
        if(c.type==SafeScanner.Type.SIMILAR && c.duplicateKeep!=null && c.duplicateKeep.isFile()) {
            comparison.addView(label("Korunması önerilen görüntü",12,MUTED,true));
            ImageView original=new ImageView(this);original.setMaxHeight(dp(220));original.setAdjustViewBounds(true);
            BitmapFactory.Options secondOptions=new BitmapFactory.Options();secondOptions.inSampleSize=8;
            original.setImageBitmap(BitmapFactory.decodeFile(c.duplicateKeep.getAbsolutePath(),secondOptions));
            comparison.addView(original);
        }
        new AlertDialog.Builder(this).setTitle(c.file.getName()).setView(comparison)
                .setMessage(c.reason+"\n\n"+c.file.getAbsolutePath())
                .setNegativeButton("Korunacak",null)
                .setPositiveButton("Karantinaya taşı",(d,w)->new AlertDialog.Builder(this)
                        .setMessage("Fotoğrafın benzer göründüğü düşünüldü ancak aynı olmayabilir. Karantinaya taşımak istiyor musun?")
                        .setNegativeButton("Vazgeç",null)
                        .setPositiveButton("Taşı",(d2,w2)->worker.execute(()->{
                            String outcome;
                            try {vault().move(c);outcome="Karantinaya taşındı. Geri yüklenebilir.";}
                            catch(Exception e){outcome="Taşınamadı: "+e.getMessage();}
                            String msg=outcome;runOnUiThread(()->notice(msg));
                        })).show()).show();
    }
    private void showMessenger() {
        if(!Environment.isExternalStorageManager()){notice("Önce tüm dosyalara erişim iznini ver.");return;}
        status.setText("Mesajlaşma dosyaları inceleniyor…");
        worker.execute(()->{
            MessengerInspector.Result r=MessengerInspector.scan();
            runOnUiThread(()->{
                status.setText("Erişilebilir "+r.count+" mesajlaşma dosyası tarandı");
                StringBuilder b=new StringBuilder("Toplam: "+size(r.total)+"\n\n");
                for(Map.Entry<String,Long> e:r.byType.entrySet())b.append(e.getKey()).append(": ").append(size(e.getValue())).append("\n");
                b.append("\nYalnızca ortak depolamadaki medya incelenir; sohbetler korunur.");
                if(r.large.isEmpty()){notice(b.toString());return;}
                String[] files=new String[r.large.size()];
                for(int i=0;i<files.length;i++){
                    MessengerInspector.Item file=r.large.get(i);
                    files[i]=file.file.getName()+" • "+file.kind+" • "+size(file.size);
                }
                new AlertDialog.Builder(this).setTitle("Mesajlaşma medyası")
                        .setMessage(b.toString()).setItems(files,(d,i)->{
                            MessengerInspector.Item item=r.large.get(i);
                            new AlertDialog.Builder(this).setTitle(item.file.getName())
                                    .setMessage(item.file.getAbsolutePath()+"\n\nBu medya bir sohbet içinde kullanılıyor olabilir. Geri alınabilir karantinaya taşımak ister misin?")
                                    .setNegativeButton("Korunacak",null)
                                    .setPositiveButton("Karantina",(x,y)->worker.execute(()->{
                                        String message;
                                        try {
                                            File root=Environment.getExternalStorageDirectory();
                                            SafeScanner.Candidate c=new SafeScanner.Candidate(item.file,root,item.size,item.modified,
                                                    SafeScanner.Type.MESSENGER,false,"Kullanıcı seçimi",null,null);
                                            vault().move(c);message="Dosya karantinaya taşındı";
                                        }catch(Exception e){message="Taşınamadı: "+e.getMessage();}
                                        String msg=message;runOnUiThread(()->notice(msg));
                                    })).show();
                        }).setPositiveButton("Kapat",null).show();
            });
        });
    }
    private void showResidues() {
        if(!Environment.isExternalStorageManager()) {notice("Önce tüm dosyalara erişim iznini ver.");return;}
        status.setText("Android/media kalıntıları inceleniyor…");
        worker.execute(()->{
            List<AppResidueInspector.Residue> items=AppResidueInspector.inspect(this);
            runOnUiThread(()->{
                status.setText(items.size()+" olası kalıntı bulundu");
                if(items.isEmpty()){notice("Kurulu olmayan uygulamalara ait belirgin medya klasörü bulunamadı.");return;}
                String[] names=new String[Math.min(100,items.size())];
                for(int i=0;i<names.length;i++) {
                    AppResidueInspector.Residue item=items.get(i);
                    names[i]=item.packageId+" • "+size(item.bytes)+" • "+item.files+" dosya";
                }
                new AlertDialog.Builder(this).setTitle("Olası kalıntılar: yalnızca inceleme")
                        .setItems(names,(d,i)->notice("Klasör: "+items.get(i).folder
                                +"\n\nBu klasör, başka bir uygulama tarafından da kullanılıyor olabilir. Otomatik silme yok; gerekli dosyaları ana taramada ayrı ayrı seç."))
                        .setPositiveButton("Kapat",null).show();
            });
        });
    }
    private void showApps() {
        if(!AppInsights.hasUsageAccess(this)) {
            new AlertDialog.Builder(this).setTitle("Kullanım erişimi gerekli")
                    .setMessage("Uygulama kullanım zamanını ve depolama boyutunu görmek için isteğe bağlı 'Kullanım erişimi' iznini açmalısın. Sistem uygulamalarını kaldırmayı önermeyeceğiz.")
                    .setNegativeButton("İptal",null)
                    .setPositiveButton("Ayarları aç",(d,w)->startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))).show();
            return;
        }
        status.setText("Uygulamalar ve boyutlar inceleniyor…");
        worker.execute(()->{
            List<AppInsights.App> all=AppInsights.getApps(this);
            ArrayList<AppInsights.App> apps=new ArrayList<>();for(AppInsights.App a:all)if(!a.system)apps.add(a);
            runOnUiThread(()->{
                status.setText(apps.size()+" kullanıcı uygulaması listelendi");
                String[] names=new String[Math.min(100,apps.size())];
                for(int i=0;i<names.length;i++){
                    AppInsights.App a=apps.get(i);long age=a.lastUsed==0?-1:(System.currentTimeMillis()-a.lastUsed)/86400000L;
                    names[i]=a.name+" • "+(a.storage<0?"Boyut bilinmiyor":size(a.storage))
                            +" • "+(age<0?"Kullanım bilinmiyor":age+" gün");
                }
                if(names.length==0){notice("Kullanıcı uygulaması listelenemedi");return;}
                new AlertDialog.Builder(this).setTitle("Uygulamalar (yalnızca inceleme)")
                        .setItems(names,(d,i)->{
                            AppInsights.App a=apps.get(i);
                            Intent intent=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:"+a.packageName));startActivity(intent);
                        }).setPositiveButton("Kapat",null).show();
            });
        });
    }
    private static String size(long b) {
        if(b<1024)return b+" B";
        String[] u={"KB","MB","GB","TB"};double n=b;int i=-1;
        do{n/=1024.0;i++;}while(n>=1024&&i<3);
        return String.format(Locale.getDefault(),"%.1f %s",n,u[i]);
    }
    private void notice(String message) {
        if(isFinishing()||isDestroyed())return;
        new AlertDialog.Builder(this).setMessage(message).setPositiveButton("Tamam",null).show();
    }
}
