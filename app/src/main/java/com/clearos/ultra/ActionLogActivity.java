package com.clearos.ultra;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Insets;
import android.os.Bundle;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/** Searchable diagnostics not sent outside the device. */
public final class ActionLogActivity extends Activity {
    @Override public void onCreate(Bundle state){super.onCreate(state);showLog();}
    private void showLog(){
        LinearLayout root=ModernUi.column(this);root.setBackgroundColor(ModernUi.BG);
        root.setPadding(ModernUi.dp(this,18),ModernUi.dp(this,20),ModernUi.dp(this,18),0);
        root.setOnApplyWindowInsetsListener((v,w)->{Insets b=w.getInsets(WindowInsets.Type.systemBars());
            root.setPadding(ModernUi.dp(this,18)+b.left,ModernUi.dp(this,20)+b.top,
                    ModernUi.dp(this,18)+b.right,b.bottom);return w;});setContentView(root);
        root.addView(ModernUi.text(this,"İşlem günlüğü",23,ModernUi.TEXT,true));
        root.addView(ModernUi.muted(this,"Yalnızca telefonda tutulur. Dosya içerikleri kaydedilmez.",12),ModernUi.space(this,-1,-2,8));
        ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout log=ModernUi.card(this);String data=ActionLog.read(this);
        log.addView(ModernUi.text(this,data.isEmpty()?"Henüz kaydedilmiş işlem yok.":data.replace("\n","\n\n"),12,ModernUi.TEXT,false));
        scroll.addView(log);
        LinearLayout buttons=ModernUi.row(this);root.addView(buttons,ModernUi.space(this,-1,48,10));
        android.widget.Button back=ModernUi.button(this,"Geri",false);buttons.addView(back,new LinearLayout.LayoutParams(0,-1,1));back.setOnClickListener(v->finish());
        android.widget.Button clear=ModernUi.button(this,"Geçmişi temizle",false);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.leftMargin=ModernUi.dp(this,8);buttons.addView(clear,p);
        clear.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Günlük silinsin mi?")
                .setMessage("Bu işlem karantinadaki dosyaları etkilemez.")
                .setNegativeButton("Vazgeç",null).setPositiveButton("Sil",(d,w)->{ActionLog.clear(this);showLog();}).show());
    }
}
