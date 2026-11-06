package com.clearos.ultra;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Path;
import java.util.List;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.Locale;

/** Self-contained, offline design system for Android 11+; no network or theme dependency. */
public final class ModernUi {
    private ModernUi() { }
    public static final int BG=0xFF07111D, PANEL=0xFF121F30, PANEL2=0xFF1B2D41;
    public static final int TEXT=0xFFF1F6FB, MUTED=0xFF9EAFBF, TEAL=0xFF29D3BD;
    public static final int AMBER=0xFFFFC879, RED=0xFFFF7A86, BLUE=0xFF8EA9FF;
    public static final int BORDER=0xFF273A4C, BLACK=0xFF061520;
    public static int dp(Activity c,float value) {return (int)(value*c.getResources().getDisplayMetrics().density+.5f);}
    public static GradientDrawable shape(Activity c,int color,int radius) {
        GradientDrawable b=new GradientDrawable();b.setColor(color);b.setCornerRadius(dp(c,radius));return b;
    }
    public static GradientDrawable outlined(Activity c,int color,int radius) {
        GradientDrawable b=shape(c,color,radius);b.setStroke(dp(c,1),BORDER);return b;
    }
    public static LinearLayout column(Activity c) {
        LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);return v;
    }
    public static LinearLayout row(Activity c) {
        LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);return v;
    }
    public static LinearLayout.LayoutParams space(Activity c,int w,int h,int top) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w<0?w:dp(c,w),h<0?h:dp(c,h));
        p.topMargin=dp(c,top);return p;
    }
    public static TextView text(Activity c,String value,int size,int color,boolean bold) {
        TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setFontFeatureSettings("kern");t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        t.setIncludeFontPadding(false);return t;
    }
    public static TextView muted(Activity c,String value,int size) {return text(c,value,size,MUTED,false);}
    public static LinearLayout card(Activity c) {
        LinearLayout v=column(c);v.setPadding(dp(c,18),dp(c,17),dp(c,18),dp(c,18));
        v.setBackground(outlined(c,PANEL,20));return v;
    }
    public static Button button(Activity c,String name,boolean primary) {
        Button b=new Button(c);b.setText(name);b.setAllCaps(false);
        b.setTextSize(13);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        b.setTextColor(primary?BLACK:TEXT);b.setBackground(shape(c,primary?TEAL:PANEL2,14));
        b.setStateListAnimator(null);b.setPadding(dp(c,9),0,dp(c,9),0);b.setMinHeight(0);b.setMinimumHeight(0);
        return b;
    }
    public static LinearLayout section(Activity c,String title,String subtitle) {
        LinearLayout v=column(c);v.addView(text(c,title,19,TEXT,true));
        if(subtitle!=null&&!subtitle.isEmpty())v.addView(muted(c,subtitle,12),space(c,-1,-2,6));
        return v;
    }
    public static void attach(LinearLayout parent,View v,Activity c,int top) {
        parent.addView(v,space(c,-1,-2,top));
    }
    public static TextView badge(Activity c,String value,int color) {
        TextView b=text(c,value,11,color,true);b.setPadding(dp(c,9),dp(c,5),dp(c,9),dp(c,5));
        b.setBackground(shape(c,PANEL2,10));return b;
    }
    public static LinearLayout actionCard(Activity c,String icon,String title,String detail,Runnable onClick) {
        LinearLayout frame=row(c);frame.setPadding(dp(c,15),dp(c,14),dp(c,13),dp(c,14));
        frame.setBackground(outlined(c,PANEL,18));
        TextView symbol=text(c,icon,21,TEAL,true);symbol.setGravity(Gravity.CENTER);
        symbol.setBackground(shape(c,PANEL2,12));frame.addView(symbol,new LinearLayout.LayoutParams(dp(c,44),dp(c,44)));
        LinearLayout labels=column(c);LinearLayout.LayoutParams pl=new LinearLayout.LayoutParams(0,-2,1);
        pl.leftMargin=dp(c,13);frame.addView(labels,pl);
        labels.addView(text(c,title,14,TEXT,true));labels.addView(muted(c,detail,11),space(c,-1,-2,5));
        frame.addView(text(c,"›",25,MUTED,false));
        frame.setOnClickListener(v->onClick.run());return frame;
    }
    public static String bytes(long bytes) {
        if(bytes<0)return "—";
        if(bytes<1024)return bytes+" B";
        String[] u={"KB","MB","GB","TB"};int i=-1;double n=bytes;
        do {n/=1024;i++;} while(n>=1024&&i<u.length-1);
        return String.format(Locale.getDefault(),"%.1f %s",n,u[i]);
    }
    public static ProgressBar progress(Activity c,int percentage) {
        ProgressBar bar=new ProgressBar(c,null,android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);bar.setProgress(Math.max(0,Math.min(1000,percentage)));
        bar.setProgressTintList(ColorStateList.valueOf(TEAL));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(PANEL2));return bar;
    }
    public static final class NavIcon extends View {
        private final int kind;private final boolean active;private final Paint p=new Paint(3);
        public NavIcon(Activity a,int icon,boolean selected){super(a);kind=icon;active=selected;}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float d=getResources().getDisplayMetrics().density;
            canvas.save();canvas.translate((getWidth()-24*d)/2,(getHeight()-24*d)/2);canvas.scale(d,d);
            p.setColor(active?TEAL:MUTED);p.setStrokeWidth(1.85f);p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeCap(Paint.Cap.ROUND);p.setStyle(Paint.Style.STROKE);
            Path path=new Path();
            switch(kind){
                case 0:
                    path.moveTo(3,10);path.lineTo(12,3);path.lineTo(21,10);path.moveTo(5.5f,9);
                    path.lineTo(5.5f,21);path.lineTo(18.5f,21);path.lineTo(18.5f,9);
                    path.moveTo(10,21);path.lineTo(10,14);path.lineTo(14,14);path.lineTo(14,21);break;
                case 1:
                    path.moveTo(6,3);path.lineTo(18,3);path.moveTo(6,7);path.lineTo(18,7);
                    path.moveTo(4,11);path.lineTo(20,11);path.moveTo(4,11);path.lineTo(6,21);
                    path.lineTo(18,21);path.lineTo(20,11);path.moveTo(9,15);path.lineTo(15,15);break;
                case 2:
                    path.moveTo(3,4);path.lineTo(21,4);path.lineTo(21,20);path.lineTo(3,20);path.close();
                    path.moveTo(6,16);path.lineTo(10,11);path.lineTo(13,14);path.lineTo(16,10);path.lineTo(20,17);break;
                case 3:
                    path.moveTo(4,20);path.lineTo(4,4);path.moveTo(4,20);path.lineTo(21,20);
                    path.moveTo(7,16);path.lineTo(11,12);path.lineTo(14,14);path.lineTo(20,6);break;
                default:
                    path.moveTo(12,3);path.lineTo(12,6);path.moveTo(12,18);path.lineTo(12,21);
                    path.moveTo(3,12);path.lineTo(6,12);path.moveTo(18,12);path.lineTo(21,12);
                    path.moveTo(5.5f,5.5f);path.lineTo(7.5f,7.5f);
                    path.moveTo(16.5f,16.5f);path.lineTo(18.5f,18.5f);
                    path.moveTo(18.5f,5.5f);path.lineTo(16.5f,7.5f);
                    path.moveTo(7.5f,16.5f);path.lineTo(5.5f,18.5f);
                    canvas.drawCircle(12,12,4,p);break;
            }
            canvas.drawPath(path,p);canvas.restore();
        }
    }
    public static final class Trend extends View {
        private final List<StorageHistory.Reading> points;private final Paint paint=new Paint(3);
        public Trend(Activity activity,List<StorageHistory.Reading> readings){super(activity);points=readings;}
        @Override protected void onDraw(Canvas canvas){
            if(points.size()<2)return;
            float d=getResources().getDisplayMetrics().density;
            float left=12*d,top=12*d,right=getWidth()-12*d,bottom=getHeight()-14*d;
            long max=Long.MIN_VALUE,min=Long.MAX_VALUE;
            for(StorageHistory.Reading r:points){max=Math.max(max,r.used);min=Math.min(min,r.used);}
            long span=Math.max(1,max-min);long padding=Math.max(1,span/8);span+=2*padding;min-=padding;
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d);paint.setColor(BORDER);
            for(int i=0;i<=3;i++){
                float y=top+(bottom-top)*i/3f;canvas.drawLine(left,y,right,y,paint);
            }
            paint.setColor(TEAL);paint.setStrokeWidth(2.5f*d);paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeCap(Paint.Cap.ROUND);
            Path path=new Path();int n=points.size();
            for(int i=0;i<n;i++){
                float x=left+(right-left)*i/(n-1f);
                float y=bottom-(float)(points.get(i).used-min)*(bottom-top)/span;
                if(i==0)path.moveTo(x,y);else path.lineTo(x,y);
            }
            canvas.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);
            float lastY=bottom-(float)(points.get(n-1).used-min)*(bottom-top)/span;
            canvas.drawCircle(right,lastY,3.5f*d,paint);
        }
    }
    public static final class Donut extends View {
        private final Paint paint=new Paint(3);private float fraction;private final String caption;
        public Donut(Activity activity,float used,String text) {
            super(activity);fraction=Math.max(0,Math.min(1,used));caption=text;
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float midX=getWidth()/2f,midY=getHeight()/2f;
            float radius=Math.min(getWidth(),getHeight())*.39f;
            float width=Math.max(10,radius*.19f);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(width);paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(PANEL2);RectF bounds=new RectF(midX-radius,midY-radius,midX+radius,midY+radius);
            canvas.drawArc(bounds,-90,360,false,paint);
            paint.setColor(TEAL);canvas.drawArc(bounds,-90,360*fraction,false,paint);
            paint.setStyle(Paint.Style.FILL);paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(TEXT);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
            paint.setTextSize(radius*.43f);canvas.drawText(caption,midX,midY+radius*.12f,paint);
            paint.setColor(MUTED);paint.setTypeface(Typeface.DEFAULT);paint.setTextSize(radius*.18f);
            canvas.drawText("Kullanılan",midX,midY+radius*.42f,paint);
        }
    }
}
