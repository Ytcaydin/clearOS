package com.clearos.ultra;

import android.app.Activity;
import android.content.res.AssetManager;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.List;
import java.util.Locale;

/**
 * Offline design system — Nothing OS inspired: pure black, white type, one red accent,
 * dot-matrix headings (Doto), mono labels (Space Mono), grotesk body (Space Grotesk).
 * Fonts are bundled by CI under assets/fonts; when missing, system fonts are used.
 */
public final class ModernUi {
    private ModernUi() { }

    // Surfaces & text
    public static final int BG=0xFF000000, PANEL=0xFF0B0B0B, PANEL2=0xFF161616;
    public static final int BORDER=0xFF262626, DIVIDER=0xFF1F1F1F, BLACK=0xFF000000;
    public static final int TEXT=0xFFFFFFFF, MUTED=0xFFA3A3A3, FAINT=0xFF6B6B6B;
    /** Nothing red. For dots and fills; text uses ACCENT_TEXT for contrast on black. */
    public static final int ACCENT=0xFFD71921, ACCENT_TEXT=0xFFFF5A5A;
    // Legacy role names used across screens, mapped onto the monochrome palette.
    public static final int TEAL=TEXT;          // primary / success
    public static final int AMBER=ACCENT_TEXT;  // attention
    public static final int RED=ACCENT_TEXT;    // danger
    public static final int BLUE=0xFFD4D4D4;    // info

    // ---------------------------------------------------------------- fonts
    private static Typeface dot, mono, monoBold, body, bodyMedium, bodyBold, bodyLight;
    private static boolean fontsLoaded;

    private static synchronized void fonts(Activity c) {
        if(fontsLoaded)return;
        fontsLoaded=true;
        AssetManager a=c.getAssets();
        dot=variable(a,"fonts/Doto.ttf","'wght' 900, 'ROND' 100");
        mono=file(a,"fonts/SpaceMono-Regular.ttf");
        monoBold=file(a,"fonts/SpaceMono-Bold.ttf");
        body=variable(a,"fonts/SpaceGrotesk.ttf","'wght' 400");
        bodyMedium=variable(a,"fonts/SpaceGrotesk.ttf","'wght' 500");
        bodyBold=variable(a,"fonts/SpaceGrotesk.ttf","'wght' 600");
        bodyLight=variable(a,"fonts/SpaceGrotesk.ttf","'wght' 300");
        Typeface sans=Typeface.create("sans-serif",Typeface.NORMAL);
        if(mono==null)mono=Typeface.MONOSPACE;
        if(monoBold==null)monoBold=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD);
        if(dot==null)dot=monoBold;
        if(body==null)body=sans;
        if(bodyMedium==null)bodyMedium=Typeface.create("sans-serif-medium",Typeface.NORMAL);
        if(bodyBold==null)bodyBold=Typeface.create("sans-serif",Typeface.BOLD);
        if(bodyLight==null)bodyLight=Typeface.create("sans-serif-light",Typeface.NORMAL);
    }
    private static Typeface file(AssetManager a,String path) {
        try {return Typeface.createFromAsset(a,path);} catch(RuntimeException e){return null;}
    }
    private static Typeface variable(AssetManager a,String path,String axes) {
        try {
            Typeface t=new Typeface.Builder(a,path).setFontVariationSettings(axes).build();
            return t;
        } catch(RuntimeException e){return null;}
    }
    public static Typeface dotFont(Activity c){fonts(c);return dot;}
    public static Typeface monoFont(Activity c,boolean bold){fonts(c);return bold?monoBold:mono;}
    public static Typeface bodyFont(Activity c,boolean bold){fonts(c);return bold?bodyBold:body;}

    // ---------------------------------------------------------------- basics
    public static int dp(Activity c,float value) {return (int)(value*c.getResources().getDisplayMetrics().density+.5f);}
    public static GradientDrawable shape(Activity c,int color,int radius) {
        GradientDrawable b=new GradientDrawable();b.setColor(color);b.setCornerRadius(dp(c,radius));return b;
    }
    public static GradientDrawable outlined(Activity c,int color,int radius) {
        GradientDrawable b=shape(c,color,radius);b.setStroke(dp(c,1),BORDER);return b;
    }
    /** Transparent background with a 1dp divider along the bottom edge. */
    public static Drawable bottomDivider(Activity c) {
        GradientDrawable line=new GradientDrawable();line.setColor(0);line.setStroke(dp(c,1),DIVIDER);
        int off=-dp(c,2);
        return new LayerDrawable(new Drawable[]{new InsetDrawable(line,off,off,off,0)});
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

    // ---------------------------------------------------------------- type
    /** Body text in Space Grotesk; bold = semibold. */
    public static TextView text(Activity c,String value,int size,int color,boolean bold) {
        fonts(c);
        TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setTypeface(bold?bodyBold:body);t.setIncludeFontPadding(false);
        t.setLineSpacing(0,1.12f);return t;
    }
    public static TextView muted(Activity c,String value,int size) {return text(c,value,size,MUTED,false);}
    /** Uppercase mono label: "DEPOLAMA", "SON TARAMA". */
    public static TextView label(Activity c,String value,int size,int color) {
        fonts(c);
        TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setTypeface(mono);t.setAllCaps(true);t.setLetterSpacing(.14f);t.setIncludeFontPadding(false);return t;
    }
    /** Dot-matrix screen title. */
    public static TextView pageTitle(Activity c,String value) {
        fonts(c);
        TextView t=new TextView(c);t.setText(value);t.setTextSize(28);t.setTextColor(TEXT);
        t.setTypeface(dot);t.setAllCaps(true);t.setLetterSpacing(.1f);t.setIncludeFontPadding(false);
        t.setContentDescription(value);return t;
    }
    /** Large light figure for hero numbers ("12,4"). */
    public static TextView figure(Activity c,String value,int size,int color) {
        fonts(c);
        TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setTypeface(bodyLight);t.setLetterSpacing(-.03f);t.setIncludeFontPadding(false);return t;
    }

    // ---------------------------------------------------------------- components
    /** Screen header: back chevron + dot-matrix title, mono subtitle. */
    public static LinearLayout header(Activity c,String title,String subtitle) {
        LinearLayout v=column(c);
        LinearLayout r=row(c);
        LineIcon back=new LineIcon(c,"‹",TEXT);back.setClickable(true);back.setFocusable(true);
        back.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);back.setContentDescription("Geri");
        back.setOnClickListener(x->c.finish());
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(c,44),dp(c,44));bp.leftMargin=-dp(c,12);
        r.addView(back,bp);
        TextView t=pageTitle(c,title);t.setTextSize(24);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.leftMargin=dp(c,2);r.addView(t,tp);
        v.addView(r);
        if(subtitle!=null&&!subtitle.isEmpty()){TextView sub=muted(c,subtitle,12);
            v.addView(sub,space(c,-1,-2,8));}
        return v;
    }
    public static Button button(Activity c,String name,boolean primary) {
        fonts(c);
        Button b=new Button(c);b.setText(name);b.setAllCaps(true);
        b.setTextSize(13);b.setTypeface(monoBold);b.setLetterSpacing(.1f);
        b.setTextColor(primary?BLACK:TEXT);
        GradientDrawable bg=shape(c,primary?TEXT:0x00000000,28);
        if(!primary)bg.setStroke(dp(c,1),0xFF3A3A3A);
        b.setBackground(bg);
        b.setStateListAnimator(null);b.setPadding(dp(c,16),0,dp(c,16),0);
        // 48dp floor: comfortable touch target, consistent pill height across screens.
        b.setMinHeight(dp(c,48));b.setMinimumHeight(dp(c,48));
        return b;
    }
    public static LinearLayout card(Activity c) {
        LinearLayout v=column(c);v.setPadding(dp(c,18),dp(c,16),dp(c,18),dp(c,16));
        v.setBackground(outlined(c,PANEL,18));return v;
    }
    /** Section header: small mono label + optional muted subtitle. */
    public static LinearLayout section(Activity c,String title,String subtitle) {
        LinearLayout v=column(c);v.setPadding(dp(c,2),0,dp(c,2),0);
        v.addView(label(c,title,11,MUTED));
        if(subtitle!=null&&!subtitle.isEmpty())v.addView(muted(c,subtitle,12),space(c,-1,-2,6));
        return v;
    }
    public static void attach(LinearLayout parent,View v,Activity c,int top) {
        parent.addView(v,space(c,-1,-2,top));
    }
    public static TextView badge(Activity c,String value,int color) {
        TextView b=label(c,value,10,color);b.setPadding(dp(c,9),dp(c,5),dp(c,9),dp(c,5));
        GradientDrawable bg=shape(c,0x00000000,12);bg.setStroke(dp(c,1),BORDER);b.setBackground(bg);return b;
    }
    /** List row: outlined circular line icon, title, detail, chevron, bottom divider. */
    public static LinearLayout actionCard(Activity c,String icon,String title,String detail,Runnable onClick) {
        LinearLayout frame=row(c);frame.setPadding(dp(c,2),dp(c,12),dp(c,4),dp(c,12));
        frame.setMinimumHeight(dp(c,66));frame.setBackground(bottomDivider(c));
        View symbol;
        if(icon!=null&&icon.length()==1&&Character.isDigit(icon.charAt(0))) {
            TextView n=label(c,icon,13,TEXT);n.setTypeface(monoBold);n.setGravity(Gravity.CENTER);symbol=n;
        } else symbol=new LineIcon(c,icon,TEXT);
        GradientDrawable ring=shape(c,0x00000000,20);ring.setStroke(dp(c,1),0xFF2A2A2A);symbol.setBackground(ring);
        frame.addView(symbol,new LinearLayout.LayoutParams(dp(c,40),dp(c,40)));
        LinearLayout labels=column(c);LinearLayout.LayoutParams pl=new LinearLayout.LayoutParams(0,-2,1);
        pl.leftMargin=dp(c,14);pl.rightMargin=dp(c,8);frame.addView(labels,pl);
        TextView t=text(c,title,15,TEXT,false);t.setTypeface(bodyMedium);labels.addView(t);
        labels.addView(muted(c,detail,12),space(c,-1,-2,4));
        frame.addView(new LineIcon(c,"›",FAINT),new LinearLayout.LayoutParams(dp(c,18),dp(c,18)));
        frame.setClickable(true);frame.setFocusable(true);
        frame.setContentDescription(title+". "+detail+". Açmak için iki kez dokun.");
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
        bar.setProgressTintList(ColorStateList.valueOf(TEXT));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(PANEL2));return bar;
    }

    // ---------------------------------------------------------------- drawn views

    /** Stroke icons on a 24-unit grid, replacing the old text glyphs. */
    public static final class LineIcon extends View {
        private final String kind;private final int color;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        public LineIcon(Activity a,String glyph,int color){super(a);kind=glyph==null?"":glyph;this.color=color;
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float s=Math.min(getWidth(),getHeight())/24f*("›".equals(kind)||"‹".equals(kind)?1f:.5f);
            canvas.save();canvas.translate((getWidth()-24*s)/2,(getHeight()-24*s)/2);canvas.scale(s,s);
            p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.7f);
            p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            Path h=new Path();
            switch(kind){
                case "♻": // trash
                    h.moveTo(4,7);h.lineTo(20,7);h.moveTo(9,7);h.lineTo(9,4);h.lineTo(15,4);h.lineTo(15,7);
                    h.moveTo(6,7);h.lineTo(7,20);h.lineTo(17,20);h.lineTo(18,7);h.moveTo(10,11);h.lineTo(10,16);h.moveTo(14,11);h.lineTo(14,16);break;
                case "▤": // bars
                    h.moveTo(4,20);h.lineTo(4,11);h.moveTo(10,20);h.lineTo(10,4);h.moveTo(16,20);h.lineTo(16,13);h.moveTo(2,20);h.lineTo(22,20);break;
                case "▣": // image
                    h.addRoundRect(3,5,21,19,2,2,Path.Direction.CW);h.moveTo(21,16);h.lineTo(16,11);h.lineTo(8,19);
                    canvas.drawCircle(9,10,1.6f,p);break;
                case "▦": // apps grid
                    h.addRoundRect(4,4,10,10,1.5f,1.5f,Path.Direction.CW);h.addRoundRect(14,4,20,10,1.5f,1.5f,Path.Direction.CW);
                    h.addRoundRect(4,14,10,20,1.5f,1.5f,Path.Direction.CW);h.addRoundRect(14,14,20,20,1.5f,1.5f,Path.Direction.CW);break;
                case "▸": // video
                    h.addRoundRect(3,6,16,18,2,2,Path.Direction.CW);h.moveTo(16,10);h.lineTo(21,7);h.lineTo(21,17);h.lineTo(16,14);break;
                case "◇": // package
                    h.moveTo(12,3);h.lineTo(20,7.5f);h.lineTo(20,16.5f);h.lineTo(12,21);h.lineTo(4,16.5f);h.lineTo(4,7.5f);h.close();
                    h.moveTo(4,7.5f);h.lineTo(12,12);h.lineTo(20,7.5f);h.moveTo(12,12);h.lineTo(12,21);break;
                case "◉": // phone
                    h.addRoundRect(6,2,18,22,3,3,Path.Direction.CW);h.moveTo(10,18);h.lineTo(14,18);break;
                case "◷": // clock
                    canvas.drawCircle(12,12,9,p);h.moveTo(12,7);h.lineTo(12,12);h.lineTo(15.5f,14);break;
                case "⚡": // bolt
                    h.moveTo(13,2);h.lineTo(4,14);h.lineTo(11,14);h.lineTo(10,22);h.lineTo(19,10);h.lineTo(12,10);h.close();break;
                case "✓":
                    h.moveTo(5,12.5f);h.lineTo(10,17.5f);h.lineTo(19,7);break;
                case "≡": // log
                    h.moveTo(5,6);h.lineTo(19,6);h.moveTo(5,12);h.lineTo(19,12);h.moveTo(5,18);h.lineTo(14,18);break;
                case "⌕": // search
                    canvas.drawCircle(11,11,6.5f,p);h.moveTo(16,16);h.lineTo(20.5f,20.5f);break;
                case "!": // alert
                    h.moveTo(12,3);h.lineTo(22,20);h.lineTo(2,20);h.close();h.moveTo(12,9);h.lineTo(12,14);
                    p.setStyle(Paint.Style.FILL);canvas.drawCircle(12,17,1.1f,p);p.setStyle(Paint.Style.STROKE);break;
                case "›":
                    p.setStrokeWidth(2f);h.moveTo(9,6);h.lineTo(15,12);h.lineTo(9,18);break;
                case "‹":
                    p.setStrokeWidth(1.8f);h.moveTo(15,6);h.lineTo(9,12);h.lineTo(15,18);break;
                default:
                    p.setStyle(Paint.Style.FILL);canvas.drawCircle(12,12,3,p);break;
            }
            canvas.drawPath(h,p);canvas.restore();
        }
    }

    /** Bottom navigation icons; active is white, inactive grey. */
    public static final class NavIcon extends View {
        private final int kind;private final boolean active;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        public NavIcon(Activity a,int icon,boolean selected){super(a);kind=icon;active=selected;}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float d=getResources().getDisplayMetrics().density;
            canvas.save();canvas.translate((getWidth()-24*d)/2,(getHeight()-24*d)/2);canvas.scale(d,d);
            p.setColor(active?TEXT:MUTED);p.setStrokeWidth(1.7f);p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeCap(Paint.Cap.ROUND);p.setStyle(Paint.Style.STROKE);
            Path path=new Path();
            switch(kind){
                case 0: path.moveTo(3,11);path.lineTo(12,4);path.lineTo(21,11);path.lineTo(21,20);path.lineTo(3,20);path.close();break;
                case 1: path.moveTo(4,7);path.lineTo(20,7);path.moveTo(9,7);path.lineTo(9,4);path.lineTo(15,4);path.lineTo(15,7);
                        path.moveTo(6,7);path.lineTo(7,20);path.lineTo(17,20);path.lineTo(18,7);break;
                case 2: path.addRoundRect(3,5,21,19,2,2,Path.Direction.CW);path.moveTo(21,16);path.lineTo(16,11);path.lineTo(8,19);break;
                case 3: path.moveTo(4,20);path.lineTo(4,10);path.moveTo(10,20);path.lineTo(10,4);path.moveTo(16,20);path.lineTo(16,13);break;
                default: path.moveTo(14.7f,6.3f);path.cubicTo(13,4.6f,10.3f,4.7f,8.7f,6.4f);path.cubicTo(7.4f,7.8f,7.1f,9.9f,8,11.7f);
                        path.lineTo(3,18);path.lineTo(6,21);path.lineTo(12.3f,14.7f);path.cubicTo(14.1f,15.6f,16.2f,15.3f,17.6f,14);
                        path.cubicTo(19.3f,12.4f,19.4f,9.7f,17.7f,8);path.lineTo(15.2f,10.5f);path.lineTo(12.7f,8);path.close();break;
            }
            canvas.drawPath(path,p);canvas.restore();
        }
    }

    /** Two-row dot bar: filled dots = used fraction. */
    public static final class DotBar extends View {
        private final float fraction;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        public DotBar(Activity a,float used){super(a);fraction=Math.max(0,Math.min(1,used));
            setContentDescription("Depolama yüzde "+Math.round(fraction*100)+" dolu");}
        @Override protected void onMeasure(int w,int h){
            float d=getResources().getDisplayMetrics().density;
            setMeasuredDimension(MeasureSpec.getSize(w),(int)(17*d));
        }
        @Override protected void onDraw(Canvas canvas){
            float d=getResources().getDisplayMetrics().density, dot=6*d, gap=5*d;
            int cols=Math.max(1,(int)((getWidth()+gap)/(dot+gap)));int total=cols*2;
            int filled=Math.round(total*fraction);
            float extra=(getWidth()-(cols*dot+(cols-1)*gap))/2f;
            for(int i=0;i<total;i++){
                int r=i/cols,c=i%cols;
                p.setColor(i<filled?TEXT:0xFF2E2E2E);
                canvas.drawCircle(extra+c*(dot+gap)+dot/2,r*(dot+gap)+dot/2,dot/2,p);
            }
        }
    }

    public static final class Trend extends View {
        private final List<StorageHistory.Reading> points;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        public Trend(Activity activity,List<StorageHistory.Reading> readings){super(activity);points=readings;}
        @Override protected void onDraw(Canvas canvas){
            if(points.size()<2)return;
            float d=getResources().getDisplayMetrics().density;
            float left=12*d,top=12*d,right=getWidth()-12*d,bottom=getHeight()-14*d;
            long max=Long.MIN_VALUE,min=Long.MAX_VALUE;
            for(StorageHistory.Reading r:points){max=Math.max(max,r.used);min=Math.min(min,r.used);}
            long span=Math.max(1,max-min);long padding=Math.max(1,span/8);span+=2*padding;min-=padding;
            paint.setStyle(Paint.Style.FILL);paint.setColor(0xFF262626);
            for(int i=0;i<=3;i++){ // dotted grid
                float y=top+(bottom-top)*i/3f;
                for(float x=left;x<=right;x+=8*d)canvas.drawCircle(x,y,d,paint);
            }
            paint.setStyle(Paint.Style.STROKE);paint.setColor(TEXT);paint.setStrokeWidth(2f*d);
            paint.setStrokeJoin(Paint.Join.ROUND);paint.setStrokeCap(Paint.Cap.ROUND);
            Path path=new Path();int n=points.size();
            for(int i=0;i<n;i++){
                float x=left+(right-left)*i/(n-1f);
                float y=bottom-(float)(points.get(i).used-min)*(bottom-top)/span;
                if(i==0)path.moveTo(x,y);else path.lineTo(x,y);
            }
            canvas.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);paint.setColor(ACCENT);
            float lastY=bottom-(float)(points.get(n-1).used-min)*(bottom-top)/span;
            canvas.drawCircle(right,lastY,4f*d,paint);
        }
    }

    /** Dot ring: used fraction in white dots, the rest dark; dot-matrix percentage inside. */
    public static final class Donut extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final float fraction;private final String caption;
        private final Typeface heading, small;
        public Donut(Activity activity,float used,String text) {
            super(activity);fraction=Math.max(0,Math.min(1,used));caption=text;
            heading=dotFont(activity);small=monoFont(activity,false);
            setContentDescription("Depolamanın "+caption+" oranı kullanılıyor");
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float midX=getWidth()/2f,midY=getHeight()/2f;
            float radius=Math.min(getWidth(),getHeight())*.42f;
            int dots=40;float r=Math.max(2,radius*.075f);int filled=Math.round(dots*fraction);
            paint.setStyle(Paint.Style.FILL);
            for(int i=0;i<dots;i++){
                double a=-Math.PI/2+2*Math.PI*i/dots;
                paint.setColor(i<filled?TEXT:0xFF2E2E2E);
                canvas.drawCircle(midX+(float)(radius*Math.cos(a)),midY+(float)(radius*Math.sin(a)),r,paint);
            }
            paint.setTextAlign(Paint.Align.CENTER);paint.setColor(TEXT);paint.setTypeface(heading);
            paint.setTextSize(radius*.46f);canvas.drawText(caption,midX,midY+radius*.14f,paint);
            paint.setColor(MUTED);paint.setTypeface(small);paint.setTextSize(radius*.15f);paint.setLetterSpacing(.12f);
            canvas.drawText("KULLANILAN",midX,midY+radius*.45f,paint);paint.setLetterSpacing(0);
        }
    }

    /** Small filled status dot (red by default). */
    public static View dot(Activity c,int color,int sizeDp) {
        View v=new View(c);GradientDrawable g=shape(c,color,sizeDp);v.setBackground(g);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c,sizeDp),dp(c,sizeDp)));return v;
    }
    /** Solid black window background for dialogs/popups that inherit ColorDrawable. */
    public static ColorDrawable black(){return new ColorDrawable(BG);}
}
