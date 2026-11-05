package cn.xunji.probe;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.content.ContentValues;
import org.json.*;

/** Durable AI decisions. A candidate is never evidence of attendance or an award. */
final class GrowthCandidateStore {
 private final SQLiteDatabase db;
 GrowthCandidateStore(SQLiteDatabase db){this.db=db;}
 static void create(SQLiteDatabase db){db.execSQL("CREATE TABLE IF NOT EXISTS growth_candidates(card_id TEXT PRIMARY KEY,input TEXT NOT NULL,state TEXT NOT NULL DEFAULT 'pending',category TEXT NOT NULL DEFAULT '',reason TEXT NOT NULL DEFAULT '',growth_id TEXT NOT NULL DEFAULT '')");}
 static String input(JSONObject c)throws Exception{return new JSONObject().put("title",c.optString("title")).put("category",c.optString("category")).put("requirements",c.optJSONArray("requirements")).put("date",c.optString("dueDate")).put("time",c.optString("dueTime")).put("event",c.optJSONObject("event")).toString();}
 void sync(JSONArray cards)throws Exception{
  db.beginTransaction();try{
   for(int i=0;i<cards.length();i++){JSONObject c=cards.getJSONObject(i);if(!"done".equals(c.optString("status"))||"课程".equals(c.optString("category")))continue;
    String id=c.getString("id"),input=input(c);try(Cursor old=db.rawQuery("SELECT state,input FROM growth_candidates WHERE card_id=?",new String[]{id})){
     if(old.moveToFirst()){if(java.util.Arrays.asList("ignored","saved").contains(old.getString(0))||input.equals(old.getString(1)))continue;}
    }
    try(Cursor linked=db.rawQuery("SELECT 1 FROM growth_records WHERE source_card_id=?",new String[]{id})){if(linked.moveToFirst())continue;}
    ContentValues v=new ContentValues();v.put("card_id",id);v.put("input",input);v.put("state","pending");v.put("category","");v.put("reason","");db.insertWithOnConflict("growth_candidates",null,v,SQLiteDatabase.CONFLICT_REPLACE);
   }db.setTransactionSuccessful();
  }finally{db.endTransaction();}
 }
 JSONArray rows()throws Exception{
  JSONArray rows=new JSONArray();try(Cursor c=db.rawQuery("SELECT g.card_id,g.state,g.category,g.reason,c.title FROM growth_candidates g JOIN cards c ON c.id=g.card_id WHERE c.state='done' AND c.category!='课程' AND g.state IN ('pending','ready','error') AND NOT EXISTS(SELECT 1 FROM growth_records r WHERE r.source_card_id=c.id)",null)){
   while(c.moveToNext())rows.put(new JSONObject().put("sourceCardId",c.getString(0)).put("state",c.getString(1)).put("category",c.getString(2)).put("reason",c.getString(3)).put("title",c.getString(4)));
  }return rows;
 }
 JSONObject next()throws Exception{try(Cursor c=db.rawQuery("SELECT g.card_id,g.input FROM growth_candidates g JOIN cards c ON c.id=g.card_id WHERE g.state='pending' AND c.state='done' AND c.category!='课程' AND NOT EXISTS(SELECT 1 FROM growth_records r WHERE r.source_card_id=c.id) ORDER BY g.rowid LIMIT 1",null)){return c.moveToFirst()?new JSONObject().put("id",c.getString(0)).put("input",c.getString(1)):null;}}
 void apply(String id,String input,JSONObject decision)throws Exception{
  ContentValues v=new ContentValues();boolean candidate=decision.getBoolean("candidate");String category=decision.optString("category");
  if(candidate&&!GrowthPolicy.CATEGORIES.contains(category))throw new IllegalArgumentException("AI分类无效，请重试");
  v.put("state",candidate?"ready":"skip");v.put("category",candidate?category:"");v.put("reason",decision.optString("reason").substring(0,Math.min(200,decision.optString("reason").length())));
  db.update("growth_candidates",v,"card_id=? AND input=? AND state='pending' AND EXISTS(SELECT 1 FROM cards WHERE id=? AND state='done' AND category!='课程')",new String[]{id,input,id});
 }
 void error(String id,String input){ContentValues v=new ContentValues();v.put("state","error");v.put("reason","AI判断失败，原事项保留，可重试");db.update("growth_candidates",v,"card_id=? AND input=? AND state='pending'",new String[]{id,input});}
 void ignore(String id){ContentValues v=new ContentValues();v.put("state","ignored");db.update("growth_candidates",v,"card_id=? AND state IN ('pending','ready','error')",new String[]{id});}
 void retry(){ContentValues v=new ContentValues();v.put("state","pending");v.put("reason","");db.update("growth_candidates",v,"state='error'",null);}
 void validate(String id)throws Exception{
  try(Cursor c=db.rawQuery("SELECT g.input,c.category,c.title,c.requirements,c.due_date,c.due_time,c.event_data FROM growth_candidates g JOIN cards c ON c.id=g.card_id WHERE g.card_id=? AND g.state='ready' AND c.state='done'",new String[]{id})){
   if(!c.moveToFirst())throw new IllegalArgumentException("候选或来源状态已变化，请重新打开");
   JSONObject source=new JSONObject().put("category",c.getString(1)).put("title",c.getString(2)).put("requirements",new JSONArray(c.getString(3))).put("dueDate",c.getString(4)).put("dueTime",c.getString(5));if(!c.getString(6).isEmpty())source.put("event",new JSONObject(c.getString(6)));
   if("课程".equals(c.getString(1))||!input(source).equals(c.getString(0)))throw new IllegalArgumentException("来源内容已变化，请重新等待AI判断");
  }
 }
 void saved(String id,String growthId){ContentValues v=new ContentValues();v.put("state","saved");v.put("growth_id",growthId);db.update("growth_candidates",v,"card_id=?",new String[]{id});}
}
