package cn.xunji.probe;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.UUID;

/** Shares CardStore's database/transaction boundary; never rewrites source tasks. */
final class GrowthStore {
    private final SQLiteDatabase db;
    GrowthStore(SQLiteDatabase db){this.db=db;}
    static void create(SQLiteDatabase db){
        db.execSQL("CREATE TABLE growth_records(id TEXT PRIMARY KEY,title TEXT NOT NULL,category TEXT NOT NULL,experience_date TEXT NOT NULL DEFAULT '',result TEXT NOT NULL DEFAULT '',source_card_id TEXT UNIQUE,source_title TEXT NOT NULL DEFAULT '',state TEXT NOT NULL DEFAULT 'active',created INTEGER NOT NULL,updated INTEGER NOT NULL,details TEXT NOT NULL DEFAULT '{}',attachments TEXT NOT NULL DEFAULT '[]')");
    }
    static void upgradeV12(SQLiteDatabase db){
        db.execSQL("ALTER TABLE growth_records ADD COLUMN details TEXT NOT NULL DEFAULT '{}'");
        db.execSQL("ALTER TABLE growth_records ADD COLUMN attachments TEXT NOT NULL DEFAULT '[]'");
    }
    JSONArray records() throws Exception {
        JSONArray rows=new JSONArray();
        try(Cursor c=db.rawQuery("SELECT g.*,c.state AS source_state,c.title AS current_source_title FROM growth_records g LEFT JOIN cards c ON c.id=g.source_card_id ORDER BY g.experience_date DESC,g.created DESC,g.id",null)){
            while(c.moveToNext()){
                JSONObject row=new JSONObject();
                String[][] fields={{"id","id"},{"title","title"},{"category","category"},{"date","experience_date"},{"result","result"},{"sourceCardId","source_card_id"},{"sourceTitle","source_title"},{"sourceState","source_state"},{"currentSourceTitle","current_source_title"},{"state","state"}};
                for(String[] field:fields){int i=c.getColumnIndexOrThrow(field[1]);row.put(field[0],c.isNull(i)?"":c.getString(i));}
                row.put("details",new JSONObject(c.getString(c.getColumnIndexOrThrow("details"))));row.put("attachments",new JSONArray(c.getString(c.getColumnIndexOrThrow("attachments"))));
                row.put("createdAt",c.getLong(c.getColumnIndexOrThrow("created")));row.put("updatedAt",c.getLong(c.getColumnIndexOrThrow("updated")));rows.put(row);
            }
        }
        return rows;
    }
    String save(JSONObject row) throws Exception {
        String id=row.optString("id"),title=row.optString("title").trim(),category=row.optString("category"),date=row.optString("date"),result=row.optString("result").trim(),sourceId=row.optString("sourceCardId");
        if(!row.optString("candidateId").isEmpty()&&(!id.isEmpty()||!sourceId.equals(row.optString("candidateId"))))throw new IllegalArgumentException("候选来源无效");
        GrowthPolicy.validate(title,category,date,result);
        JSONObject details=row.optJSONObject("details");java.util.Map<String,String> fields=new java.util.HashMap<>();
        if(details!=null){java.util.Iterator<String> keys=details.keys();while(keys.hasNext()){String key=keys.next();fields.put(key,details.getString(key).trim());}}
        GrowthPolicy.validateDetails(category,fields);
        JSONObject cleanDetails=new JSONObject();for(java.util.Map.Entry<String,String> field:fields.entrySet())cleanDetails.put(field.getKey(),field.getValue());
        JSONArray attachments=row.optJSONArray("attachments");if(attachments!=null)validateAttachments(attachments);
        db.beginTransaction();
        try{
            ContentValues value=new ContentValues();value.put("title",title);value.put("category",category);value.put("experience_date",date);value.put("result",result);value.put("updated",System.currentTimeMillis());
            if(details!=null||id.isEmpty())value.put("details",cleanDetails.toString());
            if(attachments!=null||id.isEmpty())value.put("attachments",attachments==null?"[]":attachments.toString());
            if(!id.isEmpty()){
                try(Cursor c=db.rawQuery("SELECT source_card_id,state FROM growth_records WHERE id=?",new String[]{id})){
                    if(!c.moveToFirst()||!"active".equals(c.getString(1)))throw new IllegalArgumentException("经历不存在或已移除，请刷新后重试");
                    String original=c.isNull(0)?"":c.getString(0);
                    if(!original.equals(sourceId))throw new IllegalArgumentException("不能修改经历的来源关联");
                }
                db.update("growth_records",value,"id=?",new String[]{id});
            }else{
                String sourceTitle="";
                if(!sourceId.isEmpty()){
                    try(Cursor c=db.rawQuery("SELECT id,state FROM growth_records WHERE source_card_id=?",new String[]{sourceId})){
                        if(c.moveToFirst())throw new IllegalArgumentException("deleted".equals(c.getString(1))?"该事项已有已移除经历，请到成长中恢复":"该事项已有成长记录，请直接查看或编辑");
                    }
                    try(Cursor c=db.rawQuery("SELECT state,category,title FROM cards WHERE id=?",new String[]{sourceId})){
                        if(!c.moveToFirst())throw new IllegalArgumentException("来源事项不存在，请重新核对");
                        sourceTitle=c.getString(2);boolean candidate=!row.optString("candidateId").isEmpty();
                        if(candidate){if(!sourceId.equals(row.getString("candidateId")))throw new IllegalArgumentException("候选来源不一致");new GrowthCandidateStore(db).validate(sourceId);}
                        GrowthPolicy.requireSource(c.getString(0),c.getString(1),sourceTitle,row.optBoolean("participationConfirmed",false),candidate);
                    }
                }
                id=UUID.randomUUID().toString();value.put("id",id);value.put("state","active");value.put("created",System.currentTimeMillis());value.put("source_title",sourceTitle);
                if(sourceId.isEmpty())value.putNull("source_card_id");else value.put("source_card_id",sourceId);
                db.insertOrThrow("growth_records",null,value);
            }
            if(!row.optString("candidateId").isEmpty())new GrowthCandidateStore(db).saved(row.getString("candidateId"),id);
            db.setTransactionSuccessful();return id;
        }finally{db.endTransaction();}
    }
    static void validateAttachments(JSONArray attachments)throws Exception{
        if(attachments.length()>5)throw new IllegalArgumentException("每条经历最多5张证明图片");
        java.util.Set<String> ids=new java.util.HashSet<>();
        for(int i=0;i<attachments.length();i++){
            JSONObject image=attachments.getJSONObject(i);String id=image.getString("id");
            if(!GrowthPolicy.imageId(id)||!ids.add(id)||image.optString("name").length()>200)throw new IllegalArgumentException("证明图片信息无效，请重新选择");
        }
    }
    java.util.Set<String> imageIds()throws Exception{
        java.util.Set<String> ids=new java.util.HashSet<>();
        try(Cursor c=db.rawQuery("SELECT attachments FROM growth_records",null)){while(c.moveToNext()){JSONArray images=new JSONArray(c.getString(0));for(int i=0;i<images.length();i++)ids.add(images.getJSONObject(i).getString("id"));}}
        return ids;
    }
    void state(String id,String state){
        if(!"active".equals(state)&&!"deleted".equals(state))throw new IllegalArgumentException("经历操作不可用");
        ContentValues value=new ContentValues();value.put("state",state);value.put("updated",System.currentTimeMillis());
        if(db.update("growth_records",value,"id=?",new String[]{id})!=1)throw new IllegalArgumentException("经历不存在，请刷新后重试");
    }
}
