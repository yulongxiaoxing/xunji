package cn.xunji.probe;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.webkit.WebResourceResponse;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.*;

/** Private proof images, immutable originals plus bounded previews. No OCR/network. */
final class GrowthImages {
    static final long MAX_BYTES=10L*1024*1024;
    private static final Object FILE_LOCK=new Object();
    private static final Map<String,Integer> LEASES=new HashMap<>();
    private final File directory;
    GrowthImages(Context context){directory=new File(context.getFilesDir(),"growth-proofs");}
    private File file(String id,boolean preview){if(!GrowthPolicy.imageId(id))throw new IllegalArgumentException("图片标识无效");return new File(directory,id+(preview?".preview":""));}
    JSONObject importImage(InputStream input,String name)throws Exception{
        String id=UUID.randomUUID().toString();reserve(id);File original=file(id,false);Bitmap bitmap=null;
        try{
            if(!directory.isDirectory()&&!directory.mkdirs())throw new IllegalArgumentException("无法准备图片保存目录");
            if(input==null)throw new IllegalArgumentException("无法读取所选图片");
            try(InputStream in=input;OutputStream out=new FileOutputStream(original)){
                byte[] buffer=new byte[16384];int n;long total=0;while((n=in.read(buffer))!=-1){total+=n;if(total>MAX_BYTES)throw new IllegalArgumentException("单张证明图片不能超过10MB");out.write(buffer,0,n);}
            }
            BitmapFactory.Options bounds=bounds(original);
            if(bounds.outWidth<=0||bounds.outHeight<=0||!Arrays.asList("image/jpeg","image/png","image/webp").contains(bounds.outMimeType))throw new IllegalArgumentException("请选择有效的JPG、PNG或WebP图片");
            if((long)bounds.outWidth*bounds.outHeight>24000000)throw new IllegalArgumentException("图片尺寸过大，请选择2400万像素以内的图片");
            BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>1600)options.inSampleSize*=2;
            bitmap=BitmapFactory.decodeFile(original.getPath(),options);if(bitmap==null)throw new IllegalArgumentException("图片无法解码，请换一张图片");
            try(OutputStream out=new FileOutputStream(file(id,true))){if(!bitmap.compress(bitmap.hasAlpha()?Bitmap.CompressFormat.PNG:Bitmap.CompressFormat.JPEG,90,out))throw new IOException("preview");}
            String label=name==null||name.trim().isEmpty()?"证明图片":name.trim();if(label.length()>200)label=label.substring(0,200);
            return new JSONObject().put("id",id).put("name",label);
        }catch(Exception|OutOfMemoryError error){delete(id);if(error instanceof IllegalArgumentException)throw (IllegalArgumentException)error;throw new IllegalArgumentException("图片读取失败，原有内容已保留，请换一张重试");}
        finally{if(bitmap!=null)bitmap.recycle();if(input!=null)try{input.close();}catch(Exception ignored){}}
    }
    private static BitmapFactory.Options bounds(File file){BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),options);return options;}
    boolean exists(String id){return GrowthPolicy.imageId(id)&&file(id,false).isFile()&&file(id,true).isFile();}
    void validate(JSONArray images)throws Exception{GrowthStore.validateAttachments(images);for(int i=0;i<images.length();i++)if(!exists(images.getJSONObject(i).getString("id")))throw new IllegalArgumentException("证明图片已不可用，请重新选择");}
    WebResourceResponse response(String id,boolean original){
        try{File image=file(id,!original);if(!exists(id))throw new IOException();return new WebResourceResponse(bounds(image).outMimeType,null,new FileInputStream(image));}
        catch(Exception e){return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));}
    }
    void reserve(String id){if(GrowthPolicy.imageId(id))synchronized(FILE_LOCK){LEASES.put(id,LEASES.containsKey(id)?LEASES.get(id)+1:1);}}
    void release(String id){synchronized(FILE_LOCK){Integer count=LEASES.get(id);if(count==null)return;if(count>1)LEASES.put(id,count-1);else LEASES.remove(id);}}
    void delete(String id){if(GrowthPolicy.imageId(id))synchronized(FILE_LOCK){release(id);if(!LEASES.containsKey(id)){file(id,false).delete();file(id,true).delete();}}}
    void prune(GrowthStore store)throws Exception{
        // Reference query and file decisions share the lease lock, so a just-committed
        // image cannot lose its lease between an old DB snapshot and deletion.
        synchronized(FILE_LOCK){Set<String> referenced=store.imageIds();File[] files=directory.listFiles();if(files==null)return;for(File f:files){String id=f.getName().replaceFirst("\\.preview$","");if(GrowthPolicy.imageId(id)&&!referenced.contains(id)&&!LEASES.containsKey(id)){file(id,false).delete();file(id,true).delete();}}}
    }
}
