package cn.xunji.probe;
import android.app.Instrumentation;
import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;

/** Isolated package only; never opens the user's Xunji database. */
public class StoreInstrumentation extends Instrumentation {
 int assertions=0;Context context;CardStore store;
 void check(boolean condition,String message){assertions++;if(!condition)throw new AssertionError(message);}
 JSONObject row(String title) throws Exception {return new JSONObject().put("title",title).put("category","作业").put("requirements",new JSONArray().put("题干保持完整")).put("deadline","周五");}
 JSONObject card(String id)throws Exception{JSONArray a=store.cards();for(int i=0;i<a.length();i++)if(a.getJSONObject(i).getString("id").equals(id))return a.getJSONObject(i);throw new AssertionError("card missing");}
 String id(int i)throws Exception{return store.cards().getJSONObject(i).getString("id");}
 void reset(){if(store!=null)store.close();context.deleteDatabase("xunji-cards.db");store=new CardStore(context);}
 @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
 @Override public void onStart(){Bundle result=new Bundle();try{
  context=getTargetContext();if(!context.getPackageName().equals("cn.xunji.cardtests"))throw new AssertionError("Refusing non-test package");
  reset();String source=store.addManual("测试群","测试发布人","完整原文");store.replaceCards(source,new JSONArray().put(row("第一项")).put(row("第二项")));
  String first=id(0),second=id(1);store.cardState(first,"ignored");check(card(first).getString("status").equals("ignored"),"decline persists");
  store.cardState(second,"done");store.cardState(second,"deleted");check(card(second).getString("status").equals("deleted"),"delete persists");
  store.cardState(second,"restore");check(card(second).getString("status").equals("done"),"restore previous state");
  store.updateCard(row("手动修改标题").put("id",first));store.replaceCards(source,new JSONArray().put(row("第二项")).put(row("第一项")).put(row("新第三项")));
  check(card(first).getString("title").equals("手动修改标题"),"edit survives reordering");check(card(first).getString("status").equals("ignored"),"ignored survives");check(card(second).getString("status").equals("done"),"new item cannot overwrite reordered protected ID");check(store.cards().length()==3,"new unrelated card inserted");
  check(store.source(source).getString("text").equals("完整原文"),"source immutable");
  store.cardState(first,"deleted");store.replaceCards(source,new JSONArray().put(row("模型重命名了所有事项")));
  check(store.cards().length()==3,"ambiguous re-extraction retains source cards");check(card(first).getString("status").equals("deleted"),"deleted never resurrects");
  store.close();store=new CardStore(context);check(card(first).getString("status").equals("deleted"),"restart keeps deletion");store.cardState(first,"restore");check(card(first).getString("status").equals("ignored"),"restore ignored state");
  String manual=store.createCard(row("无需AI的事项"));String manualSource=card(manual).getString("sourceId");check(card(manual).getString("status").equals("todo"),"manual directly todo");check(store.source(manualSource).getString("aiState").equals("ready"),"manual not extraction work");
  store.getWritableDatabase().execSQL("UPDATE sources SET extraction_version=0 WHERE id=?",new Object[]{manualSource});store.close();store=new CardStore(context);check(store.source(manualSource).getString("aiState").equals("ready"),"manual skips rule migration");
  int count=store.cards().length();try{store.updateCard(row(" ").put("id",manual));throw new AssertionError("blank accepted");}catch(IllegalArgumentException expected){check(store.cards().length()==count,"invalid edit atomic");}
  store.cardState(manual,"deleted");try{store.updateCard(row("bad").put("id",manual));throw new AssertionError("deleted edited");}catch(IllegalArgumentException expected){check(card(manual).getString("title").equals("无需AI的事项"),"deleted edit rejected");}
  store.close();context.deleteDatabase("xunji-cards.db");
  SQLiteDatabase old=context.openOrCreateDatabase("xunji-cards.db",0,null);
  old.execSQL("CREATE TABLE sources(id TEXT PRIMARY KEY,group_name TEXT NOT NULL,publisher TEXT NOT NULL,body TEXT NOT NULL,received INTEGER,state TEXT,error TEXT,extraction_version INTEGER NOT NULL DEFAULT 0)");
  old.execSQL("CREATE TABLE cards(id TEXT PRIMARY KEY,source_id TEXT NOT NULL,category TEXT,title TEXT,requirements TEXT,deadline TEXT,state TEXT)");
  old.execSQL("CREATE TABLE extraction_cache(content_key TEXT PRIMARY KEY,version INTEGER NOT NULL,cards TEXT NOT NULL)");
  old.execSQL("INSERT INTO sources VALUES('s','群','发布人','旧完整原文',1,'ready','',5)");old.execSQL("INSERT INTO cards VALUES('c','s','作业','旧标题','[]','','ignored')");old.setVersion(2);old.close();store=new CardStore(context);
  check(card("c").getString("title").equals("旧标题"),"v2 migrated title");check(card("c").getString("status").equals("ignored"),"v2 state preserved");
  store.replaceCards("s",new JSONArray().put(row("新标题")));check(card("c").getString("status").equals("ignored"),"migrated ignored preserved");check(store.source("s").getString("text").equals("旧完整原文"),"migration original preserved");
  check(card("c").getString("dueDate").equals(""),"legacy card date stays unknown");
  String scheduled=store.createCard(row("日程测试"));store.setSchedule(scheduled,"2026-12-31","23:59");
  check(card(scheduled).getString("dueDate").equals("2026-12-31"),"date persisted");check(card(scheduled).getString("dueTime").equals("23:59"),"time persisted");
  store.updateCard(row("日程标题修改").put("id",scheduled));check(card(scheduled).getString("dueDate").equals("2026-12-31"),"content edit retains date when omitted");
  store.close();store=new CardStore(context);check(card(scheduled).getString("dueDate").equals("2026-12-31"),"date survives restart");
  try{store.setSchedule(scheduled,"2026-02-29","");throw new AssertionError("invalid calendar date accepted");}catch(IllegalArgumentException expected){check(card(scheduled).getString("dueDate").equals("2026-12-31"),"invalid date update atomic");}
  store.setSchedule(scheduled,"","");check(card(scheduled).getString("dueDate").equals(""),"date cleared");
  store.cardState(scheduled,"deleted");try{store.setSchedule(scheduled,"2026-10-22","");throw new AssertionError("deleted schedule edited");}catch(IllegalArgumentException expected){check(card(scheduled).getString("dueDate").equals(""),"deleted cannot reschedule");}
  store.close();context.deleteDatabase("xunji-cards.db");
  SQLiteDatabase v3=context.openOrCreateDatabase("xunji-cards.db",0,null);
  v3.execSQL("CREATE TABLE sources(id TEXT PRIMARY KEY,group_name TEXT NOT NULL,publisher TEXT NOT NULL,body TEXT NOT NULL,received INTEGER,state TEXT,error TEXT,extraction_version INTEGER NOT NULL DEFAULT 0)");
  v3.execSQL("CREATE TABLE cards(id TEXT PRIMARY KEY,source_id TEXT NOT NULL,category TEXT,title TEXT,requirements TEXT,deadline TEXT,state TEXT,user_modified INTEGER NOT NULL DEFAULT 0,extraction_title TEXT,previous_state TEXT)");
  v3.execSQL("CREATE TABLE extraction_cache(content_key TEXT PRIMARY KEY,version INTEGER NOT NULL,cards TEXT NOT NULL)");
  v3.execSQL("INSERT INTO sources VALUES('v3s','群','发布人','v3原文',1,'ready','',5)");v3.execSQL("INSERT INTO cards VALUES('v3c','v3s','作业','人工编辑的标题','[]','','deleted',1,'旧AI标题','done')");v3.setVersion(3);v3.close();store=new CardStore(context);
  check(card("v3c").getString("dueDate").equals(""),"v3 migrated unknown date");check(card("v3c").getString("title").equals("人工编辑的标题"),"v3 manual content retained");store.cardState("v3c","restore");check(card("v3c").getString("status").equals("done"),"v3 delete restore state retained");
  store.setSchedule("v3c","2026-10-22","");store.replaceCards("v3s",new JSONArray().put(row("重算的标题")));check(card("v3c").getString("dueDate").equals("2026-10-22"),"schedule protected from extraction");check(store.source("v3s").getString("text").equals("v3原文"),"scheduling preserves original");
  String autoSource=store.addManual("群","老师","测试原文2026年10月23日20:00前");store.replaceCards(autoSource,new JSONArray().put(row("自动截止").put("deadline","2026年10月23日20:00前")));
  String autoId="";JSONArray all=store.cards();for(int i=0;i<all.length();i++)if(all.getJSONObject(i).getString("sourceId").equals(autoSource))autoId=all.getJSONObject(i).getString("id");
  check(card(autoId).getString("dueDate").equals("2026-10-23"),"extracted deadline auto schedules");check(card(autoId).getString("dueTime").equals("20:00"),"deadline time automatic");
  store.cardState(autoId,"todo");check(card(autoId).getString("dueDate").equals("2026-10-23"),"joining todo needs no manual date");
  store.updateCard(row("只改标题").put("deadline","2026年10月23日20:00前").put("id",autoId).put("dueDate","2026-10-23").put("dueTime","20:00"));
  check(!card(autoId).getBoolean("scheduleOverride"),"same schedule values do not create override");
  store.setSchedule(autoId,"2026-10-24","18:00");store.close();store=new CardStore(context);check(card(autoId).getString("dueDate").equals("2026-10-24"),"manual override survives auto backfill");
  store.updateCard(row("改回自动").put("id",autoId).put("deadline","2026年10月23日20:00前").put("automaticDeadline",true));check(card(autoId).getString("dueDate").equals("2026-10-23"),"can reset automatic deadline");
  store.close();context.deleteDatabase("xunji-cards.db");
  SQLiteDatabase v4=context.openOrCreateDatabase("xunji-cards.db",0,null);store=new CardStore(context);store.onCreate(v4);
  v4.execSQL("INSERT INTO sources VALUES('old-auto','群','发布人','旧原文',1,'ready','',5)");
  v4.execSQL("INSERT INTO cards(id,source_id,title,category,requirements,deadline,state,user_modified,extraction_title) VALUES('old-auto-c','old-auto','旧自动','作业','[]','2026年10月25日18:00','todo',1,'旧自动')");
  v4.execSQL("INSERT INTO cards(id,source_id,title,category,requirements,deadline,state,user_modified,extraction_title,due_date,due_time) VALUES('old-override','old-auto','旧手动','作业','[]','2026年10月25日18:00','done',1,'旧手动','2026-10-24','16:00')");
  // Simulate the actual v4 schema by dropping the new override column before opening.
  v4.execSQL("ALTER TABLE cards DROP COLUMN schedule_override");v4.setVersion(4);v4.close();store.close();store=new CardStore(context);
  check(card("old-auto-c").getString("dueDate").equals("2026-10-25"),"v4 todo backfills despite generic user_modified");check(card("old-auto-c").getString("status").equals("todo"),"backfill preserves state");check(card("old-override").getString("dueDate").equals("2026-10-24"),"v4 manual date preserved");
  String relativeA=store.addManual("群","老师","明天18点前"),relativeB=store.addManual("群","老师","明天18点前");
  long anchor=java.time.LocalDate.of(2026,10,22).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
  store.getWritableDatabase().execSQL("UPDATE sources SET received=? WHERE id=?",new Object[]{anchor,relativeA});store.getWritableDatabase().execSQL("UPDATE sources SET received=? WHERE id=?",new Object[]{anchor+86400000L,relativeB});
  JSONArray relativeRows=new JSONArray().put(row("同正文任务").put("deadline","明天18点前"));store.replaceCards(relativeA,relativeRows);store.replaceCards(relativeB,store.cachedExtraction("明天18点前"));
  JSONArray relativeCards=store.cards();String aDate="",bDate="";for(int i=0;i<relativeCards.length();i++){JSONObject c=relativeCards.getJSONObject(i);if(c.getString("sourceId").equals(relativeA))aDate=c.getString("dueDate");if(c.getString("sourceId").equals(relativeB))bDate=c.getString("dueDate");}
  check(aDate.equals("2026-10-23"),"relative deadline uses original receipt date");check(bDate.equals("2026-10-24"),"cache reuse recomputes relative date per source");
  store.close();context.deleteDatabase("xunji-cards.db");result.putString("stream","PASS: "+assertions+" Android SQLite lifecycle, re-extraction, persistence and v2 migration assertions\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){result.putString("stream","FAIL: "+e.toString());finish(Activity.RESULT_CANCELED,result);}}
}
