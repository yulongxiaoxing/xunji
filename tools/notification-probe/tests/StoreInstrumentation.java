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
  store.close();context.deleteDatabase("xunji-cards.db");result.putString("stream","PASS: "+assertions+" Android SQLite lifecycle, re-extraction, persistence and v2 migration assertions\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){result.putString("stream","FAIL: "+e.toString());finish(Activity.RESULT_CANCELED,result);}}
}
