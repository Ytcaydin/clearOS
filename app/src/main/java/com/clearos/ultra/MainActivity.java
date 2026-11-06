package com.clearos.ultra;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** V5 main experience: a real tabbed dashboard, controlled review and offline scans. */
public final class MainActivity extends Activity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancel=new AtomicBoolean(false);
    private final Set<String> picked=new HashSet<>();
    private List<SafeScanner.Candidate> candidates=new ArrayList<>();
    private SafeScanner.Type filter=null;
    private int tab=0, pageSize=70, mapPage=12, generation=0;
    private boolean scanning=false;
    private long scanned=0;
    private String progress="Hazır";
    private LinearLayout root, content, navigation;
    private ScrollView scroll;
    private TextView title;

    // Quarantine size is read from shared storage (one metadata file per entry over FUSE), so it is
    // computed off the UI thread and cached; home() only displays the last known value.
    private final ExecutorService stats=Executors.newSingleThreadExecutor();
    private volatile long quarantinedBytes=-1;
    private final AtomicBoolean statsRunning=new AtomicBoolean(false);

    @Override public void onCreate(Bundle state) {super.onCreate(state); buildShell();render();}
    @Override public void onResume() {super.onResume();if(root!=null){render();refreshQuarantineStats();}}
    @Override public void onDestroy() {cancel.set(true);generation++;io.shutdownNow();stats.shutdownNow();super.onDestroy();}

    private void refreshQuarantineStats() {
        if(!statsRunning.compareAndSet(false,true))return;
        try {
            stats.execute(()->{
                long bytes;
                try{bytes=new QuarantineManager(StorageRoots.available(this)).containedBytes();}
                catch(Exception e){bytes=-1;}
                long value=bytes;
                runOnUiThread(()->{statsRunning.set(false);if(isDestroyed())return;
                    boolean changed=value!=quarantinedBytes;quarantinedBytes=value;
                    if(changed&&tab==0)render();});
            });
        } catch(java.util.concurrent.RejectedExecutionException e){statsRunning.set(false);}
    }

    private void buildShell() {
        root=ModernUi.column(this);root.setBackgroundColor(ModernUi.BG);
        root.setOnApplyWindowInsetsListener((v,in)->{
            Insets bars=in.getInsets(WindowInsets.Type.systemBars());
            root.setPadding(bars.left,bars.top,bars.right,bars.bottom);return in;
        });
        setContentView(root);
        LinearLayout header=ModernUi.column(this);
        header.setPadding(ModernUi.dp(this,21),ModernUi.dp(this,19),ModernUi.dp(this,21),ModernUi.dp(this,14));
        title=ModernUi.text(this,"ClearOS Ultra",23,ModernUi.TEXT,true);header.addView(title);
        header.addView(ModernUi.muted(this,"V5.0  ·  Çevrimdışı  ·  Güvenli temizlik",11),ModernUi.space(this,-1,-2,5));
        root.addView(header);
        scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.setClipToPadding(false);
        content=ModernUi.column(this);content.setPadding(ModernUi.dp(this,18),0,ModernUi.dp(this,18),ModernUi.dp(this,24));
        scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        navigation=ModernUi.row(this);navigation.setBackgroundColor(ModernUi.PANEL);
        navigation.setPadding(ModernUi.dp(this,5),ModernUi.dp(this,8),ModernUi.dp(this,5),ModernUi.dp(this,8));
        root.addView(navigation);
    }
    private void render() {
        if(content==null)return;
        int y=scroll.getScrollY();content.removeAllViews();
        title.setText(new String[]{"ClearOS Ultra","Temizlik","Medya","Analiz","Araçlar"}[tab]);
        if(tab==0)home();else if(tab==1)clean();else if(tab==2)media();else if(tab==3)analysis();else tools();
        nav();scroll.post(()->scroll.scrollTo(0,y));
    }
    private void change(int n) {tab=n;pageSize=70;scroll.scrollTo(0,0);render();}
    private void nav() {
        navigation.removeAllViews();
        String[] names={"Ana Sayfa","Temizlik","Medya","Analiz","Araçlar"};
        for(int i=0;i<names.length;i++) {
            final int to=i;boolean active=i==tab;
            LinearLayout tile=ModernUi.column(this);tile.setGravity(Gravity.CENTER);
            if(active)tile.setBackground(ModernUi.shape(this,ModernUi.PANEL2,14));
            tile.addView(new ModernUi.NavIcon(this,i,active),new LinearLayout.LayoutParams(ModernUi.dp(this,27),ModernUi.dp(this,27)));
            TextView label=ModernUi.text(this,names[i],10,active?ModernUi.TEAL:ModernUi.MUTED,active);
            label.setGravity(Gravity.CENTER);tile.addView(label,ModernUi.space(this,-1,-2,4));
            navigation.addView(tile,new LinearLayout.LayoutParams(0,ModernUi.dp(this,60),1));
            tile.setClickable(true);tile.setFocusable(true);
            tile.setContentDescription(names[i]+(active?", seçili sekme":" sekmesine git"));
            tile.setOnClickListener(x->change(to));
        }
    }
    private void add(View v,int margin) {ModernUi.attach(content,v,this,margin);}
    private void section(String heading,String sub,int top) {add(ModernUi.section(this,heading,sub),top);}
    private long[] storage() {
        try {StatFs stat=new StatFs(Environment.getExternalStorageDirectory().getAbsolutePath());
            long total=stat.getTotalBytes(),free=stat.getAvailableBytes();return new long[]{total,total-free,free};}
        catch(Exception e){return new long[]{0,0,0};}
    }
    private long suggested() {long bytes=0;for(SafeScanner.Candidate c:candidates)if(c.suggested)bytes+=c.size;return bytes;}
    private long lastSuggested() {return getSharedPreferences("last_scan",0).getLong("suggested",0);}
    private int lastFound() {return getSharedPreferences("last_scan",0).getInt("found",0);}
    private void statBox(LinearLayout row,String number,String label,int accent) {
        LinearLayout box=ModernUi.card(this);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);
        if(row.getChildCount()>0)p.leftMargin=ModernUi.dp(this,9);
        row.addView(box,p);box.addView(ModernUi.text(this,number,21,accent,true));
        box.addView(ModernUi.muted(this,label,11),ModernUi.space(this,-1,-2,8));
    }
    private void home() {
        long[] s=storage();long used=s[1],total=s[0];
        LinearLayout disk=ModernUi.card(this);
        disk.addView(ModernUi.muted(this,"DEPOLAMA GENEL BAKIŞ",11));
        LinearLayout r=ModernUi.row(this);disk.addView(r,ModernUi.space(this,-1,-2,12));
        float percent=total==0?0:used/(float)total;
        r.addView(new ModernUi.Donut(this,percent,String.format(Locale.getDefault(),"%%%d",Math.round(percent*100))),
                new LinearLayout.LayoutParams(ModernUi.dp(this,138),ModernUi.dp(this,138)));
        LinearLayout details=ModernUi.column(this);LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(0,-2,1);
        dp.leftMargin=ModernUi.dp(this,18);r.addView(details,dp);
        details.addView(ModernUi.muted(this,"Kullanılan alan",11));
        details.addView(ModernUi.text(this,ModernUi.bytes(used),23,ModernUi.TEXT,true),ModernUi.space(this,-1,-2,7));
        details.addView(ModernUi.text(this,ModernUi.bytes(s[2])+" boş",14,ModernUi.TEAL,true),ModernUi.space(this,-1,-2,7));
        details.addView(ModernUi.muted(this,"Toplam "+ModernUi.bytes(total),11),ModernUi.space(this,-1,-2,7));
        add(disk,10);
        LinearLayout counters=ModernUi.row(this);
        statBox(counters,ModernUi.bytes(candidates.isEmpty()?lastSuggested():suggested()),"Önerilen dosya boyutu",ModernUi.AMBER);
        statBox(counters,quarantinedBytes<0?"…":ModernUi.bytes(quarantinedBytes),"Karantinada",ModernUi.BLUE);
        add(counters,10);
        long when=getSharedPreferences("last_scan",0).getLong("when",0);
        int found=candidates.isEmpty()?lastFound():candidates.size();
        add(ModernUi.muted(this,when==0?"Henüz tamamlanan tarama yok":
                "Son tarama: "+new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm",Locale.getDefault()).format(new java.util.Date(when))+
                " · "+found+" sonuç · Önerilmeyen dosyalar da incelemeye açıktır.",11),10);
        section("Akıllı temizlik",scanning?"Tarama devam ediyor":"Dosyaları incelemeden silmiyoruz",25);
        LinearLayout scan=ModernUi.card(this);
        scan.addView(ModernUi.text(this,scanning?"Tarama devam ediyor":"Telefonunu analiz et",17,ModernUi.TEXT,true));
        scan.addView(ModernUi.muted(this,scanning?scanned+" dosya tarandı · "+progress:
                "Kopyaları, eski APK'ları ve erişilebilir büyük dosyaları bul.",12),ModernUi.space(this,-1,-2,7));
        android.widget.Button btn=ModernUi.button(this,scanning?"Taramayı durdur":"Akıllı taramayı başlat",true);
        scan.addView(btn,ModernUi.space(this,-1,48,15));
        btn.setOnClickListener(v->{if(scanning)cancel.set(true);else startScan();});add(scan,12);
        section("Hızlı işlemler","Sık kullandığın araçlar",23);
        add(ModernUi.actionCard(this,"♻","Karantina yönetimi", "Geri yükle, seçerek sil, hata raporu",
                ()->startActivity(new Intent(this,QuarantineActivity.class))),12);
        add(ModernUi.actionCard(this,"▤","Depolama analizi", "Klasör haritası ve geçmiş",()->change(3)),9);
        add(ModernUi.actionCard(this,"▣","Medya optimizasyonu", "Benzer fotoğraf, video ve mesajlaşma",()->change(2)),9);
        if(!candidates.isEmpty())add(ModernUi.actionCard(this,"✓",""+candidates.size()+" tarama sonucu",
                "Sonuçları incele ve dosya seç",()->change(1)),9);
    }
    private boolean permitted() {return Environment.isExternalStorageManager();}
    private void requestPermission() {
        try {startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:"+getPackageName())));}
        catch(Exception e){startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));}
    }
    private void startScan() {
        if(scanning)return;
        if(!permitted()) {new AlertDialog.Builder(this).setTitle("Dosya izni gerekli")
                .setMessage("Paylaşılan depolamayı taramak için Tüm dosyalara erişim iznini açmalısın.")
                .setPositiveButton("İzni aç",(d,w)->requestPermission()).setNegativeButton("Vazgeç",null).show();return;}
        scanning=true;cancel.set(false);scanned=0;progress="Başlıyor";int job=++generation;
        render();
        android.content.SharedPreferences p=getSharedPreferences("excluded",0);
        ExcludedPaths exclusions=new ExcludedPaths(p.getBoolean("DCIM",false),p.getBoolean("Documents",false),p.getBoolean("Download",false));
        io.execute(()->{
            SafeScanner.ScanResult result=SafeScanner.scan(StorageRoots.available(this),new SafeScanner.Progress() {
                public boolean isCancelled(){return cancel.get()||Thread.currentThread().isInterrupted();}
                public boolean exclude(File f){return exclusions.excludes(f);}
                public void onUpdate(long count,String stage){
                    runOnUiThread(()->{if(job!=generation)return;
                        scanned=count;progress=stage==null?"Kontrol ediliyor":stage;
                        if(tab==0 && count%1500==0)render();});
                }
            });
            runOnUiThread(()->{
                if(job!=generation||isDestroyed())return;scanning=false;
                if(!result.cancelled){
                    candidates=result.candidates;picked.clear();
                    getSharedPreferences("last_scan",0).edit().putInt("found",candidates.size())
                            .putLong("suggested",result.suggestedBytes).putLong("visited",result.visitedFiles)
                            .putLong("when",System.currentTimeMillis()).apply();
                    saveFolderMap(result.folderBytes);
                    mapPage=12;
                    try{StorageHistory.record(this);}catch(Exception ignored){}
                    tab=1;scroll.scrollTo(0,0);
                } else progress="Tarama durduruldu";
                render();
                if(!result.cancelled && result.limited)dialog("Tarama sınırı","Güvenlik kaynak limitine ulaşıldı. Sonuçlar kısmi olabilir.");
            });
        });
    }
    private void saveFolderMap(Map<String,Long> folders) {
        List<Map.Entry<String,Long>> top=new ArrayList<>(folders.entrySet());
        top.sort((a,b)->Long.compare(b.getValue(),a.getValue()));
        org.json.JSONArray arr=new org.json.JSONArray();
        for(int i=0;i<Math.min(70,top.size());i++){
            try {org.json.JSONObject obj=new org.json.JSONObject();obj.put("path",top.get(i).getKey());
                obj.put("bytes",top.get(i).getValue());arr.put(obj);}catch(Exception ignored){}
        }
        getSharedPreferences("folder_map",0).edit().putString("json",arr.toString()).apply();
    }
    private void clean() {
        section("Temizlik merkezi",candidates.isEmpty()?"Son tarama: "+lastFound()+" sonuç · Güncel liste için tekrar tara":
                candidates.size()+" dosya bulundu · "+ModernUi.bytes(suggested())+
                " otomatik öneri · Diğerleri inceleme gerektirir",7);
        LinearLayout actions=ModernUi.row(this);add(actions,12);
        android.widget.Button scan=ModernUi.button(this,scanning?"Durdur":"Yeniden tara",true);
        actions.addView(scan,new LinearLayout.LayoutParams(0,ModernUi.dp(this,46),1));
        scan.setOnClickListener(v->{if(scanning)cancel.set(true);else startScan();});
        if(!permitted())add(ModernUi.actionCard(this,"!","Dosya erişimi gerekli", "Tarama iznini aç",this::requestPermission),12);
        if(candidates.isEmpty()){add(ModernUi.actionCard(this,"⌕","Sonuçları göster","Taramayla güncel ve doğrulanmış sonuçlar oluştur",this::startScan),14);return;}
        HorizontalScrollView chips=new HorizontalScrollView(this);chips.setHorizontalScrollBarEnabled(false);
        LinearLayout chipRow=ModernUi.row(this);chips.addView(chipRow);
        String[] labels=new String[SafeScanner.Type.values().length+1];labels[0]="Tümü";
        for(int i=0;i<SafeScanner.Type.values().length;i++)labels[i+1]=SafeScanner.Type.values()[i].label;
        for(int i=0;i<labels.length;i++){
            SafeScanner.Type t=i==0?null:SafeScanner.Type.values()[i-1];
            TextView chip=ModernUi.badge(this,labels[i],filter==t?ModernUi.TEAL:ModernUi.MUTED);
            chip.setPadding(ModernUi.dp(this,14),ModernUi.dp(this,10),ModernUi.dp(this,14),ModernUi.dp(this,10));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2);cp.rightMargin=ModernUi.dp(this,8);
            chipRow.addView(chip,cp);chip.setOnClickListener(v->{filter=t;pageSize=70;scroll.scrollTo(0,0);render();});
        }
        add(chips,17);
        LinearLayout mini=ModernUi.row(this);
        android.widget.Button suggest=ModernUi.button(this,"Güvenli önerileri seç",false);
        mini.addView(suggest,new LinearLayout.LayoutParams(0,ModernUi.dp(this,42),1));
        android.widget.Button clear=ModernUi.button(this,"Seçimi kaldır",false);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,ModernUi.dp(this,42),1);cp.leftMargin=ModernUi.dp(this,7);mini.addView(clear,cp);
        suggest.setOnClickListener(v->{for(SafeScanner.Candidate c:candidates)if(c.suggested)picked.add(c.key());render();});
        clear.setOnClickListener(v->{picked.clear();render();});add(mini,12);
        long selectedSize=0;int count=0;
        for(SafeScanner.Candidate c:candidates)if(picked.contains(c.key())){selectedSize+=c.size;count++;}
        LinearLayout selection=ModernUi.card(this);
        selection.addView(ModernUi.text(this,count+" seçili  ·  "+ModernUi.bytes(selectedSize),17,ModernUi.AMBER,true));
        selection.addView(ModernUi.muted(this,"Karantinaya taşımak alan boşaltmaz. Kalıcı silme ayrı onay ister.",11),ModernUi.space(this,-1,-2,7));
        android.widget.Button quarantine=ModernUi.button(this,"Seçilenleri karantinaya taşı",true);
        quarantine.setEnabled(count>0&&!scanning);quarantine.setAlpha(count>0&&!scanning?1:.45f);
        selection.addView(quarantine,ModernUi.space(this,-1,47,12));quarantine.setOnClickListener(v->confirmMove());
        add(selection,12);
        int shown=0;
        for(SafeScanner.Candidate c:candidates) {
            if(filter!=null&&c.type!=filter)continue;
            if(shown++>=pageSize)break;
            LinearLayout item=ModernUi.row(this);item.setPadding(ModernUi.dp(this,11),ModernUi.dp(this,12),ModernUi.dp(this,11),ModernUi.dp(this,12));
            item.setBackground(ModernUi.outlined(this,ModernUi.PANEL,15));
            CheckBox box=new CheckBox(this);box.setChecked(picked.contains(c.key()));
            box.setButtonTintList(android.content.res.ColorStateList.valueOf(ModernUi.TEAL));
            item.addView(box,new LinearLayout.LayoutParams(ModernUi.dp(this,43),ModernUi.dp(this,44)));
            LinearLayout txt=ModernUi.column(this);item.addView(txt,new LinearLayout.LayoutParams(0,-2,1));
            TextView name=ModernUi.text(this,c.file.getName(),13,ModernUi.TEXT,true);name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);txt.addView(name);
            txt.addView(ModernUi.muted(this,c.type.label+" · "+ModernUi.bytes(c.size)+" · "+RiskEngine.risk(c).label,11),ModernUi.space(this,-1,-2,5));
            TextView path=ModernUi.muted(this,c.file.getParent(),10);path.setSingleLine(true);path.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            txt.addView(path,ModernUi.space(this,-1,-2,4));
            boolean movable=c.type!=SafeScanner.Type.EMPTY;
            if(!movable){box.setEnabled(false);box.setAlpha(.35f);}
            Runnable toggle=()->{
                if(!movable){dialog("Boş klasör", "Bu sürüm boş klasörleri bilgi amaçlı gösterir; karantinaya klasör taşımak desteklenmiyor.");return;}
                if(!picked.add(c.key()))picked.remove(c.key());int y=scroll.getScrollY();render();scroll.post(()->scroll.scrollTo(0,y));
            };
            box.setOnClickListener(v->toggle.run());item.setOnClickListener(v->toggle.run());add(item,7);
        }
        long totalShown=candidates.stream().filter(c->filter==null||c.type==filter).count();
        if(totalShown>pageSize) {
            android.widget.Button more=ModernUi.button(this,"Daha fazla göster ("+(totalShown-pageSize)+")",false);
            add(more,10);more.setOnClickListener(v->{pageSize+=70;render();});
        }
    }
    private void confirmMove() {
        List<SafeScanner.Candidate> moving=new ArrayList<>();long bytes=0;
        for(SafeScanner.Candidate c:candidates)if(picked.contains(c.key())){moving.add(c);bytes+=c.size;}
        if(moving.isEmpty())return;
        for(SafeScanner.Candidate c:moving)if(c.type==SafeScanner.Type.DUPLICATE&&c.duplicateKeep!=null
                && picked.contains(c.duplicateKeep.getAbsolutePath())) {
            dialog("Kopya koruması","Aynı içeriğin korunacak aslı da seçilmiş. En az bir kopyayı bırak.");return;
        }
        long total=bytes;
        new AlertDialog.Builder(this).setTitle("Güvenli karantinaya taşı")
                .setMessage(moving.size()+" dosya · "+ModernUi.bytes(total)+"\n\nDosyalar aynı depolama birimindeki gizli karantinaya taşınacak. Gerçek boş alan henüz artmaz.")
                .setNegativeButton("Vazgeç",null).setPositiveButton("Taşı",(d,w)->{
                    if(scanning)return;scanning=true;render();
                    io.execute(()->{
                        QuarantineManager manager=new QuarantineManager(StorageRoots.available(this));
                        int ok=0;List<String> failure=new ArrayList<>();Set<String> moved=new HashSet<>();
                        for(SafeScanner.Candidate c:moving){
                            try {manager.move(c);ok++;moved.add(c.key());}
                            catch(IOException|SecurityException ex){
                                if(failure.size()<40)failure.add(c.file.getName()+": "+ex.getMessage());
                            }
                        }
                        int success=ok;int failed=moving.size()-ok;
                        runOnUiThread(()->{
                            scanning=false;picked.removeAll(moved);candidates.removeIf(c->moved.contains(c.key()));render();
                            refreshQuarantineStats();
                            ActionLog.add(this,"Karantina",success+" taşındı, "+failed+" başarısız");
                            new AlertDialog.Builder(this).setTitle("Karantina işlemi tamamlandı")
                                    .setMessage(success+" taşındı · "+failed+" taşınamadı\n\n"+String.join("\n",failure))
                                    .setPositiveButton("Karantinayı aç",(x,y)->startActivity(new Intent(this,QuarantineActivity.class)))
                                    .setNegativeButton("Kapat",null).show();
                        });
                    });
                }).show();
    }
    private void media() {
        section("Medya merkezi","Fotoğraf ve videoların kontrolü sende",7);
        int videoCount=0, screenshotCount=0;
        long videoBytes=0, screenshotBytes=0;
        for(SafeScanner.Candidate candidate:candidates) {
            String n=candidate.file.getName().toLowerCase(Locale.ROOT);
            if(candidate.type==SafeScanner.Type.LARGE &&
                    (n.endsWith(".mp4")||n.endsWith(".mov")||n.endsWith(".mkv")||n.endsWith(".webm"))) {
                videoCount++;videoBytes+=candidate.size;
            }
            if(candidate.type==SafeScanner.Type.SCREENSHOT) {screenshotCount++;screenshotBytes+=candidate.size;}
        }
        LinearLayout counts=ModernUi.row(this);
        statBox(counts,candidates.isEmpty()?"—":ModernUi.bytes(videoBytes),"Büyük video adayları · "+videoCount,ModernUi.BLUE);
        statBox(counts,candidates.isEmpty()?"—":ModernUi.bytes(screenshotBytes),"Ekran görüntüleri · "+screenshotCount,ModernUi.TEAL);
        add(counts,13);
        add(ModernUi.muted(this,candidates.isEmpty()?"Bu özet için önce Akıllı Tarama çalıştır.":
                "Bu sayılar son taramadaki inceleme adaylarıdır; tüm galeri boyutu değildir.",11),9);
        add(ModernUi.actionCard(this,"▤","Büyük dosyaları incele","Son taramanın büyük dosyalarını filtrele",
                ()->{filter=SafeScanner.Type.LARGE;change(1);}),17);
        add(ModernUi.actionCard(this,"▣","Benzer / bulanık fotoğraflar","Görüntü karşılaştırması · Otomatik silme yok",
                ()->startActivity(new Intent(this,MediaReviewActivity.class).putExtra("section","photos"))),9);
        add(ModernUi.actionCard(this,"▸","Fotoğraf ve video sıkıştırma","Orijinali koruyarak daha küçük kopya üret",
                ()->startActivity(new Intent(this,MediaReviewActivity.class).putExtra("section","compress"))),9);
        add(ModernUi.actionCard(this,"◉","WhatsApp ve Telegram medyası","Erişilebilir fotoğraf, video ve belgeler",
                ()->startActivity(new Intent(this,MediaReviewActivity.class).putExtra("section","messenger"))),9);
    }
    private void analysis() {
        section("Depolama analizi","Telefonunun depolama kullanımını keşfet",7);
        long[] disk=storage();
        LinearLayout counter=ModernUi.row(this);
        statBox(counter,ModernUi.bytes(disk[1]),"Kullanılan",ModernUi.TEXT);
        statBox(counter,ModernUi.bytes(disk[2]),"Boş",ModernUi.TEAL);add(counter,15);
        section("Klasör haritası","Son taramada hesaplanan klasör büyüklükleri",25);
        String json=getSharedPreferences("folder_map",0).getString("json","[]");
        try {
            org.json.JSONArray folders=new org.json.JSONArray(json);
            if(folders.length()==0)add(ModernUi.muted(this,"Klasör haritası için önce tarama yap.",13),14);
            long biggest=folders.length()>0?folders.getJSONObject(0).optLong("bytes",1):1;
            for(int i=0;i<Math.min(folders.length(),mapPage);i++){
                org.json.JSONObject f=folders.optJSONObject(i);if(f==null)continue;
                LinearLayout c=ModernUi.card(this);
                TextView name=ModernUi.text(this,DisplayPaths.label(f.optString("path")),12,ModernUi.TEXT,true);
                name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                c.addView(name);c.addView(ModernUi.muted(this,ModernUi.bytes(f.optLong("bytes")),11),ModernUi.space(this,-1,-2,6));
                c.addView(ModernUi.progress(this,(int)(1000.0*f.optLong("bytes")/Math.max(1,biggest))),ModernUi.space(this,-1,7,10));add(c,8);
            }
            if(folders.length()>mapPage){
                Button more=ModernUi.button(this,"Sonraki klasörleri göster ("+(folders.length()-mapPage)+" kaldı)",false);
                add(more,10);more.setOnClickListener(v->{mapPage+=12;render();});
            }
        }catch(Exception ex){add(ModernUi.muted(this,"Harita verisi okunamadı; yeniden tara.",12),12);}
        section("Depolama geçmişi","Günlük yerel ölçümler · 365 güne kadar saklanır",24);
        LinearLayout period=ModernUi.row(this);add(period,12);
        Button week=ModernUi.button(this,"Son 7 gün",false);
        Button month=ModernUi.button(this,"Son 30 gün",false);
        period.addView(week,new LinearLayout.LayoutParams(0,ModernUi.dp(this,43),1));
        LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(0,ModernUi.dp(this,43),1);
        gap.leftMargin=ModernUi.dp(this,9);period.addView(month,gap);
        week.setOnClickListener(v->{getPreferences(0).edit().putInt("trend_days",7).apply();render();});
        month.setOnClickListener(v->{getPreferences(0).edit().putInt("trend_days",30).apply();render();});
        int days=getPreferences(0).getInt("trend_days",30);
        List<StorageHistory.Reading> history=new ArrayList<>();
        long today=StorageHistory.todayLocal();
        for(StorageHistory.Reading r:StorageHistory.list(this))if(r.day>=today-days+1 && r.day<=today)history.add(r);
        add(ModernUi.muted(this,"Gösterilen dönem: Son "+days+" gün",11),8);
        if(history.size()<2)add(ModernUi.muted(this,"Bu dönemde grafik için en az iki farklı günlük ölçüm gerekli.",12),12);
        else{
            LinearLayout card=ModernUi.card(this);
            card.addView(new ModernUi.Trend(this,history),ModernUi.space(this,-1,165,3));
            long delta=history.get(history.size()-1).used-history.get(0).used;
            card.addView(ModernUi.text(this,(delta>=0?"+":"−")+ModernUi.bytes(Math.abs(delta)),25,
                    delta>=0?ModernUi.AMBER:ModernUi.TEAL,true));
            card.addView(ModernUi.muted(this,"İlk kayda göre kullanılan alan değişimi",11),ModernUi.space(this,-1,-2,7));
            add(card,12);
        }
        add(ModernUi.actionCard(this,"▦","Uygulama analizi","Uygulamaların boyutu ve son kullanımı",
                ()->startActivity(new Intent(this,DeviceToolsActivity.class).putExtra("section","apps"))),16);
    }
    private void tools() {
        section("Yönetim araçları","Önce güvenlik, sonra depolama tasarrufu",7);
        add(ModernUi.actionCard(this,"♻","Karantina & kurtarma","Toplu seçim, geri yükleme, kalıcı silme",
                ()->startActivity(new Intent(this,QuarantineActivity.class))),15);
        add(ModernUi.actionCard(this,"⚡","Ultra Mod kurulumu","Shizuku durum kontrolü ve önbellek yönetimi",
                ()->startActivity(new Intent(this,DeviceToolsActivity.class).putExtra("section","ultra"))),9);
        add(ModernUi.actionCard(this,"▦","Uygulamalar","Büyük / kullanılmayan uygulamaları incele",
                ()->startActivity(new Intent(this,DeviceToolsActivity.class).putExtra("section","apps"))),9);
        add(ModernUi.actionCard(this,"◇","Olası uygulama kalıntıları","Eski uygulamalara ait olabilecek medya klasörleri",
                ()->startActivity(new Intent(this,DeviceToolsActivity.class).putExtra("section","residue"))),9);
        add(ModernUi.actionCard(this,"◷","Otomasyon ve ayarlar","Haftalık tarama, korunan klasörler",
                ()->startActivity(new Intent(this,DeviceToolsActivity.class).putExtra("section","settings"))),9);
        add(ModernUi.actionCard(this,"✓","Cihaz tanılama", "Dosya izinleri ve servis durumunu kontrol et",
                ()->startActivity(new Intent(this,DeviceToolsActivity.class).putExtra("section","diagnostics"))),9);
        add(ModernUi.actionCard(this,"≡","İşlem günlüğü","Başarılı ve başarısız temizlik işlemleri",
                ()->startActivity(new Intent(this,ActionLogActivity.class))),9);
    }
    private void dialog(String title,String message){new AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setPositiveButton("Tamam",null).show();}
}
