package cn.xunji.probe;

import android.graphics.Bitmap;
import java.io.*;
import java.util.*;
import org.json.*;

final class GrowthImageChecks {
    static JSONObject record(String title)throws Exception{return GrowthStoreChecks.record(title).put("date","2026-10").put("details",new JSONObject().put("award","省二等奖"));}
    static byte[] png()throws Exception{
        Bitmap b=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888);b.eraseColor(0xffeeeeff);ByteArrayOutputStream out=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.PNG,100,out);b.recycle();return out.toByteArray();
    }
    static byte[] bytes(InputStream in)throws Exception{try(InputStream input=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[4096];int n;while((n=input.read(buffer))!=-1)out.write(buffer,0,n);return out.toByteArray();}}
    static void run(StoreInstrumentation t)throws Exception{
        t.reset();GrowthStore growth=new GrowthStore(t.store.getWritableDatabase());GrowthImages images=new GrowthImages(t.context);GrowthImageDraft draft=new GrowthImageDraft(images);byte[] raw=png();
        JSONObject proof=images.importImage(new ByteArrayInputStream(raw),"证明.png");String id=proof.getString("id");
        t.check(images.exists(id),"proof original and preview stored privately");
        t.check(Arrays.equals(raw,bytes(images.response(id,true).getData())),"original image bytes preserved");
        t.check(images.response(id,false).getMimeType().equals("image/png"),"local preview MIME valid");
        JSONArray kept=new JSONArray().put(proof);images.validate(kept);t.check(true,"proof validates from private files");
        String saved=growth.save(record("竞赛证据").put("attachments",kept));images.release(id);
        t.store.close();t.store=new CardStore(t.context);growth=new GrowthStore(t.store.getWritableDatabase());
        JSONObject reopened=GrowthStoreChecks.find(growth,saved);t.check(reopened.getJSONObject("details").getString("award").equals("省二等奖")&&reopened.getString("date").equals("2026-10"),"typed fields and month survive restart");
        t.check(reopened.getJSONArray("attachments").getJSONObject(0).getString("id").equals(id)&&images.exists(id),"saved proof survives restart");
        GrowthImageDraft otherDraft=new GrowthImageDraft(new GrowthImages(t.context));otherDraft.begin("other-editor");
        JSONObject otherProof=images.importImage(new ByteArrayInputStream(raw),"另一表单.png");otherDraft.accept("other-editor",new JSONArray().put(otherProof));
        new GrowthImages(t.context).prune(growth);
        t.check(images.exists(otherProof.getString("id")),"a second activity cleanup cannot delete another active proof draft");otherDraft.cancelAll();
        GrowthImageDraft editorA=new GrowthImageDraft(images);editorA.begin("editing-saved",kept);
        growth.save(GrowthStoreChecks.record("另一窗口移除图片").put("id",saved).put("attachments",new JSONArray()));
        new GrowthImages(t.context).prune(growth);t.check(images.exists(id),"saved image in another open editor remains leased");
        growth.save(record("恢复该图片").put("id",saved).put("attachments",kept));editorA.commit("editing-saved",kept);images.prune(growth);t.check(images.exists(id),"commit then lease release preserves referenced image");
        growth.save(GrowthStoreChecks.record("兼容旧字段更新").put("id",saved));t.check(GrowthStoreChecks.find(growth,saved).getJSONArray("attachments").length()==1,"omitted proof fields do not silently delete attachments");
        growth.state(saved,"deleted");images.prune(growth);t.check(images.exists(id),"soft deleted record retains proof for recovery");growth.state(saved,"active");
        draft.begin("a");JSONObject cancelled=images.importImage(new ByteArrayInputStream(raw),"取消.png");draft.accept("a",new JSONArray().put(cancelled));draft.cancel("a");t.check(!images.exists(cancelled.getString("id"))&&images.exists(id),"discard cleans imports but keeps saved proof");
        draft.begin("old");JSONObject stale=images.importImage(new ByteArrayInputStream(raw),"迟到.png");draft.begin("new");t.check(!draft.accept("old",new JSONArray().put(stale))&&!images.exists(stale.getString("id")),"stale picker result deleted and not attached to new editor");
        JSONObject selected=images.importImage(new ByteArrayInputStream(raw),"选择.png"),removed=images.importImage(new ByteArrayInputStream(raw),"移除.png");draft.accept("new",new JSONArray().put(selected).put(removed));draft.commit("new",new JSONArray().put(selected));t.check(images.exists(selected.getString("id"))&&!images.exists(removed.getString("id")),"commit keeps chosen import and cleans removed draft proof");
        GrowthStoreChecks.rejects(t,()->images.validate(new JSONArray().put(new JSONObject().put("id",UUID.randomUUID().toString()).put("name","missing"))),"missing private image rejected");
        GrowthStoreChecks.rejects(t,()->GrowthStore.validateAttachments(new JSONArray().put(new JSONObject().put("id","../secret"))),"attachment traversal rejected");
        GrowthStoreChecks.rejects(t,()->images.importImage(new ByteArrayInputStream("not an image".getBytes("UTF-8")),"bad.png"),"non image bytes rejected");
        GrowthStoreChecks.rejects(t,()->images.importImage(new InputStream(){long remaining=GrowthImages.MAX_BYTES+1;public int read(){return remaining-->0?0:-1;}public int read(byte[] b,int off,int len){if(remaining<=0)return -1;int n=(int)Math.min(remaining,len);Arrays.fill(b,off,off+n,(byte)0);remaining-=n;return n;}},"large.png"),"oversize image rejected before decoding");
        JSONArray six=new JSONArray();for(int i=0;i<6;i++)six.put(new JSONObject().put("id",UUID.randomUUID().toString()));GrowthStoreChecks.rejects(t,()->GrowthStore.validateAttachments(six),"six proofs rejected");
        t.reset();GrowthStore v11=new GrowthStore(t.store.getWritableDatabase());String legacy=v11.save(GrowthStoreChecks.record("v11旧经历").put("date","2026-10-28").put("result","原结果"));
        t.store.getWritableDatabase().execSQL("ALTER TABLE growth_records DROP COLUMN details");t.store.getWritableDatabase().execSQL("ALTER TABLE growth_records DROP COLUMN attachments");t.store.getWritableDatabase().setVersion(11);t.store.close();t.store=new CardStore(t.context);
        GrowthStore upgraded=new GrowthStore(t.store.getWritableDatabase());JSONObject migrated=GrowthStoreChecks.find(upgraded,legacy);
        t.check(migrated.getString("date").equals("2026-10-28")&&migrated.getString("result").equals("原结果"),"v11 upgrade preserves exact date and result");t.check(migrated.getJSONObject("details").length()==0&&migrated.getJSONArray("attachments").length()==0,"v11 migration defaults optional fields empty");
        upgraded.save(record("v12可保存").put("attachments",kept));t.check(upgraded.records().length()==2,"new typed fields save after migration");
        images.delete(selected.getString("id"));images.delete(id);draft.cancelAll();
    }
}
