package com.clearos.ultra;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Insets;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Button;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Full-screen quarantine with verified batch purge, individual restore, and detailed results. */
public final class QuarantineActivity extends Activity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Set<String> checked=new HashSet<>();
    private final ArrayList<TrashStore.Entry> all=new ArrayList<>(), visible=new ArrayList<>();
    private ListView list;private TextView summary,sub,progress;
    private Button restore,delete,select;
    private boolean busy=false,sortLargest=false,filterLarge=false;
    private QuarantineManager manager;

    @Override public void onCreate(Bundle state){super.onCreate(state);manager=new QuarantineManager(StorageRoots.available(this));build();reload();}
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
    private void build(){
        LinearLayout root=ModernUi.column(this);root.setBackgroundColor(ModernUi.BG);
        root.setPadding(ModernUi.dp(this,17),ModernUi.dp(this,17),ModernUi.dp(this,17),0);
        root.setOnApplyWindowInsetsListener((v,w)->{Insets b=w.getInsets(WindowInsets.Type.systemBars());
            root.setPadding(ModernUi.dp(this,17)+b.left,ModernUi.dp(this,17)+b.top,
                    ModernUi.dp(this,17)+b.right,b.bottom);return w;});setContentView(root);
        root.addView(ModernUi.text(this,"Karantina",25,ModernUi.TEXT,true));
        root.addView(ModernUi.muted(this,"Geri yükle veya gerçekten alan açmak için kalıcı sil",12),ModernUi.space(this,-1,-2,6));
        LinearLayout stat=ModernUi.card(this);root.addView(stat,ModernUi.space(this,-1,-2,16));
        summary=ModernUi.text(this,"Karantina okunuyor…",24,ModernUi.TEXT,true);stat.addView(summary);
        sub=ModernUi.muted(this,"",12);stat.addView(sub,ModernUi.space(this,-1,-2,6));
        stat.addView(ModernUi.text(this,"Karantinaya taşıma boş alan oluşturmaz.",11,ModernUi.AMBER,false),ModernUi.space(this,-1,-2,12));
        LinearLayout toolbar=ModernUi.row(this);root.addView(toolbar,ModernUi.space(this,-1,43,12));
        Button sorted=ModernUi.button(this,"Boyuta göre sırala",false);toolbar.addView(sorted,new LinearLayout.LayoutParams(0,-1,1));
        sorted.setOnClickListener(v->{sortLargest=!sortLargest;sorted.setText(sortLargest?"Tarihe göre sırala":"Boyuta göre sırala");applyFilter();});
        Button large=ModernUi.button(this,"Büyük dosyalar",false);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);lp.leftMargin=ModernUi.dp(this,8);
        toolbar.addView(large,lp);large.setOnClickListener(v->{filterLarge=!filterLarge;large.setText(filterLarge?"Tüm dosyalar":"Büyük dosyalar");applyFilter();});
        progress=ModernUi.muted(this,"Yükleniyor…",12);root.addView(progress,ModernUi.space(this,-1,-2,12));
        list=new ListView(this);list.setDivider(null);list.setDividerHeight(ModernUi.dp(this,7));
        list.setCacheColorHint(0);list.setBackgroundColor(ModernUi.BG);
        list.setAdapter(new EntriesAdapter());root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout actions=ModernUi.row(this);root.addView(actions,ModernUi.space(this,-1,49,8));
        select=ModernUi.button(this,"Tümünü seç",false);actions.addView(select,new LinearLayout.LayoutParams(0,-1,1));
        restore=ModernUi.button(this,"Geri yükle",false);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);
        p.leftMargin=ModernUi.dp(this,7);actions.addView(restore,p);
        delete=ModernUi.button(this,"Kalıcı sil",true);
        p=new LinearLayout.LayoutParams(0,-1,1);p.leftMargin=ModernUi.dp(this,7);actions.addView(delete,p);
        select.setOnClickListener(v->{if(checked.containsAll(ids(visible)))checked.clear();
            else for(TrashStore.Entry e:visible)checked.add(e.id);updateButtons();((BaseAdapter)list.getAdapter()).notifyDataSetChanged();});
        restore.setOnClickListener(v->confirmRestore());delete.setOnClickListener(v->confirmDelete());updateButtons();
    }
    private Set<String> ids(List<TrashStore.Entry> values){Set<String> out=new HashSet<>();for(TrashStore.Entry e:values)out.add(e.id);return out;}
    private void reload(){busy=true;updateButtons();progress.setText("Karantina yeniden okunuyor…");
        io.execute(()->{List<TrashStore.Entry> entries=manager.list();runOnUiThread(()->{
            if(isDestroyed())return;busy=false;all.clear();all.addAll(entries);
            checked.retainAll(ids(all));applyFilter();
        });});
    }
    private void applyFilter(){
        visible.clear();for(TrashStore.Entry e:all)if(!filterLarge||e.size>=5*1024*1024)visible.add(e);
        if(sortLargest)visible.sort((a,b)->Long.compare(b.size,a.size));
        else visible.sort((a,b)->Long.compare(b.timestamp,a.timestamp));
        long bytes=0;for(TrashStore.Entry e:all)bytes+=e.size;
        summary.setText(ModernUi.bytes(bytes));sub.setText(all.size()+" dosya · Kalıcı silinirse yaklaşık bu kadar alan açılabilir");
        int selected=0;long selectedBytes=0;for(TrashStore.Entry e:all)if(checked.contains(e.id)){selected++;selectedBytes+=e.size;}
        progress.setText(selected+" seçili · "+ModernUi.bytes(selectedBytes)+(filterLarge?" · 5 MB üzeri":""));
        ((BaseAdapter)list.getAdapter()).notifyDataSetChanged();updateButtons();
    }
    private void updateButtons(){int count=checked.size();
        if(restore==null)return;
        restore.setEnabled(!busy&&count>0);delete.setEnabled(!busy&&count>0);select.setEnabled(!busy&&!visible.isEmpty());
        restore.setAlpha(restore.isEnabled()?1f:.4f);delete.setAlpha(delete.isEnabled()?1f:.4f);
        select.setText(!visible.isEmpty()&&checked.containsAll(ids(visible))?"Seçimi kaldır":"Tümünü seç");
    }
    private ArrayList<TrashStore.Entry> selectedEntries(){ArrayList<TrashStore.Entry> r=new ArrayList<>();
        for(TrashStore.Entry e:all)if(checked.contains(e.id))r.add(e);return r;}
    private void confirmDelete(){ArrayList<TrashStore.Entry> items=selectedEntries();if(items.isEmpty())return;
        long bytes=0;for(TrashStore.Entry e:items)bytes+=e.size;
        new AlertDialog.Builder(this).setTitle("GERİ ALINAMAZ")
                .setMessage(items.size()+" öğe kalıcı olarak silinecek ("+ModernUi.bytes(bytes)+").\n\nÖnemli fotoğraflar, belgeler veya yedeklerin seçili olmadığını kontrol et. Geri almak mümkün olmayabilir.")
                .setNegativeButton("Vazgeç",null).setPositiveButton("Kalıcı sil",(d,w)->purge(items)).show();
    }
    private void purge(List<TrashStore.Entry> items){busy=true;updateButtons();progress.setText("Dosyalar tek tek doğrulanıyor ve siliniyor…");
        io.execute(()->{
            long before=primaryFree();
            List<TrashStore.PurgeResult> outcomes=manager.purgeBatch(items);
            long after=primaryFree();
            int success=0;long bytes=0;StringBuilder errors=new StringBuilder();
            for(TrashStore.PurgeResult r:outcomes){
                if(r.deleted){success++;bytes+=r.entry.size;}
                else {
                    if(errors.length()<2400)errors.append("• ").append(r.entry==null?"Bilinmeyen":r.entry.original.getName())
                            .append(": ").append(r.reason).append('\n');
                    if(errors.length()<1600)ActionLog.add(this,"Silme hatası",(r.entry==null?"Bilinmeyen":r.entry.original.getName())+
                            ": "+r.reason);
                }
            }
            int passed=success,failed=items.size()-success;long freed=bytes;String detail=errors.toString();
            ActionLog.add(this,"Kalıcı silme",passed+" silindi · "+failed+" hata · "+ModernUi.bytes(freed));
            runOnUiThread(()->{if(isDestroyed())return;busy=false;checked.clear();reload();
                new AlertDialog.Builder(this).setTitle(failed==0?"Kalıcı silme tamamlandı":passed==0?"Kalıcı silme başarısız":"Kalıcı silme kısmen tamamlandı")
                        .setMessage(passed+" silindi · "+failed+" başarısız\n\nSilinen dosya boyutu: "+ModernUi.bytes(freed)
                                +(before>=0&&after>=0?"\nBirincil depolamada raporlanan değişim: "+
                                        (after-before>=0?"+":"−")+ModernUi.bytes(Math.abs(after-before)):"")
                                +(failed>0?"\n\nBaşarısız dosyalar:\n"+detail:"\n\nKarantina listesi yenilendi."))
                        .setPositiveButton("Tamam",null).show();});
        });
    }
    private long primaryFree(){
        try{return new android.os.StatFs(android.os.Environment.getExternalStorageDirectory().getAbsolutePath())
                .getAvailableBytes();}
        catch(Exception e){return -1;}
    }
    private void confirmRestore(){ArrayList<TrashStore.Entry> items=selectedEntries();if(items.isEmpty())return;
        new AlertDialog.Builder(this).setTitle("Geri yükle")
                .setMessage(items.size()+" öğe özgün klasörlerine döndürülecek. Hedefte aynı adlı dosya varsa üzerine yazılmayacak.")
                .setNegativeButton("Vazgeç",null).setPositiveButton("Geri yükle",(d,w)->restore(items)).show();
    }
    private void restore(List<TrashStore.Entry> items){busy=true;updateButtons();progress.setText("Dosyalar doğrulanıp geri yükleniyor…");
        io.execute(()->{
            java.util.Set<String> restoredIds=manager.restoreBatch(items);
            int ok=restoredIds.size();StringBuilder errors=new StringBuilder();
            for(TrashStore.Entry e:items) {
                if(!restoredIds.contains(e.id)&&errors.length()<1800)errors.append("• ").append(e.original.getName())
                        .append(" (orijinal yol yok, çakışma veya erişim sorunu)\n");
            }
            int done=ok;String detail=errors.toString();ActionLog.add(this,"Geri yükleme",done+" başarı · "+(items.size()-done)+" hata");
            runOnUiThread(()->{if(isDestroyed())return;busy=false;checked.clear();reload();
                new AlertDialog.Builder(this).setTitle("Geri yükleme sonucu")
                        .setMessage(done+" geri yüklendi · "+(items.size()-done)+" başarısız\n"+detail)
                        .setPositiveButton("Tamam",null).show();});
        });
    }
    private final class EntriesAdapter extends BaseAdapter {
        public int getCount(){return visible.size();}
        public Object getItem(int i){return visible.get(i);}
        public long getItemId(int i){return i;}
        public View getView(int position,View reused,ViewGroup parent){
            TrashStore.Entry e=visible.get(position);
            LinearLayout box=ModernUi.row(QuarantineActivity.this);box.setPadding(ModernUi.dp(QuarantineActivity.this,11),
                    ModernUi.dp(QuarantineActivity.this,12),ModernUi.dp(QuarantineActivity.this,11),ModernUi.dp(QuarantineActivity.this,12));
            box.setBackground(ModernUi.outlined(QuarantineActivity.this,ModernUi.PANEL,15));
            CheckBox check=new CheckBox(QuarantineActivity.this);check.setChecked(checked.contains(e.id));
            check.setButtonTintList(ColorStateList.valueOf(ModernUi.TEAL));
            box.addView(check,new LinearLayout.LayoutParams(ModernUi.dp(QuarantineActivity.this,42),ModernUi.dp(QuarantineActivity.this,44)));
            LinearLayout text=ModernUi.column(QuarantineActivity.this);
            box.addView(text,new LinearLayout.LayoutParams(0,-2,1));
            TextView name=ModernUi.text(QuarantineActivity.this,e.original.getName(),14,ModernUi.TEXT,true);
            name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);text.addView(name);
            text.addView(ModernUi.muted(QuarantineActivity.this,ModernUi.bytes(e.size)+" · "+
                    new java.text.SimpleDateFormat("dd.MM.yy HH:mm",Locale.getDefault()).format(new java.util.Date(e.timestamp)),11),
                    ModernUi.space(QuarantineActivity.this,-1,-2,5));
            TextView path=ModernUi.muted(QuarantineActivity.this,e.original.getParent(),10);
            path.setSingleLine(true);path.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            text.addView(path,ModernUi.space(QuarantineActivity.this,-1,-2,5));
            Runnable toggle=()->{if(!checked.add(e.id))checked.remove(e.id);applyFilter();};
            check.setOnClickListener(v->toggle.run());box.setOnClickListener(v->toggle.run());
            box.setOnLongClickListener(v->{new AlertDialog.Builder(QuarantineActivity.this).setTitle(e.original.getName())
                    .setMessage("Önceki konum:\n"+e.original.getPath()+"\n\nBoyut: "+ModernUi.bytes(e.size))
                    .setPositiveButton("Kapat",null).show();return true;});
            return box;
        }
    }
}
