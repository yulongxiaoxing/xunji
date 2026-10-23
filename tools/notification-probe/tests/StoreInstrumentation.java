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
  v4.execSQL("ALTER TABLE cards DROP COLUMN auto_completed");v4.execSQL("ALTER TABLE cards DROP COLUMN event_data");v4.execSQL("ALTER TABLE cards DROP COLUMN course_data");v4.execSQL("ALTER TABLE cards DROP COLUMN schedule_override");v4.setVersion(4);v4.close();store.close();store=new CardStore(context);
  check(card("old-auto-c").getString("dueDate").equals("2026-10-25"),"v4 todo backfills despite generic user_modified");check(card("old-auto-c").getString("status").equals("todo"),"backfill preserves state");check(card("old-override").getString("dueDate").equals("2026-10-24"),"v4 manual date preserved");
  String relativeA=store.addManual("群","老师","明天18点前"),relativeB=store.addManual("群","老师","明天18点前");
  long anchor=java.time.LocalDate.of(2026,10,22).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
  store.getWritableDatabase().execSQL("UPDATE sources SET received=? WHERE id=?",new Object[]{anchor,relativeA});store.getWritableDatabase().execSQL("UPDATE sources SET received=? WHERE id=?",new Object[]{anchor+86400000L,relativeB});
  JSONArray relativeRows=new JSONArray().put(row("同正文任务").put("deadline","明天18点前"));store.replaceCards(relativeA,relativeRows);store.replaceCards(relativeB,store.cachedExtraction("明天18点前"));
  JSONArray relativeCards=store.cards();String aDate="",bDate="";for(int i=0;i<relativeCards.length();i++){JSONObject c=relativeCards.getJSONObject(i);if(c.getString("sourceId").equals(relativeA))aDate=c.getString("dueDate");if(c.getString("sourceId").equals(relativeB))bDate=c.getString("dueDate");}
  check(aDate.equals("2026-10-23"),"relative deadline uses original receipt date");check(bDate.equals("2026-10-24"),"cache reuse recomputes relative date per source");
  String rawCourse="{}\n[{\"node\":1,\"startTime\":\"08:00\",\"endTime\":\"08:45\"},{\"node\":2,\"startTime\":\"08:55\",\"endTime\":\"09:40\"}]\n{\"startDate\":\"2026-10-19\",\"tableName\":\"测试学期\"}\n[{\"id\":1,\"courseName\":\"模拟电子技术\",\"teacher\":\"测试老师\"}]\n[{\"id\":1,\"day\":5,\"startNode\":1,\"step\":2,\"startWeek\":1,\"endWeek\":4,\"type\":1,\"room\":\"教学楼A101\"}]";
  JSONObject coursePreview=WakeupImport.parse(rawCourse);check(coursePreview.getJSONArray("sessions").length()==2,"odd weeks create exact occurrences");
  JSONObject courseInfo=coursePreview.getJSONArray("sessions").getJSONObject(0).getJSONObject("course");check(courseInfo.getString("startTime").equals("08:00")&&courseInfo.getString("endTime").equals("09:40"),"course interval complete");check(courseInfo.getString("teacher").equals("测试老师")&&courseInfo.getString("location").equals("教学楼A101"),"teacher and room retained");
  JSONObject imported=store.importCourses(coursePreview);check(imported.getInt("added")==2,"course occurrences added");String courseId=coursePreview.getJSONArray("sessions").getJSONObject(0).getString("id");check(card(courseId).getString("category").equals("课程"),"course category");check(card(courseId).getJSONObject("course").getString("endTime").equals("09:40"),"stored course end preserved");
  store.cardState(courseId,"done");check(store.importCourses(coursePreview).getInt("added")==0,"repeat import idempotent");check(card(courseId).getString("status").equals("done"),"repeat import preserves completion");store.cardState(courseId,"deleted");store.importCourses(coursePreview);check(card(courseId).getString("status").equals("deleted"),"repeat import no resurrection");
  try{WakeupImport.parse(rawCourse.replace("\"node\":2","\"node\":3"));throw new AssertionError("missing slot silently skipped");}catch(IllegalArgumentException expected){assertions++;}
  check(store.source(card(courseId).getString("sourceId")).getString("text").equals(rawCourse),"entire timetable source preserved");
  String editedCourse=coursePreview.getJSONArray("sessions").getJSONObject(1).getString("id");JSONObject editCourseData=card(editedCourse).getJSONObject("course");editCourseData.put("teacher","编辑后的老师").put("location","实验楼B202");
  store.updateCard(row("模拟电子技术").put("id",editedCourse).put("category","课程").put("course",editCourseData).put("dueDate",card(editedCourse).getString("dueDate")).put("dueTime","08:00"));check(card(editedCourse).getJSONObject("course").getString("teacher").equals("编辑后的老师"),"structured course edit retained");
  store.updateCard(row("保留用户改分类").put("id",editedCourse).put("category","其他"));check(store.importCourses(coursePreview).getInt("added")==0,"reimport preserves recategorized occurrence");check(card(editedCourse).getString("category").equals("其他"),"category decision preserved");

  try{WakeupImport.parse(rawCourse+"\n{}");throw new AssertionError("unknown extra sections accepted");}catch(IllegalArgumentException expected){assertions++;}
  JSONObject irregular=WakeupImport.parse(rawCourse.replace("\"type\":1","\"type\":0,\"stepWeekList\":[1,4]"));check(irregular.getJSONArray("sessions").getJSONObject(1).getString("date").equals("2026-11-13"),"irregular active week list is respected");
  JSONObject fallbackTeacher=WakeupImport.parse(rawCourse.replace("\"room\":","\"teacher\":\"\",\"room\":"));check(fallbackTeacher.getJSONArray("meetings").getJSONObject(0).getString("teacher").equals("测试老师"),"blank detail teacher keeps known base teacher");
  JSONObject another=WakeupImport.parse(rawCourse.replace("模拟电子技术","大学物理"));check(store.importCourses(another).getInt("added")==2,"different names with same export-local ID are not lost");
  try{store.importCourses(WakeupImport.parse(rawCourse.replace("测试老师","另一老师")));throw new AssertionError("conflicting identity silently ignored");}catch(IllegalArgumentException expected){assertions++;}

  String ics="BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nUID:ics-test-1\r\nSUMMARY:电子技术\r\nDTSTART;TZID=Asia/Shanghai:20261023T080000\r\nDTEND;TZID=Asia/Shanghai:20261023T094000\r\nLOCATION:教学楼A101\r\nDESCRIPTION:老师：测试老师\\n完整课程说明\r\nRRULE:FREQ=WEEKLY;COUNT=3\r\nEXDATE;TZID=Asia/Shanghai:20261030T080000\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n";
  JSONObject ip=IcsImport.parse(ics);check(ip.getJSONArray("sessions").length()==2,"ICS weekly count and exclusion");
  JSONObject ic=ip.getJSONArray("sessions").getJSONObject(0).getJSONObject("course");check(ic.getString("teacher").equals("测试老师")&&ic.getString("endTime").equals("09:40"),"ICS teacher and end time");
  check(ic.getString("description").contains("完整课程说明"),"ICS description intact");check(store.importCourses(ip).getInt("added")==2&&store.importCourses(ip).getInt("added")==0,"ICS store and idempotence");
  check(IcsImport.parse(ics.replace("20261023T080000","20261023T000000Z").replace("DTSTART;TZID=Asia/Shanghai","DTSTART")).getJSONArray("sessions").length()==2,"ICS UTC converted to local time");
  check(IcsImport.parse(ics.replace("20261023T080000","20261023T000000Z").replace("DTSTART;TZID=Asia/Shanghai","DTSTART")).getJSONArray("sessions").getJSONObject(0).getJSONObject("course").getString("startTime").equals("08:00"),"ICS UTC actually converts to China time");
  check(IcsImport.parse(ics.replace("SUMMARY:电子技术","SUMMARY:电子\r\n 技术")).getJSONArray("meetings").getJSONObject(0).getString("name").equals("电子技术"),"ICS folded text lines");
  check(IcsImport.parse(ics.replace("RRULE:FREQ=WEEKLY;COUNT=3", "RRULE:FREQ=WEEKLY;UNTIL=20261106T000000Z")).getJSONArray("sessions").length()==2,"ICS inclusive UNTIL");
  check(IcsImport.parse(ics.replace("RRULE:FREQ=WEEKLY;COUNT=3", "RDATE;TZID=Asia/Shanghai:20261106T080000")).getJSONArray("sessions").length()==2,"ICS explicit extra date");
  check(IcsImport.parse(ics.replace("FREQ=WEEKLY;COUNT=3", "FREQ=WEEKLY;COUNT=3;INTERVAL=2;BYDAY=FR")).getJSONArray("sessions").getJSONObject(1).getString("date").equals("2026-11-06"),"ICS biweekly dates");
  try{IcsImport.parse(ics.replace("FREQ=WEEKLY;COUNT=3","FREQ=DAILY;UNTIL=20361023T000000Z"));throw new AssertionError("overlong recurrence truncated");}catch(IllegalArgumentException expected){assertions++;}
  String dstIcs=ics.replace("Asia/Shanghai","America/New_York").replace("20261023T080000","20260302T023000").replace("20261023T094000","20260302T033000").replace("FREQ=WEEKLY;COUNT=3","FREQ=WEEKLY;COUNT=2;BYDAY=MO");
  check(IcsImport.parse(dstIcs).getJSONArray("sessions").length()==2,"unselected DST Sunday does not reject Monday classes");
  try{IcsImport.parse(ics.replace("FREQ=WEEKLY;COUNT=3","FREQ=MONTHLY;COUNT=3"));throw new AssertionError("unsupported recurrence silently accepted");}catch(IllegalArgumentException expected){assertions++;}
  try{IcsImport.parse(ics.replace("20261023T094000","20261024T094000"));throw new AssertionError("cross-day class truncated");}catch(IllegalArgumentException expected){assertions++;}
  JSONObject recurring=row("每周课程").put("category","课程").put("dueDate","2026-10-23").put("course",new JSONObject().put("location","A101").put("teacher","任课教师").put("startTime","08:00").put("endTime","09:40")).put("repeat",new JSONObject().put("rule","biweekly").put("weekday",5).put("mode","count").put("count",3));
  JSONArray repeatedIds=store.createCards(recurring);check(repeatedIds.length()==3,"manual biweekly occurrences created");check(card(repeatedIds.getString(1)).getString("dueDate").equals("2026-11-06"),"manual repeat exact dates");
  check(card(repeatedIds.getString(0)).getJSONObject("course").getString("teacher").equals("任课教师"),"manual course teacher preserved");
  JSONObject eventRow=row("活动区间").put("category","活动").put("dueDate","2026-10-23").put("event",new JSONObject().put("location","礼堂").put("startTime","14:00").put("endTime","16:00"));String eventId=store.createCard(eventRow);check(card(eventId).getJSONObject("event").getString("endTime").equals("16:00")&&card(eventId).getString("dueTime").equals("14:00"),"event range persisted and ordered by start");
  try{store.setSchedule(eventId,"2026-10-23","15:00");throw new AssertionError("interval desynchronized");}catch(IllegalArgumentException expected){check(card(eventId).getString("dueTime").equals("14:00"),"generic schedule cannot desynchronize event range");}
  check(card(repeatedIds.getString(0)).getJSONObject("course").getJSONObject("repeat").getInt("count")==3,"repeat rule retained on occurrence");
  int beforeBad=store.cards().length();try{store.createCards(new JSONObject(recurring.toString()).put("repeat",new JSONObject().put("rule","weekly").put("weekday",5).put("mode","count").put("count",0)));throw new AssertionError("invalid repeat accepted");}catch(IllegalArgumentException expected){check(store.cards().length()==beforeBad,"invalid repeat is atomic");}
  String memoryRaw="明天下午两点到四点去礼堂参加活动";JSONArray memoryIds=store.saveMemory(memoryRaw,new JSONArray().put(eventRow),System.currentTimeMillis());check(card(memoryIds.getString(0)).getString("status").equals("todo"),"confirmed memory enters todo");check(store.source(card(memoryIds.getString(0)).getString("sourceId")).getString("text").equals(memoryRaw),"memory original immutable");
  int beforeMemory=store.cards().length(),beforeSources=store.sources().length();try{store.saveMemory("不能部分保存",new JSONArray().put(eventRow).put(row("")),System.currentTimeMillis());throw new AssertionError("partial memory saved");}catch(IllegalArgumentException expected){check(store.cards().length()==beforeMemory&&store.sources().length()==beforeSources,"memory transaction rolls back source and every card");}
  String memorySource=card(memoryIds.getString(0)).getString("sourceId");store.getWritableDatabase().execSQL("UPDATE sources SET extraction_version=0 WHERE id=?",new Object[]{memorySource});store.close();store=new CardStore(context);check(store.source(memorySource).getString("aiState").equals("ready"),"memory excluded from automatic extraction upgrades");
  SQLiteDatabase oldV6=store.getWritableDatabase();oldV6.execSQL("ALTER TABLE cards DROP COLUMN auto_completed");oldV6.execSQL("ALTER TABLE cards DROP COLUMN event_data");oldV6.setVersion(6);store.close();store=new CardStore(context);check(card(repeatedIds.getString(0)).getJSONObject("course").getString("teacher").equals("任课教师"),"v6 to v7 preserves imported/manual course metadata");
  JSONObject autoRow=row("隔离课程自动完成").put("category","课程").put("dueDate","2099-10-23").put("course",new JSONObject().put("startTime","08:00").put("endTime","09:40").put("teacher","").put("location",""));String courseAutoId=store.createCard(autoRow);
  long autoEnd=java.time.LocalDateTime.parse("2099-10-23T09:40:00").atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();store.completeEndedCourses(autoEnd-1);check(card(courseAutoId).getString("status").equals("todo"),"course stays scheduled until end");store.completeEndedCourses(autoEnd);check(card(courseAutoId).getString("status").equals("done")&&card(courseAutoId).getBoolean("autoCompleted"),"course auto completes at end");check(card(eventId).getString("status").equals("todo"),"ordinary events never auto complete");
  JSONObject rescheduled=new JSONObject(autoRow.toString()).put("id",courseAutoId).put("dueDate","2100-10-23");store.updateCard(rescheduled);check(card(courseAutoId).getString("status").equals("todo")&&!card(courseAutoId).getBoolean("autoCompleted"),"editing ended class into future reopens auto completion");
  boolean privateAudit=false;for(String asset:context.getAssets().list(""))if(asset.equals("private-course.ics"))privateAudit=true;
  if(privateAudit){String userRaw;try(java.io.InputStream in=context.getAssets().open("private-course.ics");java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);userRaw=new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);}
    JSONObject audit=IcsImport.parse(userRaw);JSONArray audited=audit.getJSONArray("sessions");int total=audited.length(),monday=0,wednesday=0;java.util.List<String> wedDates=new java.util.ArrayList<>();
    for(int i=0;i<total;i++){JSONObject session=audited.getJSONObject(i),course=session.getJSONObject("course");if(!course.getString("name").equals("模拟电子技术"))continue;int weekday=java.time.LocalDate.parse(session.getString("date")).getDayOfWeek().getValue();if(weekday==1)monday++;if(weekday==3){wednesday++;wedDates.add(session.getString("date"));}}
    check(monday==17&&wednesday==9,"real export preserves weekly Monday and alternating Wednesday");check(wedDates.contains("2026-10-28")&&!wedDates.contains("2026-10-21"),"real export Wednesday parity exact");
    JSONObject auditResult=store.importCourses(audit);int storedWed=0;JSONArray saved=store.cards();for(int i=0;i<saved.length();i++){JSONObject c=saved.getJSONObject(i);if(c.getString("title").equals("模拟电子技术")&&java.time.LocalDate.parse(c.getString("dueDate")).getDayOfWeek().getValue()==3)storedWed++;}check(storedWed==9,"real file Wednesday records survive SQLite import");
    android.util.Log.i("XunjiCourseAudit","sessions="+total+" Monday="+monday+" Wednesday="+wednesday+" WednesdayDates="+wedDates.toString()+" added="+auditResult.getInt("added"));
  }
  store.close();context.deleteDatabase("xunji-cards.db");result.putString("stream","PASS: "+assertions+" Android SQLite lifecycle, re-extraction, persistence and v2 migration assertions\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){result.putString("stream","FAIL: "+e.toString());finish(Activity.RESULT_CANCELED,result);}}
}
