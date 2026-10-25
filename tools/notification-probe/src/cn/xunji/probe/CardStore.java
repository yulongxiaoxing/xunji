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
    CardStore(Context context) { super(context, "xunji-cards.db", null, 10); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE sources(id TEXT PRIMARY KEY, group_name TEXT NOT NULL, publisher TEXT NOT NULL, body TEXT NOT NULL, received INTEGER, state TEXT, error TEXT, extraction_version INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE cards(id TEXT PRIMARY KEY, source_id TEXT NOT NULL, category TEXT, title TEXT, requirements TEXT, deadline TEXT, state TEXT, user_modified INTEGER NOT NULL DEFAULT 0, extraction_title TEXT, previous_state TEXT, due_date TEXT NOT NULL DEFAULT '', due_time TEXT NOT NULL DEFAULT '', schedule_override INTEGER NOT NULL DEFAULT 0, course_data TEXT NOT NULL DEFAULT '', event_data TEXT NOT NULL DEFAULT '', auto_completed INTEGER NOT NULL DEFAULT 0)");
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
        if(oldVersion<4){
            db.execSQL("ALTER TABLE cards ADD COLUMN due_date TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE cards ADD COLUMN due_time TEXT NOT NULL DEFAULT ''");
        }
        if(oldVersion<5){
            db.execSQL("ALTER TABLE cards ADD COLUMN schedule_override INTEGER NOT NULL DEFAULT 0");
            db.execSQL("UPDATE cards SET schedule_override=1 WHERE due_date!='' OR due_time!=''");
            try(Cursor c=db.rawQuery("SELECT cards.id,cards.deadline,sources.received FROM cards JOIN sources ON cards.source_id=sources.id WHERE schedule_override=0",null)){
                while(c.moveToNext()){
                    ContentValues automatic=new ContentValues();putAutomatic(automatic,c.getString(1),c.getLong(2));
                    db.update("cards",automatic,"id=?",new String[]{c.getString(0)});
                }
            }
        }
        if(oldVersion<7)db.execSQL("ALTER TABLE cards ADD COLUMN event_data TEXT NOT NULL DEFAULT ''");
        if(oldVersion<6)db.execSQL("ALTER TABLE cards ADD COLUMN course_data TEXT NOT NULL DEFAULT ''");
        if(oldVersion<8)db.execSQL("ALTER TABLE cards ADD COLUMN auto_completed INTEGER NOT NULL DEFAULT 0");
        if(oldVersion<8)try(Cursor c=db.rawQuery("SELECT id,course_data FROM cards WHERE category='课程' AND user_modified=0 AND course_data!=''",null)){
            while(c.moveToNext())try{JSONObject course=new JSONObject(c.getString(1));if(IcsImport.hydrateWakeupFacts(course)){ContentValues v=new ContentValues();v.put("course_data",course.toString());db.update("cards",v,"id=?",new String[]{c.getString(0)});}}catch(Exception ignored){}
        }
        if(oldVersion<9)try(Cursor c=db.rawQuery("SELECT id,due_date,course_data FROM cards WHERE category='课程' AND state='done' AND due_date!='' AND course_data!=''",null)){
            long now=System.currentTimeMillis();while(c.moveToNext())try{
                if(!CoursePolicy.ended(c.getString(1),new JSONObject(c.getString(2)).getString("endTime"),now)){ContentValues v=new ContentValues();v.put("state","todo");v.put("auto_completed",0);db.update("cards",v,"id=?",new String[]{c.getString(0)});}
            }catch(Exception ignored){}
        }
        if(oldVersion<10)backfillEventIntervals(db);
    }

    private void backfillEventIntervals(SQLiteDatabase db){
        try(Cursor c=db.rawQuery("SELECT cards.id,cards.category,cards.deadline,cards.requirements,sources.body,sources.received,(SELECT COUNT(*) FROM cards siblings WHERE siblings.source_id=cards.source_id) FROM cards JOIN sources ON cards.source_id=sources.id WHERE cards.category IN ('活动','考试','竞赛') AND cards.event_data='' AND cards.schedule_override=0",null)){
            while(c.moveToNext())try{
                ContentValues value=new ContentValues();
                putEventInterval(value,c.getString(1),c.getString(2),new JSONArray(c.getString(3)),c.getString(4),c.getLong(5),c.getInt(6)==1);
                if(value.size()>0)db.update("cards",value,"id=?",new String[]{c.getString(0)});
            }catch(org.json.JSONException ignored){}
        }
    }
    @Override public void onOpen(SQLiteDatabase db){
        super.onOpen(db);
        if(!db.isReadOnly()){
            db.execSQL("UPDATE sources SET state='pending',error='' WHERE state='ready' AND extraction_version<? AND id NOT LIKE 'manual-card:%' AND id NOT LIKE 'wakeup:%' AND id NOT LIKE 'memory:%'",new Object[]{ExtractionPolicy.VERSION});
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
        completeEndedCourses(System.currentTimeMillis());
        JSONArray rows = new JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT cards.*,sources.group_name,sources.publisher,sources.received FROM cards JOIN sources ON cards.source_id=sources.id ORDER BY sources.received DESC,cards.rowid", null)) {
            while (c.moveToNext()) {
                JSONObject row = new JSONObject(); row.put("id", string(c, "id")); row.put("sourceId", string(c, "source_id"));
                row.put("group", string(c, "group_name")); row.put("publisher", string(c, "publisher"));
                row.put("category", string(c, "category")); row.put("title", string(c, "title"));
                row.put("requirements", new JSONArray(string(c, "requirements"))); row.put("deadline", string(c, "deadline"));
                row.put("status", string(c, "state")); row.put("receivedAt", c.getLong(c.getColumnIndexOrThrow("received")));
                row.put("autoCompleted",c.getInt(c.getColumnIndexOrThrow("auto_completed"))!=0);
                if(!string(c,"event_data").isEmpty())row.put("event",new JSONObject(string(c,"event_data")));
                if(!string(c,"course_data").isEmpty())row.put("course",new JSONObject(string(c,"course_data"))); row.put("scheduleOverride",c.getInt(c.getColumnIndexOrThrow("schedule_override"))!=0); row.put("dueDate",string(c,"due_date"));row.put("dueTime",string(c,"due_time")); row.put("userModified", c.getInt(c.getColumnIndexOrThrow("user_modified"))!=0); row.put("demo", false); rows.put(row);
            }
        }
        return rows;
    }
    synchronized int completeEndedCourses(long now){
        SQLiteDatabase db=getWritableDatabase();int changed=0;db.beginTransaction();
        try(Cursor c=db.rawQuery("SELECT id,due_date,course_data FROM cards WHERE category='课程' AND state='todo' AND due_date!='' AND course_data!=''",null)){
            while(c.moveToNext())try{
                JSONObject course=new JSONObject(c.getString(2));if(!CoursePolicy.ended(c.getString(1),course.getString("endTime"),now))continue;
                ContentValues v=new ContentValues();v.put("state","done");v.put("auto_completed",1);changed+=db.update("cards",v,"id=? AND state='todo'",new String[]{c.getString(0)});
            }catch(org.json.JSONException|java.time.DateTimeException ignored){}
            db.setTransactionSuccessful();return changed;
        }finally{db.endTransaction();}
    }
    synchronized long nextCourseEnd(long now){
        long next=Long.MAX_VALUE;try(Cursor c=getReadableDatabase().rawQuery("SELECT due_date,course_data FROM cards WHERE category='课程' AND state='todo' AND due_date!='' AND course_data!=''",null)){
            while(c.moveToNext())try{long end=CoursePolicy.endMillis(c.getString(0),new JSONObject(c.getString(1)).getString("endTime"));if(end>now&&end<next)next=end;}catch(Exception ignored){}
        }return next;
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
        try(Cursor c=db.rawQuery("SELECT state,previous_state,category,due_date,course_data FROM cards WHERE id=?",new String[]{id})){
            if(!c.moveToFirst())throw new IllegalArgumentException("事项不存在，请刷新后重试");
            String current=c.getString(0);ContentValues value=new ContentValues();
            if("done".equals(state)&&"课程".equals(c.getString(2))){
                try{if(!CoursePolicy.ended(c.getString(3),new JSONObject(c.getString(4)).getString("endTime"),System.currentTimeMillis()))throw new IllegalArgumentException("课程尚未下课，不能提前标为完成");}
                catch(org.json.JSONException|java.time.DateTimeException e){throw new IllegalArgumentException("课程起止时间不完整，请先核对");}
            }
            if("restore".equals(state)){
                if(!"deleted".equals(current))throw new IllegalArgumentException("事项不在回收站");
                value.put("state",CardPolicy.restoreState(c.getString(1)));
                if("done".equals(value.getAsString("state"))&&"课程".equals(c.getString(2)))try{
                    if(!CoursePolicy.ended(c.getString(3),new JSONObject(c.getString(4)).getString("endTime"),System.currentTimeMillis())){value.put("state","todo");value.put("auto_completed",0);}
                }catch(org.json.JSONException|java.time.DateTimeException e){throw new IllegalArgumentException("课程起止时间不完整，请先核对");}
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
        value.put("requirements",cleaned.toString());value.put("deadline",deadline);value.put("user_modified",1);
        if(row.has("dueDate")||row.has("dueTime")){
            String date=row.optString("dueDate"),time=row.optString("dueTime");SchedulePolicy.validate(date,time);
            value.put("due_date",date);value.put("due_time",time);
        }
        if("课程".equals(category)){
            JSONObject course=row.optJSONObject("course");if(course==null)throw new IllegalArgumentException("课程信息不完整");
            CoursePolicy.validateTime(course.optString("startTime"),course.optString("endTime"));
            if(row.optString("dueDate").isEmpty())throw new IllegalArgumentException("请选择课程日期");
            course.put("name",title);value.put("course_data",course.toString());value.put("due_time",course.getString("startTime"));value.put("schedule_override",1);
        }
        JSONObject event=row.optJSONObject("event");
        if(java.util.Arrays.asList("活动","考试","竞赛").contains(category)&&event!=null){
            CoursePolicy.validateTime(event.optString("startTime"),event.optString("endTime"));if(row.optString("dueDate").isEmpty())throw new IllegalArgumentException("请选择事项日期");
            if(event.optString("location").length()>500)throw new IllegalArgumentException("地点过长");
            value.put("event_data",event.toString());value.put("due_time",event.getString("startTime"));value.put("schedule_override",1);
        }else value.put("event_data","");
        return value;
    }
    private void putAutomatic(ContentValues value,String deadline,long received){
        SchedulePolicy.Deadline result=SchedulePolicy.fromReceived(deadline,received);
        value.put("due_date",result.date);value.put("due_time",result.time);value.put("schedule_override",0);
    }
    private void putEventInterval(ContentValues value,String category,String deadline,JSONArray requirements,String original,long received,boolean singleCard) throws org.json.JSONException {
        if(!EventPolicy.category(category))return;
        StringBuilder text=new StringBuilder(deadline);
        for(int i=0;i<requirements.length();i++)text.append('\n').append(requirements.getString(i));
        String context=text.toString();EventPolicy.Interval interval=EventPolicy.fromReceived(context,received);
        if(interval==null&&singleCard){context=original;interval=EventPolicy.fromReceived(context,received);}
        if(interval==null)return;
        java.util.regex.Matcher location=java.util.regex.Pattern.compile("地点(?:为|是|[:：])\\s*([^，。；;\\n]{1,100})|在([^，。；;\\n]{1,100}?)举行").matcher(context);
        String place=location.find()?(location.group(1)!=null?location.group(1):location.group(2)):"";
        JSONObject event=new JSONObject().put("location",place).put("startTime",interval.start).put("endTime",interval.end);
        value.put("event_data",event.toString());value.put("due_date",interval.date);value.put("due_time",interval.start);value.put("schedule_override",0);
    }
    synchronized void updateCard(JSONObject row) throws Exception {
        ContentValues value=editedValues(row);SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try(Cursor c=db.rawQuery("SELECT cards.due_date,cards.due_time,cards.schedule_override,sources.received,cards.state,cards.auto_completed FROM cards JOIN sources ON cards.source_id=sources.id WHERE cards.id=? AND cards.state!='deleted'",new String[]{row.getString("id")})){
            if(!c.moveToFirst())throw new IllegalArgumentException("事项不存在或已删除，请刷新后重试");
            boolean changed=(row.has("dueDate")||row.has("dueTime"))&&(!row.optString("dueDate").equals(c.getString(0))||!row.optString("dueTime").equals(c.getString(1)));
            if("课程".equals(row.optString("category"))||value.containsKey("event_data")&&!value.getAsString("event_data").isEmpty())value.put("schedule_override",1);
            else if(row.optBoolean("automaticDeadline")||(!changed&&c.getInt(2)==0))putAutomatic(value,value.getAsString("deadline"),c.getLong(3));
            else if(changed)value.put("schedule_override",1);
            if("课程".equals(row.optString("category"))&&"done".equals(c.getString(4))&&CoursePolicy.endMillis(row.getString("dueDate"),row.getJSONObject("course").getString("endTime"))>System.currentTimeMillis()){
                value.put("state","todo");value.put("auto_completed",0);
            }
            if(db.update("cards",value,"id=? AND state!='deleted'",new String[]{row.getString("id")})!=1)throw new IllegalArgumentException("事项不存在或已删除，请刷新后重试");
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    synchronized void setSchedule(String id,String date,String time){
        SchedulePolicy.validate(date,time);
        try(Cursor c=getReadableDatabase().rawQuery("SELECT category,event_data FROM cards WHERE id=?",new String[]{id})){
            if(c.moveToFirst()&&("课程".equals(c.getString(0))||!c.getString(1).isEmpty()))throw new IllegalArgumentException("请在事项编辑中调整完整起止时间");
        }
        ContentValues value=new ContentValues();
        value.put("due_date",date);value.put("due_time",time);value.put("schedule_override",1);value.put("user_modified",1);
        if(getWritableDatabase().update("cards",value,"id=? AND state!='deleted'",new String[]{id})!=1)
            throw new IllegalArgumentException("事项不存在或已删除，请刷新后重试");
    }
    synchronized String createCard(JSONObject row) throws Exception {return createCard(row,null);}
    private String createCard(JSONObject row,String sharedSource) throws Exception {
        ContentValues value=editedValues(row);String sourceId=sharedSource==null?"manual-card:"+UUID.randomUUID().toString():sharedSource,id=UUID.randomUUID().toString();
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            StringBuilder manualBody=new StringBuilder(value.getAsString("title"));
            JSONArray lines=new JSONArray(value.getAsString("requirements"));for(int i=0;i<lines.length();i++)manualBody.append("\n").append(lines.getString(i));
            if(!value.getAsString("deadline").isEmpty())manualBody.append("\n").append(value.getAsString("deadline"));String body=manualBody.toString();
            if(!row.optString("dueDate").isEmpty())manualBody.append("\n日期：").append(row.optString("dueDate")).append(" ").append(row.optString("dueTime"));
            if(row.has("course"))manualBody.append("\n").append(row.getJSONObject("course").toString());
            if(row.has("event"))manualBody.append("\n").append(row.getJSONObject("event").toString());
            if(row.has("repeat"))manualBody.append("\n").append(row.getJSONObject("repeat").toString());
            if(sharedSource==null){if(!insertSource(sourceId,"手动新建","",manualBody.toString(),System.currentTimeMillis()))throw new IllegalStateException("新建失败");state(sourceId,"ready","");}
            // Memory dates were already normalized and confirmed in preview, including unknown dates.
            // Never reinterpret a model's partial deadline while saving that shared memory source.
            if(sharedSource!=null||"课程".equals(row.optString("category"))||value.getAsString("due_date")!=null&&!value.getAsString("due_date").isEmpty())value.put("schedule_override",1);
            else putAutomatic(value,value.getAsString("deadline"),sharedSource==null?System.currentTimeMillis():source(sharedSource).getLong("receivedAt"));
            value.put("id",id);value.put("source_id",sourceId);value.put("state","todo");
            value.put("extraction_title",row.getString("title"));
            if(db.insert("cards",null,value)<0)throw new IllegalStateException("新建失败");
            db.setTransactionSuccessful();return id;
        }finally{db.endTransaction();}
    }
    private List<String> creationDates(JSONObject row) throws Exception {
        JSONObject repeat=row.optJSONObject("repeat");
        if(repeat==null)return java.util.Collections.singletonList(row.optString("dueDate"));
        return CoursePolicy.repeatDates(row.optString("dueDate"),repeat.optString("rule","none"),repeat.optInt("weekday"),repeat.optString("mode"),repeat.optInt("count"),repeat.optString("until"));
    }
    synchronized JSONArray createCards(JSONObject row) throws Exception {return createCards(row,null);}
    private JSONArray createCards(JSONObject row,String source) throws Exception {
        List<String> dates=creationDates(row);SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{JSONArray ids=new JSONArray();for(String date:dates){JSONObject copy=new JSONObject(row.toString()).put("dueDate",date);if(copy.has("course")&&copy.has("repeat"))copy.getJSONObject("course").put("repeat",copy.getJSONObject("repeat"));ids.put(createCard(copy,source));}db.setTransactionSuccessful();return ids;}
        finally{db.endTransaction();}
    }
    synchronized JSONArray saveMemory(String original,JSONArray rows,long received) throws Exception {
        if(original.trim().isEmpty()||original.length()>50000||rows.length()<1||rows.length()>32)throw new IllegalArgumentException("口头记忆内容或事项数量无效");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{String source="memory:"+UUID.randomUUID().toString();insertSource(source,"口头记忆","自己",original,received);state(source,"ready","");
            JSONArray ids=new JSONArray();for(int i=0;i<rows.length();i++){JSONArray created=createCards(rows.getJSONObject(i),source);for(int j=0;j<created.length();j++)ids.put(created.getString(j));if(ids.length()>104)throw new IllegalArgumentException("一次口头记忆不能超过104项");}
            db.setTransactionSuccessful();return ids;
        }finally{db.endTransaction();}
    }
    synchronized JSONObject importCourses(JSONObject preview) throws Exception {
        JSONArray sessions=preview.getJSONArray("sessions");if(sessions.length()==0||sessions.length()>3000)throw new IllegalArgumentException("课次数量无效");
        String raw=preview.getString("raw"),sourceId="wakeup:"+digest(raw);SQLiteDatabase db=getWritableDatabase();int added=0,kept=0;db.beginTransaction();
        try{
            for(int i=0;i<sessions.length();i++){
                JSONObject session=sessions.getJSONObject(i),course=session.getJSONObject("course");String id=session.getString("id");
                try(Cursor old=db.rawQuery("SELECT course_data FROM cards WHERE id=?",new String[]{id})){if(old.moveToFirst()){
                    JSONObject existing=new JSONObject(old.getString(0));
                    if(!existing.optString("importIdentity").equals(course.optString("importIdentity")))throw new IllegalArgumentException("存在相同课次但课程资料不同，未导入；请核对老师、地点和周次");
                    kept++;continue;
                }}
                CoursePolicy.validateTime(course.getString("startTime"),course.getString("endTime"));SchedulePolicy.validate(session.getString("date"),course.getString("startTime"));
                if(added==0){insertSource(sourceId,("ics".equals(preview.optString("format"))?"ICS · ":"WakeUp · ")+preview.getString("tableName"),"",raw,System.currentTimeMillis());state(sourceId,"ready","");}
                JSONArray requirements="ics".equals(course.optString("format"))?new JSONArray().put(course.optString("description")):new JSONArray().put("第"+course.getInt("startNode")+"—"+(course.getInt("startNode")+course.getInt("step")-1)+"节；"+(course.optBoolean("explicitWeeks")?course.getJSONArray("weeks").join("、")+"周":"第"+course.getInt("startWeek")+"—"+course.getInt("endWeek")+"周；"+(course.getInt("weekType")==1?"单周":course.getInt("weekType")==2?"双周":"每周")));
                ContentValues value=new ContentValues();value.put("id",id);value.put("source_id",sourceId);value.put("category","课程");value.put("title",course.getString("name"));value.put("extraction_title",course.getString("name"));
                value.put("requirements",requirements.toString());value.put("deadline",course.getString("startTime")+"—"+course.getString("endTime"));value.put("state","todo");value.put("due_date",session.getString("date"));value.put("due_time",course.getString("startTime"));value.put("schedule_override",1);value.put("course_data",course.toString());
                if(db.insert("cards",null,value)<0)throw new IllegalStateException("课程保存失败，未完成导入");added++;
            }
            db.setTransactionSuccessful();return new JSONObject().put("added",added).put("kept",kept);
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
                JSONObject originalForDate=source(sourceId);if(originalForDate==null)throw new IllegalStateException("原文不存在");
                putAutomatic(value,row.optString("deadline",""),originalForDate.getLong("receivedAt"));
                putEventInterval(value,row.getString("category"),row.optString("deadline",""),row.getJSONArray("requirements"),originalForDate.getString("text"),originalForDate.getLong("receivedAt"),cards.length()==1);
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
