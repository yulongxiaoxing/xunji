package cn.xunji.probe;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

/** Durable real sources, cards and versioned content extraction results. */
final class CardStore extends SQLiteOpenHelper {
    CardStore(Context context) { super(context, "xunji-cards.db", null, 3); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE sources(id TEXT PRIMARY KEY, group_name TEXT NOT NULL, publisher TEXT NOT NULL, body TEXT NOT NULL, received INTEGER, state TEXT, error TEXT, extraction_version INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE cards(id TEXT PRIMARY KEY, source_id TEXT NOT NULL, category TEXT, title TEXT, requirements TEXT, deadline TEXT, state TEXT, user_modified INTEGER NOT NULL DEFAULT 0, extraction_title TEXT, previous_state TEXT)");
        db.execSQL("CREATE INDEX cards_source ON cards(source_id)");
        createCache(db);
    }
    private void createCache(SQLiteDatabase db){db.execSQL("CREATE TABLE IF NOT EXISTS extraction_cache(content_key TEXT PRIMARY KEY, version INTEGER NOT NULL, cards TEXT NOT NULL)");}
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(oldVersion<2){db.execSQL("ALTER TABLE sources ADD COLUMN extraction_version INTEGER NOT NULL DEFAULT 0");createCache(db);}
        if(oldVersion<3){
            db.execSQL("ALTER TABLE cards ADD COLUMN user_modified INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE cards ADD COLUMN extraction_title TEXT");
            db.execSQL("ALTER TABLE cards ADD COLUMN previous_state TEXT");
            db.execSQL("UPDATE cards SET extraction_title=title");
        }
    }
    @Override public void onOpen(SQLiteDatabase db){
        super.onOpen(db);
        if(!db.isReadOnly()){
            db.execSQL("UPDATE sources SET state='pending',error='' WHERE state='ready' AND extraction_version<? AND id NOT LIKE 'manual-card:%'",new Object[]{ExtractionPolicy.VERSION});
            // Recover the old empty-result error once under the corrected rules.
            db.execSQL("UPDATE sources SET state='pending',error='',extraction_version=? WHERE state='error' AND error=? AND extraction_version<?",new Object[]{ExtractionPolicy.VERSION,"模型返回的事项数量不正确，请重试",ExtractionPolicy.VERSION});
        }
    }
    void importNotifications(JSONArray rows) {
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null || !"白名单匹配".equals(row.optString("mode"))) continue;
            String body = row.optString("text");
            if (body.isEmpty()) continue;
            String id = digest(row.optString("key") + "|" + row.optLong("time") + "|" + body);
            insertSource(id, row.optString("label", "来源待确认"), row.optString("publisher", ""), body, row.optLong("time"));
        }
    }
    String addManual(String group, String publisher, String text) {
        String id = UUID.randomUUID().toString(); insertSource(id, group, publisher, text, System.currentTimeMillis()); return id;
    }
    boolean addNapCat(String account,String groupId,String messageId,String group,String publisher,String text,long time){return insertSource("napcat:"+digest(account+"|"+groupId+"|"+messageId),group,publisher,text,time);}
    private boolean insertSource(String id, String group, String publisher, String body, long received) {
        ContentValues value = new ContentValues(); value.put("id", id); value.put("group_name", group);
        value.put("publisher", publisher); value.put("body", body); value.put("received", received);
        value.put("state", "pending"); value.put("error", "");
        return getWritableDatabase().insertWithOnConflict("sources", null, value, SQLiteDatabase.CONFLICT_IGNORE)!=-1;
    }
    JSONArray sources() throws Exception {
        JSONArray rows = new JSONArray();
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT * FROM sources ORDER BY received DESC", null)) {
            while (cursor.moveToNext()) rows.put(source(cursor));
        }
        return rows;
    }
    JSONObject source(String id) throws Exception {
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT * FROM sources WHERE id=?", new String[]{id})) {
            return cursor.moveToFirst() ? source(cursor) : null;
        }
    }
    private JSONObject source(Cursor c) throws Exception {
        JSONObject value = new JSONObject(); value.put("id", string(c, "id")); value.put("group", string(c, "group_name"));
        value.put("publisher", string(c, "publisher")); value.put("text", string(c, "body"));
        value.put("receivedAt", c.getLong(c.getColumnIndexOrThrow("received")));
        value.put("aiState", string(c, "state")); value.put("error", string(c, "error")); return value;
    }
    JSONArray cards() throws Exception {
        JSONArray rows = new JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT cards.*,sources.group_name,sources.publisher,sources.received FROM cards JOIN sources ON cards.source_id=sources.id ORDER BY sources.received DESC,cards.rowid", null)) {
            while (c.moveToNext()) {
                JSONObject row = new JSONObject(); row.put("id", string(c, "id")); row.put("sourceId", string(c, "source_id"));
                row.put("group", string(c, "group_name")); row.put("publisher", string(c, "publisher"));
                row.put("category", string(c, "category")); row.put("title", string(c, "title"));
                row.put("requirements", new JSONArray(string(c, "requirements"))); row.put("deadline", string(c, "deadline"));
                row.put("status", string(c, "state")); row.put("receivedAt", c.getLong(c.getColumnIndexOrThrow("received")));
                row.put("userModified", c.getInt(c.getColumnIndexOrThrow("user_modified"))!=0); row.put("demo", false); rows.put(row);
            }
        }
        return rows;
    }
    void state(String id, String state, String error) {
        ContentValues value = new ContentValues(); value.put("state", state); value.put("error", error);
        if(state.equals("ready")||state.equals("error"))value.put("extraction_version",ExtractionPolicy.VERSION);
        getWritableDatabase().update("sources", value, "id=?", new String[]{id});
    }
    boolean hasWork() {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM sources WHERE state IN ('pending','processing') LIMIT 1",null)) {return c.moveToFirst();}
    }
    void retryErrors() {
        ContentValues value=new ContentValues();value.put("state","pending");value.put("error","");
        getWritableDatabase().update("sources",value,"state=?",new String[]{"error"});
    }
    synchronized void cardState(String id, String state) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try(Cursor c=db.rawQuery("SELECT state,previous_state FROM cards WHERE id=?",new String[]{id})){
            if(!c.moveToFirst())throw new IllegalArgumentException("事项不存在，请刷新后重试");
            String current=c.getString(0);ContentValues value=new ContentValues();
            if("restore".equals(state)){
                if(!"deleted".equals(current))throw new IllegalArgumentException("事项不在回收站");
                value.put("state",CardPolicy.restoreState(c.getString(1)));
            }else{
                value.put("state",CardPolicy.transition(current,state));
                if("deleted".equals(state))value.put("previous_state",current);
            }
            // A deliberate decision protects this card even after restoring to inbox.
            value.put("user_modified",1);
            db.update("cards",value,"id=?",new String[]{id});db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    private ContentValues editedValues(JSONObject row) throws Exception {
        String title=row.optString("title").trim(),category=row.optString("category"),deadline=row.optString("deadline").trim();
        JSONArray input=row.getJSONArray("requirements"),cleaned=new JSONArray();List<String> requirements=new ArrayList<>();
        for(int i=0;i<input.length();i++){String line=input.getString(i).trim();if(!line.isEmpty()){requirements.add(line);cleaned.put(line);}}
        CardPolicy.validate(title,category,requirements,deadline);
        ContentValues value=new ContentValues();value.put("title",title);value.put("category",category);
        value.put("requirements",cleaned.toString());value.put("deadline",deadline);value.put("user_modified",1);return value;
    }
    synchronized void updateCard(JSONObject row) throws Exception {
        ContentValues value=editedValues(row);
        if(getWritableDatabase().update("cards",value,"id=? AND state!='deleted'",new String[]{row.getString("id")})!=1)
            throw new IllegalArgumentException("事项不存在或已删除，请刷新后重试");
    }
    synchronized String createCard(JSONObject row) throws Exception {
        ContentValues value=editedValues(row);String sourceId="manual-card:"+UUID.randomUUID().toString(),id=UUID.randomUUID().toString();
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            StringBuilder manualBody=new StringBuilder(value.getAsString("title"));
            JSONArray lines=new JSONArray(value.getAsString("requirements"));for(int i=0;i<lines.length();i++)manualBody.append("\n").append(lines.getString(i));
            if(!value.getAsString("deadline").isEmpty())manualBody.append("\n").append(value.getAsString("deadline"));String body=manualBody.toString();
            if(!insertSource(sourceId,"手动新建","",body,System.currentTimeMillis()))throw new IllegalStateException("新建失败");
            state(sourceId,"ready","");value.put("id",id);value.put("source_id",sourceId);value.put("state","todo");
            value.put("extraction_title",row.getString("title"));
            if(db.insert("cards",null,value)<0)throw new IllegalStateException("新建失败");
            db.setTransactionSuccessful();return id;
        }finally{db.endTransaction();}
    }
    synchronized void replaceCards(String sourceId, JSONArray cards) throws Exception {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            List<PreviousCard> previous=new ArrayList<>();
            try(Cursor old=db.rawQuery("SELECT id,COALESCE(extraction_title,title),state,user_modified FROM cards WHERE source_id=? ORDER BY rowid",new String[]{sourceId})){
                while(old.moveToNext())previous.add(new PreviousCard(old.getString(0),old.getString(1),old.getString(2),old.getInt(3)!=0));
            }
            // Retain unmatched actionable cards rather than silently resetting a user's tasks.

            List<String> oldTitles=new ArrayList<>(),newTitles=new ArrayList<>();
            for(PreviousCard old:previous)oldTitles.add(old.title);
            for(int i=0;i<cards.length();i++)newTitles.add(cards.getJSONObject(i).getString("title"));
            List<Boolean> protectedCards=new ArrayList<>();for(PreviousCard old:previous)protectedCards.add(old.modified||!"inbox".equals(old.state));
            boolean freeze=CardPolicy.preserveSource(oldTitles,newTitles,protectedCards);
            if(!freeze)db.delete("cards", "source_id=? AND state='inbox' AND user_modified=0", new String[]{sourceId});
            for (int i = 0; i < cards.length() && !freeze; i++) {
                JSONObject row = cards.getJSONObject(i); ContentValues value = new ContentValues();
                PreviousCard match=null;
                int previousIndex=ExtractionPolicy.previousCardIndex(oldTitles,newTitles,i);
                if(previousIndex>=0)match=previous.get(previousIndex);
                if(match!=null && (match.modified||!"inbox".equals(match.state)))continue;
                value.put("id",match==null?UUID.randomUUID().toString():match.id);
                value.put("extraction_title",row.getString("title")); value.put("source_id", sourceId); value.put("category", row.getString("category"));
                value.put("title", row.getString("title")); value.put("requirements", row.getJSONArray("requirements").toString());
                value.put("deadline", row.optString("deadline", "")); value.put("state",match==null?"inbox":match.state);
                if(db.insertWithOnConflict("cards",null,value,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new IllegalStateException("事项保存失败，原文已保留");
            }
            JSONObject original=source(sourceId);
            if(original==null)throw new IllegalStateException("原文不存在");
            ContentValues cached=new ContentValues();cached.put("content_key",ExtractionPolicy.contentKey(original.getString("text")));
            cached.put("version",ExtractionPolicy.VERSION);cached.put("cards",cards.toString());
            if(db.insertWithOnConflict("extraction_cache",null,cached,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new IllegalStateException("提取结果保存失败，原文已保留");
            state(sourceId, "ready", ""); db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    JSONArray cachedExtraction(String text){
        try(Cursor cached=getReadableDatabase().rawQuery("SELECT cards FROM extraction_cache WHERE content_key=? AND version=?",new String[]{ExtractionPolicy.contentKey(text),String.valueOf(ExtractionPolicy.VERSION)})){
            if(cached.moveToFirst()){JSONArray result=new JSONArray(cached.getString(0));if(ExtractionPolicy.validCardCount(result.length()))return result;}
        }catch(Exception ignored){}return null;
    }
    private static final class PreviousCard{
        final String id,title,state;final boolean modified;
        PreviousCard(String id,String title,String state,boolean modified){this.id=id;this.title=title;this.state=state;this.modified=modified;}
    }
    static String digest(String text) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(); for (byte b : bytes) hex.append(String.format("%02x", b & 255)); return hex.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private String string(Cursor c, String key) { String value = c.getString(c.getColumnIndexOrThrow(key)); return value == null ? "" : value; }
}
