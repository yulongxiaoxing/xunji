package cn.xunji.probe;
import org.json.*;
final class ScheduleCompletionChecks {
 static JSONObject event(StoreInstrumentation t,String title,String category)throws Exception{return t.row(title).put("category",category).put("dueDate","2099-11-04").put("dueTime","10:00").put("event",new JSONObject().put("startTime","10:00").put("endTime","11:00").put("location","A203"));}
 static void run(StoreInstrumentation t)throws Exception{
  t.reset();String activity=t.store.createCard(event(t,"活动","活动")),exam=t.store.createCard(event(t,"考试","考试")),competition=t.store.createCard(event(t,"竞赛","竞赛"));
  String deadline=t.store.createCard(t.row("过期作业").put("dueDate","2000-01-01").put("dueTime","10:00"));
  String deleted=t.store.createCard(event(t,"删除活动","活动"));t.store.cardState(deleted,"deleted");
  String manual=t.store.createCard(event(t,"手动完成","活动"));t.store.cardState(manual,"done");
  long end=CoursePolicy.endMillis("2099-11-04","11:00");t.check(t.store.nextCourseEnd(end-1)==end,"background next end includes events and exams");
  t.store.completeEndedCourses(end-1);t.check(t.card(activity).getString("status").equals("todo"),"event remains todo before end");
  t.store.completeEndedCourses(end);for(String id:new String[]{activity,exam})t.check(t.card(id).getString("status").equals("done")&&t.card(id).getBoolean("autoCompleted"),"event/exam automatically ends at boundary");
  t.check(t.card(competition).getString("status").equals("done"),"competition now automatically completes");t.check(t.card(deadline).getString("status").equals("done"),"overdue homework now automatically completes");
  t.check(t.card(deleted).getString("status").equals("deleted"),"deleted never completes or resurrects");t.check(!t.card(manual).getBoolean("autoCompleted"),"manual completion not relabeled automatic");
  t.store.close();t.store=new CardStore(t.context);t.check(t.card(activity).getBoolean("autoCompleted"),"ended history persists on reopen");
  t.store.updateCard(event(t,"改期活动","活动").put("id",activity).put("dueDate","2100-11-04"));t.check(t.card(activity).getString("status").equals("todo")&&!t.card(activity).getBoolean("autoCompleted"),"rescheduled automatic event reopens");
  t.store.updateCard(event(t,"手动完成改期","活动").put("id",manual).put("dueDate","2100-11-04"));t.check(t.card(manual).getString("status").equals("done"),"manual completion preserved when edited");
  t.store.updateCard(t.row("重分类未来课程").put("id",manual).put("category","课程").put("dueDate","2100-11-04").put("dueTime","10:00").put("course",new JSONObject().put("startTime","10:00").put("endTime","11:00").put("location","A203").put("teacher","")));
  t.check(t.card(manual).getString("status").equals("todo"),"reclassifying as future course keeps existing no-premature-completion rule");
 }
}
