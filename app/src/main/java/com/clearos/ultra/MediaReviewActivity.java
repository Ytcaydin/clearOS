package com.clearos.ultra;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.WindowInsets;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Button;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import android.widget.CheckBox;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Media review, original-preserving compression and message-media inventory. */
public final class MediaReviewActivity extends Activity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final AtomicBoolean stopped=new AtomicBoolean(false);
    private static final int CHOOSE_PHOTO=101,CHOOSE_VIDEO=102;
    private LinearLayout root,items;
    private String section;
    private boolean busy=false;
    private ImageInspector.Results photoResults;
    private final Set<String> photoSelected=new HashSet<>();
    private int photoPage=0;
    private int photoLimit=6000;
    private TextView selectedSummary;
    @Override public void onCreate(Bundle b){super.onCreate(b);section=getIntent().getStringExtra("section");
        if(section==null)section="photos";build();}
    @Override public void onDestroy(){stopped.set(true);io.shutdownNow();super.onDestroy();}
    private void build(){
        root=ModernUi.column(this);root.setBackgroundColor(ModernUi.BG);
        root.setPadding(ModernUi.dp(this,19),ModernUi.dp(this,18),ModernUi.dp(this,19),0);
        root.setOnApplyWindowInsetsListener((v,w)->{Insets bars=w.getInsets(WindowInsets.Type.systemBars());
            root.setPadding(ModernUi.dp(this,19)+bars.left,ModernUi.dp(this,18)+bars.top,ModernUi.dp(this,19)+bars.right,bars.bottom);return w;});
        setContentView(root);
        String heading=section.equals("compress")?"Medya sıkıştırma":section.equals("messenger")?"Mesajlaşma medyası":"Fotoğraf dedektifi";
        root.addView(ModernUi.text(this,heading,23,ModernUi.TEXT,true));
        root.addView(ModernUi.muted(this,"Dosyaların iznin olmadan silinmez. Sonuçlar yalnızca cihazında işlenir.",12),ModernUi.space(this,-1,-2,9));
        ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        items=ModernUi.column(this);items.setPadding(0,ModernUi.dp(this,18),0,ModernUi.dp(this,18));scroll.addView(items);
        if(section.equals("compress"))compression();
        else if(section.equals("messenger"))messenger();
        else photos();
    }
    private void add(android.view.View view,int top){items.addView(view,ModernUi.space(this,-1,-2,top));}
    private void info(String message){add(ModernUi.muted(this,message,12),8);}
    private void message(String title,String text){new AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton("Tamam",null).show();}
    private void compression(){
        add(ModernUi.section(this,"Orijinaller korunur","Sıkıştırma sonucunda daha küçük bir kopya varsa kaydedilir."),2);
        add(ModernUi.actionCard(this,"▣","Fotoğraf sıkıştır","JPEG kalite 82 · en fazla 2560 piksel",
                ()->pick("image/*",CHOOSE_PHOTO)),16);
        add(ModernUi.actionCard(this,"▸","Video sıkıştır","H.264 · mevcut çözünürlük 720p'den düşükse büyütülmez",
                ()->pick("video/*",CHOOSE_VIDEO)),10);
        info("Kalite / boyut kontrolü tamamlandıktan sonra kopya oluşur. Orijinal dosyayı otomatik silmeyiz. Video dönüştürme cihazın medya kodeklerine bağlıdır.");
    }
    private void pick(String mime,int code){Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType(mime);intent.addCategory(Intent.CATEGORY_OPENABLE);
        try{startActivityForResult(intent,code);}catch(Exception e){message("Seçici açılamadı",e.getMessage());}}
    @Override protected void onActivityResult(int code,int result,Intent data){super.onActivityResult(code,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri file=data.getData();
        new AlertDialog.Builder(this).setTitle("Sıkıştırma başlatılsın mı?")
                .setMessage("Orijinal dosya korunur. Yeni çıktı ayrı bir dosya olarak kaydedilir.")
                .setNegativeButton("İptal",null).setPositiveButton("Başlat",(d,w)->{
                    if(code==CHOOSE_PHOTO){
                        add(ModernUi.badge(this,"Fotoğraf işleniyor…",ModernUi.AMBER),12);
                        io.execute(()->{String text;
                            try{text=MediaCompressor.compressPhoto(this,file);}catch(Exception e){text=e.toString();}
                            String output=text;runOnUiThread(()->{ActionLog.add(this,"Fotoğraf sıkıştırma",output);message("Sonuç",output);});});
                    }else if(code==CHOOSE_VIDEO){
                        add(ModernUi.badge(this,"Video dönüştürülüyor…",ModernUi.AMBER),12);
                        MediaCompressor.compressVideo(this,file,new MediaCompressor.Listener(){
                            public void success(String result){ActionLog.add(MediaReviewActivity.this,"Video sıkıştırma",result);message("Video hazır",result);}
                            public void error(String error){ActionLog.add(MediaReviewActivity.this,"Video sıkıştırma hatası",error);message("Video dönüştürülemedi",error);}
                        });
                    }
                }).show();
    }
    private void photos(){
        add(ModernUi.section(this,"Görüntü analizi","Benzerlik veya bulanıklık tespiti kesin silme kararı değildir."),4);
        Button scan=ModernUi.button(this,"Hızlı tarama · 6.000 fotoğraf",true);
        add(scan,15);scan.setOnClickListener(v->{if(!busy)scanPhotos(6000);});
        Button extensive=ModernUi.button(this,"Ayrıntılı tarama · 20.000 fotoğraf",false);
        add(extensive,9);extensive.setOnClickListener(v->{if(!busy)new AlertDialog.Builder(this)
                .setTitle("Ayrıntılı fotoğraf taraması")
                .setMessage("20.000 fotoğrafa kadar analiz uzun sürebilir ve pil tüketebilir. Fotoğrafların hiçbiri otomatik silinmez.")
                .setNegativeButton("Vazgeç",null)
                .setPositiveButton("Taramayı başlat",(d,w)->scanPhotos(20000)).show();});
        info("Benzer fotoğrafları tek başına silmiyoruz. En iyi görüntü önerisi kesin sonuç değildir; önce görüntüleri incele.");
    }
    private void scanPhotos(int requestedLimit){
        photoLimit=requestedLimit;
        if(!Environment.isExternalStorageManager()){message("İzin gerekli","Tüm dosyalara erişim iznini açmalısın.");return;}
        busy=true;stopped.set(false);items.removeAllViews();
        TextView running=ModernUi.text(this,"Fotoğraflar inceleniyor (0 / "+photoLimit+")…",16,ModernUi.TEAL,true);
        add(running,0);
        Button stopButton=ModernUi.button(this,"Taramayı durdur",false);add(stopButton,14);
        stopButton.setOnClickListener(v->{stopped.set(true);stopButton.setText("Durduruluyor…");stopButton.setEnabled(false);});
        android.content.SharedPreferences p=getSharedPreferences("excluded",0);
        ExcludedPaths exclusions=new ExcludedPaths(p.getBoolean("DCIM",false),p.getBoolean("Documents",false),p.getBoolean("Download",false));
        io.execute(()->{
            ImageInspector.Results report=ImageInspector.inspect(StorageRoots.available(this),new SafeScanner.Progress(){
                public boolean isCancelled(){return stopped.get()||Thread.currentThread().isInterrupted();}
                public boolean exclude(File file){return exclusions.excludes(file);}
                public void onUpdate(long count,String path){
                    runOnUiThread(()->{if(!isDestroyed()&&busy)running.setText(
                            count+" / "+photoLimit+" fotoğraf değerlendirildi…");});
                }
            },photoLimit);
            runOnUiThread(()->{
                if(isDestroyed())return;busy=false;items.removeAllViews();
                if(report.cancelled){add(ModernUi.text(this,"Tarama durduruldu. Yeniden başlatabilirsin.",15,ModernUi.AMBER,true),0);
                    Button repeat=ModernUi.button(this,"Yeniden tara",true);add(repeat,14);repeat.setOnClickListener(v->scanPhotos(photoLimit));return;}
                add(ModernUi.text(this,report.inspected+" fotoğraf analiz edildi",19,ModernUi.TEXT,true),0);
                info(report.similarGroups+" benzer grup · "+report.blurryCandidates+" olası bulanık görüntü"
                        +(report.limited?" · "+photoLimit+" sınırına ulaşıldı":""));
                photoResults=report;photoSelected.clear();photoPage=0;
                drawPhotoResults();
            });
        });
    }
    private void drawPhotoResults(){
        items.removeAllViews();
        if(photoResults==null)return;
        add(ModernUi.text(this,photoResults.inspected+" fotoğraf kontrol edildi",19,ModernUi.TEXT,true),0);
        info(photoResults.similarGroups+" benzer grup · "+photoResults.blurryCandidates+" olası bulanık görüntü"+
                (photoResults.limited?" · "+photoLimit+" sınırı doldu":""));
        selectedSummary=ModernUi.text(this,photoSelected.size()+" fotoğraf seçili",13,ModernUi.AMBER,true);
        add(selectedSummary,17);
        LinearLayout actions=ModernUi.row(this);
        Button clear=ModernUi.button(this,"Seçimi temizle",false);
        actions.addView(clear,new LinearLayout.LayoutParams(0,ModernUi.dp(this,45),1));
        Button move=ModernUi.button(this,"Karantinaya taşı",true);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,ModernUi.dp(this,45),1);
        p.leftMargin=ModernUi.dp(this,8);actions.addView(move,p);
        add(actions,10);
        clear.setOnClickListener(v->{photoSelected.clear();drawPhotoResults();});
        move.setOnClickListener(v->moveSelectedPhotos());
        if(photoPage*50>=photoResults.candidates.size())photoPage=Math.max(0,(photoResults.candidates.size()-1)/50);
        int start=photoPage*50;
        int end=Math.min(start+50,photoResults.candidates.size());
        info(photoResults.candidates.isEmpty()?"İncelenmesi gereken aday bulunmadı.":
                (start+1)+"–"+end+" / "+photoResults.candidates.size()+" sonuç · Sayfa "+(photoPage+1));
        for(int i=start;i<end;i++)renderPhoto(photoResults.candidates.get(i));
        if(photoResults.candidates.size()>50){
            LinearLayout pages=ModernUi.row(this);
            Button previous=ModernUi.button(this,"Önceki 50",false);
            Button next=ModernUi.button(this,"Sonraki 50",false);
            pages.addView(previous,new LinearLayout.LayoutParams(0,ModernUi.dp(this,45),1));
            LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(0,ModernUi.dp(this,45),1);
            gap.leftMargin=ModernUi.dp(this,8);pages.addView(next,gap);
            previous.setEnabled(photoPage>0);next.setEnabled(end<photoResults.candidates.size());
            previous.setOnClickListener(v->{photoPage--;drawPhotoResults();});
            next.setOnClickListener(v->{photoPage++;drawPhotoResults();});
            add(pages,12);
        }
    }
    private void moveSelectedPhotos(){
        if(photoResults==null||photoSelected.isEmpty()){
            message("Seçim gerekli","Önce inceleyip bir fotoğraf seç.");return;
        }
        ArrayList<SafeScanner.Candidate> targets=new ArrayList<>();Set<String> seen=new HashSet<>();long bytes=0;
        for(SafeScanner.Candidate c:photoResults.candidates){
            if(photoSelected.contains(c.key())&&seen.add(c.key())){targets.add(c);bytes+=c.size;}
        }
        final long amount=bytes;
        new AlertDialog.Builder(this).setTitle("Fotoğrafları karantinaya taşı")
                .setMessage(targets.size()+" fotoğraf · "+ModernUi.bytes(amount)+".\n\nBenzerlik/bulanıklık tahmini hatalı olabilir. Önemli fotoğrafları kontrol ettiğinden emin ol. Karantina alan boşaltmaz.")
                .setNegativeButton("Vazgeç",null).setPositiveButton("Taşı",(d,w)->{
                    io.execute(()->{
                        QuarantineManager trash=new QuarantineManager(StorageRoots.available(this));
                        int ok=0;StringBuilder problems=new StringBuilder();Set<String> moved=new HashSet<>();
                        for(SafeScanner.Candidate c:targets){
                            try{trash.move(c);ok++;moved.add(c.key());}
                            catch(Exception ex){if(problems.length()<1000)problems.append(c.file.getName()).append(": ").append(ex.getMessage()).append('\n');}
                        }
                        final int success=ok;final String errors=problems.toString();
                        ActionLog.add(this,"Medya karantina",success+" taşındı, "+(targets.size()-success)+" başarısız");
                        runOnUiThread(()->{
                            if(isDestroyed())return;
                            photoSelected.clear();photoResults.candidates.removeIf(c->moved.contains(c.key()));
                            drawPhotoResults();message("Karantina sonucu",success+" taşındı · "+
                                    (targets.size()-success)+" hata.\n"+errors);
                        });
                    });
                }).show();
    }
    private void renderPhoto(SafeScanner.Candidate c){
        LinearLayout row=ModernUi.row(this);row.setBackground(ModernUi.outlined(this,ModernUi.PANEL,16));
        row.setPadding(ModernUi.dp(this,9),ModernUi.dp(this,9),ModernUi.dp(this,9),ModernUi.dp(this,9));
        ImageView preview=new ImageView(this);preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        try {BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;
            BitmapFactory.decodeFile(c.file.getAbsolutePath(),o);o.inJustDecodeBounds=false;
            o.inPreferredConfig=Bitmap.Config.RGB_565;
            o.inSampleSize=1;while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>180)o.inSampleSize*=2;
            Bitmap img=BitmapFactory.decodeFile(c.file.getAbsolutePath(),o);preview.setImageBitmap(img);
        }catch(Exception|OutOfMemoryError ignored) { }
        row.addView(preview,new LinearLayout.LayoutParams(ModernUi.dp(this,64),ModernUi.dp(this,64)));
        LinearLayout labels=ModernUi.column(this);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);
        p.leftMargin=ModernUi.dp(this,12);row.addView(labels,p);
        TextView name=ModernUi.text(this,c.file.getName(),13,ModernUi.TEXT,true);
        name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);labels.addView(name);
        labels.addView(ModernUi.muted(this,c.type.label+" · "+ModernUi.bytes(c.size),11),ModernUi.space(this,-1,-2,5));
        CheckBox check=new CheckBox(this);check.setChecked(photoSelected.contains(c.key()));
        check.setButtonTintList(android.content.res.ColorStateList.valueOf(ModernUi.TEAL));
        row.addView(check,new LinearLayout.LayoutParams(ModernUi.dp(this,43),ModernUi.dp(this,45)));
        check.setOnClickListener(v->{if(!photoSelected.add(c.key()))photoSelected.remove(c.key());
            selectedSummary.setText(photoSelected.size()+" fotoğraf seçili");});
        row.setOnClickListener(v->message(c.file.getName(),c.reason+"\n\n"+c.file.getAbsolutePath()));add(row,8);
    }
    private void messenger(){
        add(ModernUi.section(this,"Mesajlaşma analizi","WhatsApp / Telegram erişilebilir medya klasörleri"),4);
        Button scan=ModernUi.button(this,"Medya dosyalarını tara",true);add(scan,15);
        scan.setOnClickListener(v->{scan.setEnabled(false);scan.setText("Taranıyor…");
            io.execute(()->{
                MessengerInspector.Result report=MessengerInspector.scan();
                runOnUiThread(()->{if(isDestroyed())return;items.removeAllViews();
                    add(ModernUi.text(this,ModernUi.bytes(report.total),25,ModernUi.TEXT,true),0);
                    info(report.count+" erişilebilir medya dosyası · Mesajlar ve veritabanları taranmaz.");
                    for(java.util.Map.Entry<String,Long> e:report.byType.entrySet()){
                        LinearLayout card=ModernUi.card(this);
                        card.addView(ModernUi.text(this,e.getKey(),14,ModernUi.TEXT,true));
                        card.addView(ModernUi.muted(this,ModernUi.bytes(e.getValue()),12),ModernUi.space(this,-1,-2,6));
                        add(card,9);
                    }
                    add(ModernUi.section(this,"Medya önizlemeleri","İlk 24 erişilebilir fotoğraf · Salt okunur"),20);
                    for(MessengerInspector.Item photo:report.photoPreviews){
                        LinearLayout row=ModernUi.row(this);
                        row.setBackground(ModernUi.outlined(this,ModernUi.PANEL,15));
                        row.setPadding(ModernUi.dp(this,9),ModernUi.dp(this,9),ModernUi.dp(this,9),ModernUi.dp(this,9));
                        ImageView preview=new ImageView(this);preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        try{BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
                            BitmapFactory.decodeFile(photo.file.getAbsolutePath(),options);options.inJustDecodeBounds=false;
                            options.inSampleSize=1;
                            while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>144)options.inSampleSize*=2;
                            preview.setImageBitmap(BitmapFactory.decodeFile(photo.file.getAbsolutePath(),options));
                        }catch(Exception|OutOfMemoryError ignored){}
                        row.addView(preview,new LinearLayout.LayoutParams(ModernUi.dp(this,56),ModernUi.dp(this,56)));
                        LinearLayout text=ModernUi.column(this);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);
                        p.leftMargin=ModernUi.dp(this,12);row.addView(text,p);
                        text.addView(ModernUi.text(this,photo.file.getName(),12,ModernUi.TEXT,true));
                        text.addView(ModernUi.muted(this,ModernUi.bytes(photo.size),11),ModernUi.space(this,-1,-2,5));
                        row.setOnClickListener(x->message(photo.file.getName(),photo.file.getAbsolutePath()));add(row,8);
                    }
                    add(ModernUi.section(this,"Büyük dosyalar","Salt okunur inceleme · dosyalara dokunulmaz"),22);
                    for(MessengerInspector.Item item:report.large){
                        LinearLayout c=ModernUi.card(this);
                        c.addView(ModernUi.text(this,item.file.getName(),13,ModernUi.TEXT,true));
                        c.addView(ModernUi.muted(this,ModernUi.bytes(item.size)+" · "+item.kind,11),ModernUi.space(this,-1,-2,7));
                        c.setOnClickListener(x->message(item.file.getName(),item.file.getAbsolutePath()));add(c,7);
                    }
                });
            });
        });
        info("Erişimi kısıtlı Android/data konumları veya özel sohbet verileri okunamaz. Dosyaların silinmesi için ayrı güvenli değerlendirme gerekir.");
    }
}
