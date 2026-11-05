package cn.xunji.probe;
import org.json.*;
final class GrowthCandidateChecks {
 static JSONObject yes(String category)throws Exception{return new JSONObject().put("candidate",true).put("category",category).put("reason","可能形成相关成长经历，需要本人确认");}
 static void run(StoreInstrumentation t)throws Exception{
  t.reset();String id=t.store.createCard(t.row("数学竞赛报名").put("category","竞赛").put("dueDate","2000-01-01").put("dueTime","10:00"));
  GrowthCandidateStore queue=new GrowthCandidateStore(t.store.getWritableDatabase());queue.sync(t.store.cards());
  t.check(t.card(id).getString("status").equals("done"),"registration deadline automatically completes");
  t.check(queue.rows().length()==1&&queue.next()!=null,"completed task queued for AI not formal archive");
  JSONObject first=queue.next();queue.apply(id,first.getString("input"),yes("竞赛"));
  t.check(queue.rows().getJSONObject(0).getString("category").equals("竞赛"),"AI recommends candidate category");
  t.check(new GrowthStore(t.store.getReadableDatabase()).records().length()==0,"AI decision never saves formal growth");
  queue.sync(t.store.cards());t.check(queue.rows().length()==1&&queue.next()==null,"rescan deduplicates AI decision");
  JSONObject row=new JSONObject().put("title","数学竞赛实际参与").put("category","竞赛").put("date","").put("result","").put("sourceCardId",id).put("candidateId",id);
  GrowthStoreChecks.rejects(t,()->new GrowthStore(t.store.getWritableDatabase()).save(row),"candidate still requires actual-participation confirmation");
  String g=new GrowthStore(t.store.getWritableDatabase()).save(row.put("participationConfirmed",true));
  t.check(!g.isEmpty()&&queue.rows().length()==0,"confirmed candidate transaction creates linked record and leaves queue");
  GrowthStoreChecks.rejects(t,()->new GrowthStore(t.store.getWritableDatabase()).save(row),"same candidate cannot be confirmed twice");
  String homework=t.store.createCard(t.row("普通作业").put("dueDate","2000-01-01"));queue.sync(t.store.cards());JSONObject task=queue.next();
  t.check(task!=null&&task.getString("id").equals(homework),"date-only homework completes and AI may consider it");
  queue.apply(homework,task.getString("input"),new JSONObject().put("candidate",false));queue.sync(t.store.cards());t.check(queue.rows().length()==0&&queue.next()==null,"AI rejection is durable and does not become candidate");
  String other=t.store.createCard(t.row("校园志愿服务").put("category","活动").put("dueDate","2000-01-01"));queue.sync(t.store.cards());JSONObject old=queue.next();
  t.store.updateCard(t.row("修改后的校园志愿服务").put("id",other).put("category","活动").put("dueDate","2000-01-01"));queue.sync(t.store.cards());queue.apply(other,old.getString("input"),yes("志愿"));t.check(queue.next()!=null,"late AI result cannot overwrite edited-source generation");
  JSONObject current=queue.next();queue.error(other,current.getString("input"));t.check(queue.rows().getJSONObject(0).getString("state").equals("error"),"AI failure retained separately");queue.retry();t.check(queue.next()!=null,"explicit retry resumes failed judgment");
  queue.ignore(other);queue.apply(other,current.getString("input"),yes("志愿"));queue.sync(t.store.cards());t.check(queue.rows().length()==0,"ignore persists and late result cannot resurrect");
  String future=t.store.createCard(t.row("未来事项").put("dueDate","2099-01-01"));queue.sync(t.store.cards());t.check(queue.rows().length()==0,"future todos do not enter queue");
  t.store.setSchedule(homework,"2099-01-01","");t.check(t.card(homework).getString("status").equals("todo"),"rescheduled automatic deadline reopens");
  String undone=t.store.createCard(t.row("竞赛候选").put("category","竞赛").put("dueDate","2000-01-01"));queue.sync(t.store.cards());JSONObject pending=queue.next();queue.apply(undone,pending.getString("input"),yes("竞赛"));
  t.store.setSchedule(undone,"2099-01-01","");t.check(queue.rows().length()==0,"reopened source hides old candidate");
  JSONObject stale=new JSONObject().put("title","旧候选").put("category","竞赛").put("sourceCardId",undone).put("candidateId",undone).put("participationConfirmed",true);
  GrowthStoreChecks.rejects(t,()->new GrowthStore(t.store.getWritableDatabase()).save(stale),"formal save rechecks live source completion");
  t.store.getWritableDatabase().execSQL("DROP TABLE growth_candidates");t.store.getWritableDatabase().setVersion(12);t.store.close();t.store=new CardStore(t.context);
  t.check(t.store.getReadableDatabase().getVersion()==13,"candidate schema upgrades v12 to v13");
  t.check(new GrowthStore(t.store.getReadableDatabase()).records().length()==1,"v12 upgrade preserves existing confirmed growth");
  new GrowthCandidateStore(t.store.getWritableDatabase()).sync(t.store.cards());
  t.check(t.store.cards().length()>=5,"v12 upgrade preserves existing source tasks");
 }
}
