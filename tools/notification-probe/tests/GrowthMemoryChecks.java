package cn.xunji.probe;
import org.json.*;
/** Model results and storage boundaries tested only in the isolated package. */
final class GrowthMemoryChecks {
 static JSONObject model(String title,String month,JSONObject details)throws Exception{return new JSONObject().put("experienced",true).put("title",title).put("monthText",month).put("details",details);}
 static void run(StoreInstrumentation t)throws Exception{
  t.reset();long base=java.time.ZonedDateTime.parse("2026-11-05T09:00:00+08:00").toInstant().toEpochMilli();
  String text="上个月参加校园迎新志愿服务，服务单位是学生会，服务四小时。";
  JSONObject raw=model("校园迎新志愿服务","上个月",new JSONObject().put("organizer","学生会").put("hours","四小时").put("award","一等奖"));
  JSONObject g=GrowthMemoryExtractor.normalize(raw.put("category","竞赛"),text,"志愿",base);
  t.check(g.getString("category").equals("志愿"),"growth memory category fixed to entry");
  t.check(g.getString("date").equals("2026-10"),"relative month anchored at request time");
  t.check(g.getJSONObject("details").getString("hours").equals("4"),"explicit Chinese hours normalized");
  t.check(g.getJSONObject("details").getString("organizer").equals("学生会"),"organization grounded");
  t.check(!g.getJSONObject("details").has("award"),"cross-category model field removed");
  t.check(g.getString("result").equals(text),"full original retained in editable description");
  t.check(t.store.cards().length()==0&&new GrowthStore(t.store.getReadableDatabase()).records().length()==0,"AI normalization never writes tasks or growth");
  JSONObject unknown=GrowthMemoryExtractor.normalize(model("志愿服务","2026年11月",new JSONObject().put("hours","100").put("organizer","学校")),"参加志愿服务。","志愿",base);
  t.check(unknown.getString("date").isEmpty(),"model-invented date cleared");
  t.check(unknown.getJSONObject("details").getString("hours").isEmpty(),"model-invented hours cleared");
  t.check(unknown.getJSONObject("details").getString("organizer").isEmpty(),"model-invented organizer cleared");
  JSONObject contest=GrowthMemoryExtractor.normalize(model("数学竞赛","",new JSONObject().put("award","二等奖")),"参加数学竞赛，尚未公布二等奖结果。","竞赛",base);
  t.check(contest.getJSONObject("details").getString("award").isEmpty(),"not-yet-published awards cannot be filled");
  JSONObject won=GrowthMemoryExtractor.normalize(model("数学竞赛","2026年10月",new JSONObject().put("award","二等奖")),"2026年10月参加数学竞赛，获得二等奖。","竞赛",base);
  t.check(won.getJSONObject("details").getString("award").equals("二等奖"),"explicit award retained");
  GrowthStoreChecks.rejects(t,()->GrowthMemoryExtractor.normalize(raw.put("experienced",false),text,"志愿",base),"planned or ambiguous experience not silently drafted");
  GrowthStoreChecks.rejects(t,()->GrowthMemoryExtractor.normalize(model("编造的经历","",new JSONObject()),text,"志愿",base),"ungrounded title rejected");
  JSONObject malformed=GrowthMemoryExtractor.normalize(model("志愿服务","",new JSONObject().put("hours",99).put("organizer",new JSONArray())),"参加志愿服务。","志愿",base);
  t.check(malformed.getJSONObject("details").getString("hours").isEmpty()&&malformed.getJSONObject("details").getString("organizer").isEmpty(),"non-string model fields ignored");
  String id=new GrowthStore(t.store.getWritableDatabase()).save(g);
  t.check(GrowthStoreChecks.find(new GrowthStore(t.store.getReadableDatabase()),id).getString("title").equals("校园迎新志愿服务"),"approved draft uses existing growth save");
  t.check(t.store.cards().length()==0,"growth save creates no todo");
 }
}
