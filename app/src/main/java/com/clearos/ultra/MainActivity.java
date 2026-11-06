package com.clearos.ultra;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Native Android UI, no ads, analytics, internet, root or third-party libraries. */
public class MainActivity extends Activity {
    private static final int BG = 0xFF09111D;
    private static final int CARD = 0xFF152436;
    private static final int FG = 0xFFEAF5FF;
    private static final int MUTED = 0xFFA9B8C8;
    private static final int ACCENT = 0xFF36D9BC;
    private static final int ORANGE = 0xFFFFC178;
    private static final int DANGER = 0xFFFF6B78;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancel = new AtomicBoolean(false);
    private final Set<String> selected = new HashSet<>();
    private final ArrayList<SafeScanner.Candidate> filtered = new ArrayList<>();
    private List<SafeScanner.Candidate> results = new ArrayList<>();
    private SafeScanner.Type chosen = null;
    private boolean working = false;
    private int jobId = 0;

    private LinearLayout categoryRow;
    private TextView capacity, storageBarLabel, stats, status, selection;
    private ProgressBar capacityBar, busyBar;
    private Button permissionButton, scanButton, recommendedButton, deleteButton, clearSelectionButton, cacheButton;
    private ListView list;
    private ResultAdapter adapter;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        renderUi();
        updateStorage();
        updatePermission();
    }

    @Override public void onResume() {
        super.onResume();
        if (permissionButton != null) updatePermission();
    }

    @Override protected void onDestroy() {
        cancel.set(true); jobId++;
        io.shutdownNow();
        super.onDestroy();
    }

    private GradientDrawable background(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private int dp(float value) { return (int)(getResources().getDisplayMetrics().density * value + .5f); }

    private TextView txt(String text, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text); v.setTextSize(sp); v.setTextColor(color);
        v.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return v;
    }

    private LinearLayout vertical() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }

    private Button button(String caption, int color) {
        Button b = new Button(this);
        b.setText(caption); b.setTextAllCaps(false);
        b.setTextColor(color == ACCENT ? BG : FG);
        b.setTextSize(12); b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setPadding(dp(7),dp(2),dp(7),dp(2));
        b.setBackground(background(color,12));
        return b;
    }

    private LinearLayout.LayoutParams lp(int width, int height, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, height);
        p.topMargin=dp(top); return p;
    }

    private void renderUi() {
        LinearLayout root = vertical();
        root.setBackgroundColor(BG);
        root.setPadding(dp(16),dp(12),dp(16),dp(8));
        root.setOnApplyWindowInsetsListener((v,insets) -> {
            Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
            root.setPadding(dp(16)+bars.left,dp(12)+bars.top,dp(16)+bars.right,dp(8)+bars.bottom);
            return insets;
        });
        setContentView(root);

        TextView title = txt("CLEAROS  ULTRA",23,FG,true);
        root.addView(title);
        TextView subtitle = txt("v3.1 • Güvenli tarama • %100 çevrimdışı",11,MUTED,false);
        root.addView(subtitle,lp(-1,-2,2));

        LinearLayout disk = vertical(); disk.setPadding(dp(14),dp(12),dp(14),dp(12));
        disk.setBackground(background(CARD,16)); root.addView(disk,lp(-1,-2,14));
        capacity=txt("Depolama alanı",19,FG,true); disk.addView(capacity);
        storageBarLabel=txt("",12,MUTED,false); disk.addView(storageBarLabel,lp(-1,-2,4));
        capacityBar = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        capacityBar.setMax(1000); capacityBar.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        capacityBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF304257));
        disk.addView(capacityBar,lp(-1,dp(9),10));

        LinearLayout first = new LinearLayout(this); first.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(first,lp(-1,dp(44),12));
        permissionButton=button("Dosya erişimi",CARD);
        first.addView(permissionButton,new LinearLayout.LayoutParams(0,-1,1));
        scanButton=button("Taramayı başlat",ACCENT);
        LinearLayout.LayoutParams space=new LinearLayout.LayoutParams(0,-1,1.4f); space.leftMargin=dp(8);
        first.addView(scanButton,space);
        permissionButton.setOnClickListener(v -> requestAllFiles());
        scanButton.setOnClickListener(v -> { if (working) cancelScan(); else beginScan(); });

        LinearLayout second = new LinearLayout(this); second.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(second,lp(-1,dp(37),8));
        cacheButton=button("Sistem önbelleği",CARD);
        second.addView(cacheButton,new LinearLayout.LayoutParams(0,-1,1));
        Button appSettings=button("Ultra 3.1 araçlar",CARD);
        LinearLayout.LayoutParams settingsParams=new LinearLayout.LayoutParams(0,-1,1);
        settingsParams.leftMargin=dp(8); second.addView(appSettings,settingsParams);
        cacheButton.setOnClickListener(v -> clearSystemCache());
        appSettings.setOnClickListener(v -> startActivity(new Intent(this,UltraFeaturesActivity.class)));

        status=txt("Erişim verip taramayı başlat.",12,MUTED,false);
        root.addView(status,lp(-1,-2,10));
        busyBar = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        busyBar.setIndeterminate(true); busyBar.setVisibility(View.GONE);
        root.addView(busyBar,lp(-1,dp(4),5));
        stats=txt("Dosyalar incelenmedi",13,FG,true); root.addView(stats,lp(-1,-2,12));

        HorizontalScrollView chipScroller = new HorizontalScrollView(this);
        chipScroller.setHorizontalScrollBarEnabled(false);
        categoryRow = new LinearLayout(this); categoryRow.setOrientation(LinearLayout.HORIZONTAL);
        chipScroller.addView(categoryRow);
        root.addView(chipScroller,lp(-1,dp(43),8));
        drawChips();

        LinearLayout action = new LinearLayout(this); action.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(action,lp(-1,dp(41),8));
        recommendedButton=button("Önerileri seç",CARD); action.addView(recommendedButton,new LinearLayout.LayoutParams(0,-1,1));
        clearSelectionButton=button("Seçimi kaldır",CARD);
        LinearLayout.LayoutParams clearP=new LinearLayout.LayoutParams(0,-1,1); clearP.leftMargin=dp(6);
        action.addView(clearSelectionButton,clearP);
        deleteButton=button("KARANTİNA",DANGER);
        LinearLayout.LayoutParams delP=new LinearLayout.LayoutParams(0,-1,.58f); delP.leftMargin=dp(6);
        action.addView(deleteButton,delP);
        recommendedButton.setOnClickListener(v -> {
            for (SafeScanner.Candidate c : results) if (c.suggested) selected.add(c.key());
            updateSelection(); adapter.notifyDataSetChanged();
        });
        clearSelectionButton.setOnClickListener(v -> {
            selected.clear(); updateSelection(); adapter.notifyDataSetChanged();
        });
        deleteButton.setOnClickListener(v -> confirmQuarantine());

        selection=txt("0 dosya seçili",12,ORANGE,true);
        root.addView(selection,lp(-1,-2,7));

        list = new ListView(this);
        list.setDividerHeight(dp(5)); list.setDivider(null);
        list.setCacheColorHint(Color.TRANSPARENT);
        list.setSelector(android.R.color.transparent);
        adapter = new ResultAdapter(); list.setAdapter(adapter);
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        list.setOnItemClickListener((parent,view,position,id) -> toggleItem(filtered.get(position)));

        TextView footer=txt("Geri alınabilir karantina yer açmaz • Kalıcı silme: Ultra araçlar",10,MUTED,false);
        footer.setGravity(Gravity.CENTER); root.addView(footer,lp(-1,-2,7));
        updateSelection();
    }

    private void updateStorage() {
        try {
            StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getAbsolutePath());
            long total=stat.getTotalBytes(), free=stat.getAvailableBytes(), used=total-free;
            capacity.setText("Kullanılan  " + size(used));
            storageBarLabel.setText("Toplam " + size(total) + "    •    Boş " + size(free));
            capacityBar.setProgress(total>0 ? (int)Math.min(1000,(used*1000.0)/total) : 0);
        } catch (Exception e) { capacity.setText("Depolama okunamadı"); }
    }

    private boolean permitted() { return Environment.isExternalStorageManager(); }

    private void updatePermission() {
        boolean enabled=permitted();
        permissionButton.setText(enabled ? "✓ İzin verildi" : "Dosya izni ver");
        if (!working && results.isEmpty())
            status.setText(enabled ? "Hazır. Tüm paylaşılan depolamayı tara." : "Derin tarama için 'Tüm dosyalara erişim' iznini aç.");
        cacheButton.setEnabled(enabled);
        cacheButton.setAlpha(enabled ? 1f : .45f);
    }

    private void requestAllFiles() {
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            openIntent(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
        }
    }

    private void openIntent(Intent intent) {
        try { startActivity(intent); }
        catch (Exception e) { show("Bu ayar telefonunda kullanılamıyor."); }
    }

    private void clearSystemCache() {
        if (!permitted()) { requestAllFiles(); return; }
        new AlertDialog.Builder(this)
                .setTitle("Sistem önbelleğini temizle")
                .setMessage("Android'in kendi onay ekranı açılır. Uygulamaların dış önbellekleri silinebilir ve sonradan tekrar oluşabilir. Pil/performans etkisi olabilir. Devam edilsin mi?")
                .setNegativeButton("Vazgeç",null)
                .setPositiveButton("Sistem ekranını aç",(d,w) -> {
                    try {
                        startActivityForResult(new Intent(StorageManager.ACTION_CLEAR_APP_CACHE),101);
                    } catch (Exception ex) {
                        show("Telefonun bu temizleme ekranını desteklemiyor. Depolama yönetimi açılıyor.");
                        openIntent(new Intent(StorageManager.ACTION_MANAGE_STORAGE));
                    }
                }).show();
    }

    private List<File> storageRoots() { return StorageRoots.available(this); }

    private void beginScan() {
        if (!permitted()) { requestAllFiles(); return; }
        if (working) return;
        results=new ArrayList<>(); selected.clear(); filtered.clear(); adapter.notifyDataSetChanged();
        working=true; cancel.set(false); int myJob=++jobId;
        scanButton.setText("Taramayı durdur"); busyBar.setVisibility(View.VISIBLE);
        status.setText("Paylaşılan depolama taranıyor…"); updateSelection();
        List<File> roots=storageRoots();
        android.content.SharedPreferences p=getSharedPreferences("excluded",0);
        ExcludedPaths exclusions=new ExcludedPaths(p.getBoolean("DCIM",false),
                p.getBoolean("Documents",false),p.getBoolean("Download",false));
        io.execute(() -> {
            SafeScanner.ScanResult r=SafeScanner.scan(roots,new SafeScanner.Progress() {
                @Override public boolean isCancelled() { return cancel.get() || Thread.currentThread().isInterrupted(); }
                @Override public boolean exclude(File file) {return exclusions.excludes(file);}
                @Override public void onUpdate(long count,String path) {
                    runOnUiThread(() -> {
                        if (jobId==myJob) status.setText(String.format(Locale.getDefault(),
                                "%s dosya tarandı • %s",String.format(Locale.getDefault(),"%,d",count),path));
                    });
                }
            });
            runOnUiThread(() -> {
                if (jobId!=myJob || isFinishing() || isDestroyed()) return;
                working=false; scanButton.setText("Yeniden tara"); busyBar.setVisibility(View.GONE);
                if (r.cancelled) {
                    status.setText("Tarama durduruldu. Tam sonuç için yeniden tara.");
                    return;
                }
                results=r.candidates;
                try { StorageHistory.record(this); } catch(Exception ignored) { }
                saveFolderMap(r.folderBytes);
                stats.setText(String.format(Locale.getDefault(),"%,d dosya incelendi • %,d sonuç • %s öneri",
                        r.visitedFiles,r.candidates.size(),size(r.suggestedBytes)));
                status.setText((r.limited ? "Tarama güvenlik sınırına ulaştı. " : "Tarama tamamlandı. ")
                        + (r.inaccessibleDirectories>0 ? r.inaccessibleDirectories+" klasöre erişilemedi." : "Dosyalar iznin olmadan silinmez."));
                applyFilter(); updateStorage();
            });
        });
    }

    private void saveFolderMap(java.util.Map<String, Long> folders) {
        ArrayList<java.util.Map.Entry<String, Long>> top=new ArrayList<>(folders.entrySet());
        top.sort((a,b)->Long.compare(b.getValue(),a.getValue()));
        StringBuilder readable=new StringBuilder();
        org.json.JSONArray data=new org.json.JSONArray();
        for(int i=0;i<Math.min(60,top.size());i++) {
            java.util.Map.Entry<String, Long> folder=top.get(i);
            try {
                org.json.JSONObject entry=new org.json.JSONObject();
                entry.put("path",folder.getKey());entry.put("bytes",folder.getValue());data.put(entry);
            }catch(org.json.JSONException ignored) { }
            if(i<20)readable.append(i+1).append(". ").append(folder.getKey())
                    .append("  •  ").append(size(folder.getValue())).append("\n");
        }
        getSharedPreferences("folder_map",0).edit()
                .putString("latest",readable.toString()).putString("json",data.toString()).apply();
    }

    private void cancelScan() { cancel.set(true); status.setText("Tarama durduruluyor…"); }

    private void drawChips() {
        categoryRow.removeAllViews();
        addChip("Tümü",null);
        for (SafeScanner.Type type : SafeScanner.Type.values()) addChip(type.label,type);
    }

    private void addChip(String caption,SafeScanner.Type type) {
        boolean active = chosen==type;
        TextView chip=txt(caption,11,active?BG:FG,active);
        chip.setGravity(Gravity.CENTER); chip.setPadding(dp(12),0,dp(12),0);
        chip.setBackground(background(active?ACCENT:CARD,13));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(35)); p.rightMargin=dp(6);
        categoryRow.addView(chip,p);
        chip.setOnClickListener(v -> { chosen=type; drawChips(); applyFilter(); });
    }

    private void applyFilter() {
        filtered.clear();
        for (SafeScanner.Candidate c : results) if (chosen==null || chosen==c.type) filtered.add(c);
        adapter.notifyDataSetChanged(); updateSelection();
    }

    private void toggleItem(SafeScanner.Candidate c) {
        if (working) return;
        if (!selected.add(c.key())) selected.remove(c.key());
        updateSelection(); adapter.notifyDataSetChanged();
    }

    private void updateSelection() {
        long bytes=0; int count=0;
        for (SafeScanner.Candidate c : results) if (selected.contains(c.key())) { bytes+=c.size; count++; }
        if (selection!=null) selection.setText(count + " seçili • " + size(bytes) + " karantinaya taşınabilir (yer açılmaz)");
        if (deleteButton!=null) { deleteButton.setEnabled(count>0 && !working); deleteButton.setAlpha(count>0 && !working?1f:.45f); }
    }

    private void confirmQuarantine() {
        if (working || selected.isEmpty()) return;
        ArrayList<SafeScanner.Candidate> targets=new ArrayList<>();
        long bytes=0;
        for (SafeScanner.Candidate c:results) if (selected.contains(c.key())) { targets.add(c); bytes+=c.size; }
        if (targets.isEmpty()) return;
        // Never delete both the protected original and its duplicate in one operation.
        for (SafeScanner.Candidate c : targets) {
            if (c.type==SafeScanner.Type.DUPLICATE && c.duplicateKeep!=null
                    && selected.contains(c.duplicateKeep.getAbsolutePath())) {
                show("Birbirinin aynısı olan dosyaların hem kopyası hem korunacak aslı seçilmiş. "
                        + "En az bir kopya bırakmak için korunan dosyanın seçimini kaldır.");
                return;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle("Güvenli karantinaya taşı")
                .setMessage(targets.size()+" öğe ("+size(bytes)+") uygulama karantinasına taşınacak."
                        +"\n\nKarantina depolama alanını hemen boşaltmaz. Ultra araçlar üzerinden geri yükleyebilir veya ayrı onayla kalıcı silebilirsin."
                        +"\n\nKarantina, dosyayla aynı depolama biriminde .ClearOS_Quarantine klasörüne taşınır. Uygulama silinse de dosyalar kalabilir.")
                .setNegativeButton("Vazgeç",null)
                .setPositiveButton("Karantinaya taşı",(dialog,which)->moveTargets(targets)).show();
    }

    private void moveTargets(List<SafeScanner.Candidate> targets) {
        if (working) return;
        working=true; busyBar.setVisibility(View.VISIBLE); int myJob=++jobId;
        status.setText("Dosyalar doğrulanıp karantinaya taşınıyor…"); updateSelection();
        io.execute(() -> {
            int ok=0, failed=0; long bytes=0;
            Set<String> erased=new HashSet<>();
            QuarantineManager trash=new QuarantineManager(storageRoots());
            for (SafeScanner.Candidate c:targets) {
                try {
                    trash.move(c);
                    ok++; bytes+=c.size; erased.add(c.key());
                } catch(IOException | SecurityException e) {failed++;}
            }
            final int done=ok, errors=failed; final long freed=bytes;
            runOnUiThread(() -> {
                if (jobId!=myJob || isFinishing() || isDestroyed()) return;
                working=false; busyBar.setVisibility(View.GONE);
                selected.removeAll(erased);
                ArrayList<SafeScanner.Candidate> remaining=new ArrayList<>();
                for (SafeScanner.Candidate c:results) if (!erased.contains(c.key())) remaining.add(c);
                results=remaining; applyFilter(); updateStorage();
                status.setText(done+" öğe karantinada • "+size(freed)+" henüz diskten silinmedi");
                new AlertDialog.Builder(this).setTitle("Karantina sonucu")
                        .setMessage("Taşınan: "+done+"\nTaşınamayan: "+errors
                                +"\nKarantinada tutulan: "+size(freed)
                                +"\n\nGerçek boş alan artışı için Ultra araçlar > Karantina > Kalıcı sil kullan.")
                        .setPositiveButton("Tamam",null).show();
            });
        });
    }

    private void show(String text) {
        new AlertDialog.Builder(this).setMessage(text).setPositiveButton("Tamam",null).show();
    }

    private static String size(long bytes) {
        if (bytes<1024) return bytes+" B";
        String[] units={"KB","MB","GB","TB"};
        double d=bytes;
        int pos=-1;
        do { d/=1024.0; pos++; } while (d>=1024 && pos<units.length-1);
        return String.format(Locale.getDefault(),"%.1f %s",d,units[pos]);
    }

    private final class ResultAdapter extends BaseAdapter {
        @Override public int getCount() { return filtered.size(); }
        @Override public Object getItem(int position) { return filtered.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position,View convertView,ViewGroup parent) {
            Holder h;
            if (convertView == null) {
                LinearLayout row=new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(9),dp(9),dp(10),dp(9)); row.setBackground(background(CARD,12));
                CheckBox box = new CheckBox(MainActivity.this);
                box.setButtonTintList(android.content.res.ColorStateList.valueOf(ACCENT));
                row.addView(box,new LinearLayout.LayoutParams(dp(42),dp(43)));
                LinearLayout texts=vertical(); row.addView(texts,new LinearLayout.LayoutParams(0,-2,1));
                TextView name=txt("",13,FG,true); name.setSingleLine(true); name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                texts.addView(name);
                TextView desc=txt("",11,ORANGE,false); texts.addView(desc,lp(-1,-2,3));
                TextView path=txt("",10,MUTED,false); path.setSingleLine(true);
                path.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                texts.addView(path,lp(-1,-2,3));
                h=new Holder(box,name,desc,path); row.setTag(h); convertView=row;
            } else h=(Holder)convertView.getTag();
            SafeScanner.Candidate c=filtered.get(position);
            h.check.setOnCheckedChangeListener(null);
            h.check.setChecked(selected.contains(c.key()));
            h.check.setContentDescription("Seç: " + c.file.getName());
            h.check.setOnClickListener(v -> toggleItem(c));
            h.name.setText(c.file.getName());
            h.desc.setText(c.type.label + "  •  " + size(c.size) + "  •  " + RiskEngine.risk(c).label + (c.suggested ? "  •  ÖNERİ" : ""));
            h.path.setText(c.file.getParent() + "   ·   " + c.reason);
            return convertView;
        }
    }

    private static final class Holder {
        final CheckBox check;
        final TextView name,desc,path;
        Holder(CheckBox a,TextView b,TextView c,TextView d) { check=a;name=b;desc=c;path=d; }
    }
}
