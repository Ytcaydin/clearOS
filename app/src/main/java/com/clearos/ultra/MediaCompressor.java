package com.clearos.ultra;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.database.Cursor;
import android.provider.OpenableColumns;

import androidx.media3.common.Effect;
import androidx.media3.transformer.Effects;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.effect.Presentation;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.Transformer;
import androidx.media3.transformer.DefaultEncoderFactory;
import androidx.media3.transformer.VideoEncoderSettings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.Collections;

/** Compression produces a separate verified copy. Source is never removed/replaced. */
public final class MediaCompressor {
    private MediaCompressor() {}
    public interface Listener {void success(String message);void error(String message);}
    private static long fileSize(Context c,Uri uri) {
        try(Cursor cursor=c.getContentResolver().query(uri,new String[]{OpenableColumns.SIZE},null,null,null)) {
            if(cursor!=null && cursor.moveToFirst())return cursor.getLong(0);
        }catch(Exception ignored){}
        return -1;
    }
    private static int readOrientation(ContentResolver resolver,Uri uri) {
        try(InputStream input=resolver.openInputStream(uri)) {
            if(input!=null) return new ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);
        }catch(Exception ignored){}
        return ExifInterface.ORIENTATION_NORMAL;
    }
    public static String compressPhoto(Context context,Uri uri) throws Exception {
        ContentResolver resolver=context.getContentResolver();
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
        try(InputStream input=resolver.openInputStream(uri)){if(input==null)throw new IOException("Resim okunamadı");BitmapFactory.decodeStream(input,null,options);}
        if(options.outWidth<1||options.outHeight<1)throw new IOException("Resim formatı desteklenmiyor");
        options.inJustDecodeBounds=false;options.inSampleSize=1;
        while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>2560)options.inSampleSize*=2;
        options.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap b;
        try(InputStream input=resolver.openInputStream(uri)){if(input==null)throw new IOException("Resim okunamadı");b=BitmapFactory.decodeStream(input,null,options);}
        if(b==null)throw new IOException("Resim çözümlenemedi");
        if(b.hasAlpha()){b.recycle();throw new IOException("Şeffaf görseller JPEG olarak dönüştürülmüyor");}
        int orientation=readOrientation(resolver,uri);
        Matrix m=new Matrix();
        switch(orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:m.postRotate(90);break;
            case ExifInterface.ORIENTATION_ROTATE_180:m.postRotate(180);break;
            case ExifInterface.ORIENTATION_ROTATE_270:m.postRotate(270);break;
            default:break;
        }
        if(!m.isIdentity()) {Bitmap rotated=Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),m,true);b.recycle();b=rotated;}
        File cache=File.createTempFile("clearos_photo_",".jpg",context.getCacheDir());
        try(FileOutputStream out=new FileOutputStream(cache)) {
            if(!b.compress(Bitmap.CompressFormat.JPEG,82,out))throw new IOException("JPEG oluşturulamadı");
            out.getFD().sync();
        } finally {b.recycle();}
        long previous=fileSize(context,uri);
        if(cache.length()<=0 || (previous>0 && cache.length()>=previous)) {
            cache.delete();throw new IOException("Dönüşüm daha küçük bir dosya oluşturmadı; orijinal korundu");
        }
        try {
            Uri saved=save(context,cache,"image/jpeg","Pictures/ClearOS Ultra","clearos_"+System.currentTimeMillis()+".jpg",false);
            return "Yeni JPEG kaydedildi: "+saved+"\nOrijinal korundu.\n"+
                    "Önce: "+ModernUi.bytes(previous)+" · Sonra: "+ModernUi.bytes(cache.length())+
                    (previous>0?" · Tasarruf: "+Math.max(0,(100L*(previous-cache.length())/previous))+"%":"");
        } finally {cache.delete();}
    }
    public static void compressVideo(Context context,Uri uri,Listener listener) {
        File output;
        int displayHeight;
        int displayWidth;
        int targetHeight;
        int bitrate;
        try {
            // Use source metadata to avoid upscaling a small video to 720p.
            MediaMetadataRetriever retriever=new MediaMetadataRetriever();
            int rotation=0;
            try {
                retriever.setDataSource(context,uri);
                String h=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                String w=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                String r=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
                if(h==null||w==null)throw new IOException("Video çözünürlüğü okunamadı");
                displayHeight=Integer.parseInt(h);displayWidth=Integer.parseInt(w);
                if(r!=null)try{rotation=Integer.parseInt(r);}catch(NumberFormatException ignored){}
            } finally {retriever.release();}
            if(displayHeight<=0||displayWidth<=0)throw new IOException("Video boyutu geçersiz");
            // Phone portrait clips are often stored landscape + 90/270° rotation. Media3 applies
            // the rotation before Presentation, so work in displayed (rotated) dimensions.
            if(rotation%180!=0){int t=displayHeight;displayHeight=displayWidth;displayWidth=t;}
            // "720p" = short side 720. Portrait 1080x1920 -> 720x1280, not 405x720.
            int shortSide=Math.min(displayWidth,displayHeight);
            double scale=shortSide>720 ? 720.0/shortSide : 1.0;
            targetHeight=((int)Math.round(displayHeight*scale))&~1;
            int targetWidth=((int)Math.round(displayWidth*scale))&~1;
            // Conservative 2.2 Mbps at 720p; scale by output pixel count, floor 0.5 Mbps.
            bitrate=Math.max(500_000,Math.min(2_200_000,
                    (int)(2_200_000.0*targetWidth*targetHeight/(1280*720))));
            File dir=context.getExternalFilesDir(Environment.DIRECTORY_MOVIES);
            if(dir==null)throw new IOException("Geçici video alanı yok");
            output=File.createTempFile("clearos_video_",".mp4",dir);
        } catch(Exception e) {listener.error("Video bilgisi okunamadı: "+e.getMessage());return;}
        // Re-encode at no more than source height and capped 720p; never alter the original.
        EditedMediaItem.Builder mediaBuilder=new EditedMediaItem.Builder(MediaItem.fromUri(uri));
        if(targetHeight<displayHeight) {
            mediaBuilder.setEffects(new Effects(Collections.emptyList(),
                    Collections.<Effect>singletonList(Presentation.createForHeight(targetHeight))));
        }
        EditedMediaItem edit=mediaBuilder.build();
        try {
            Transformer transcode=new Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setEncoderFactory(new DefaultEncoderFactory.Builder(context)
                            .setRequestedVideoEncoderSettings(new VideoEncoderSettings.Builder()
                                    .setBitrate(bitrate).build()).build())
                    .addListener(new Transformer.Listener() {
                        @Override public void onCompleted(Composition composition,ExportResult result) {
                            try {
                                long previous=fileSize(context,uri);
                                if(output.length()<=0 || (previous>0 && output.length()>=previous)) {
                                    listener.error("Video küçülmedi; orijinal korundu, çıktı kaydedilmedi.");return;
                                }
                                Uri saved=save(context,output,"video/mp4","Movies/ClearOS Ultra",
                                        "clearos_video_"+System.currentTimeMillis()+".mp4",true);
                                listener.success("Yeni video: "+saved+"\nOrijinal korundu.\n"+
                                        "Önce: "+ModernUi.bytes(previous)+" · Sonra: "+ModernUi.bytes(output.length())+
                                        (previous>0?" · Tasarruf: "+Math.max(0,(100L*(previous-output.length())/previous))+"%":""));
                            }catch(Exception e){listener.error("Kayıt başarısız: "+e.getMessage());}
                            finally{output.delete();}
                        }
                        @Override public void onError(Composition composition,ExportResult result,ExportException e) {
                            output.delete();listener.error("Telefon video dönüşümünü desteklemedi: "+e.getMessage());
                        }
                    }).build();
            transcode.start(edit,output.getAbsolutePath());
        }catch(Exception e){output.delete();listener.error("Video dönüştürülemedi: "+e.getMessage());}
    }
    private static Uri save(Context context,File input,String mime,String relative,String name,boolean video) throws IOException {
        ContentResolver resolver=context.getContentResolver();
        ContentValues values=new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME,name);
        values.put(MediaStore.MediaColumns.MIME_TYPE,mime);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,relative);
        values.put(MediaStore.MediaColumns.IS_PENDING,1);
        Uri collection=video?MediaStore.Video.Media.EXTERNAL_CONTENT_URI:MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        Uri destination=resolver.insert(collection,values);
        if(destination==null)throw new IOException("Medya kaydı oluşturulamadı");
        try {
            try(InputStream in=new java.io.FileInputStream(input);OutputStream out=resolver.openOutputStream(destination,"w")) {
                if(out==null)throw new IOException("Medya hedefi açılamadı");
                byte[] buffer=new byte[65536];int n;
                while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
                out.flush();
            }
            long bytes=fileSize(context,destination);
            if(bytes!=input.length())throw new IOException("Kopya boyutu doğrulanamadı");
            values.clear();values.put(MediaStore.MediaColumns.IS_PENDING,0);
            resolver.update(destination,values,null,null);
            return destination;
        }catch(Exception e) {
            resolver.delete(destination,null,null);
            if(e instanceof IOException)throw (IOException)e;
            throw new IOException(e);
        }
    }
}
