package cn.xunji.probe;

import org.json.JSONArray;
import org.json.JSONObject;

/** Runs only inside StoreInstrumentation's isolated Android package. */
final class GrowthStoreChecks {
    interface Attempt{void run() throws Exception;}
    static void rejects(StoreInstrumentation t,Attempt action,String message)throws Exception{
        try{action.run();throw new AssertionError(message);}catch(IllegalArgumentException expected){t.check(true,message);}
    }
    static JSONObject record(String title)throws Exception{return new JSONObject().put("title",title).put("category","竞赛").put("date","").put("result","").put("sourceCardId","");}
    static JSONObject find(GrowthStore growth,String id)throws Exception{
        JSONArray rows=growth.records();for(int i=0;i<rows.length();i++)if(id.equals(rows.getJSONObject(i).getString("id")))return rows.getJSONObject(i);throw new AssertionError("growth missing");
    }
    static void run(StoreInstrumentation t)throws Exception{
        t.reset();GrowthStore growth=new GrowthStore(t.store.getWritableDatabase());
        t.check(growth.records().length()==0,"fresh archive empty");
        String manual=growth.save(record("手动经历"));String another=growth.save(record("第二条手动经历"));
        t.check(!manual.equals(another)&&growth.records().length()==2,"manual records do not collide on null source");
        t.check(find(growth,manual).getString("date").isEmpty()&&find(growth,manual).getString("result").isEmpty(),"unknown date and award stay empty");
        rejects(t,()->growth.save(record(" ")),"blank growth rejected");
        rejects(t,()->growth.save(record("坏日期").put("date","2026-02-29")),"invalid growth date rejected");
        t.check(growth.records().length()==2,"invalid save atomic");
        String source=t.store.createCard(t.row("实际参与数学竞赛").put("category","竞赛"));
        JSONObject linked=record("数学竞赛经历").put("sourceCardId",source).put("participationConfirmed",true);
        rejects(t,()->growth.save(linked),"unfinished source rejected");
        t.store.cardState(source,"done");
        rejects(t,()->growth.save(new JSONObject(linked.toString()).put("participationConfirmed",false)),"participation consent required");
        String fromTask=growth.save(linked);
        t.check(find(growth,fromTask).getString("sourceCardId").equals(source),"source link persisted");
        t.check(find(growth,fromTask).getString("sourceTitle").equals("实际参与数学竞赛"),"source title preserved");
        t.check(find(growth,fromTask).getString("result").isEmpty(),"completion does not invent award");
        rejects(t,()->growth.save(linked),"duplicate source rejected");
        growth.state(fromTask,"deleted");rejects(t,()->growth.save(linked),"deleted source record not duplicated");
        t.check(find(growth,fromTask).getString("state").equals("deleted"),"growth removal reversible");
        growth.state(fromTask,"active");t.store.cardState(source,"todo");
        t.check(find(growth,fromTask).getString("sourceState").equals("todo"),"undo completion retains growth and exposes current state");
        growth.save(new JSONObject(linked.toString()).put("id",fromTask).put("title","补充后的经历").put("result","实际参加，结果待公布").put("date","2026-10-28"));
        t.check(find(growth,fromTask).getString("result").equals("实际参加，结果待公布"),"edit experience independent of source status");
        t.check(t.card(source).getString("status").equals("todo")&&t.card(source).getString("title").equals("实际参与数学竞赛"),"growth edit does not mutate task");
        rejects(t,()->growth.save(record("篡改关联").put("id",fromTask)),"source cannot be reassigned");
        rejects(t,()->growth.save(record("缺失").put("id","missing")),"missing growth cannot silently save");
        rejects(t,()->growth.state(manual,"invalid"),"invalid archive state rejected");
        t.store.cardState(source,"deleted");t.check(find(growth,fromTask).getString("sourceState").equals("deleted"),"deleted source keeps experience");
        String signup=t.store.createCard(t.row("竞赛报名").put("category","竞赛"));t.store.cardState(signup,"done");
        rejects(t,()->growth.save(record("报名不能当参赛").put("sourceCardId",signup).put("participationConfirmed",true)),"registration cannot become participation");
        rejects(t,()->growth.save(record("不存在来源").put("sourceCardId","missing").put("participationConfirmed",true)),"missing source rejected");
        t.store.close();t.store=new CardStore(t.context);GrowthStore reopened=new GrowthStore(t.store.getWritableDatabase());
        t.check(find(reopened,fromTask).getString("title").equals("补充后的经历")&&reopened.records().length()==3,"archive persists after restart");
        t.check(find(reopened,fromTask).getString("date").equals("2026-10-28"),"experience date survives restart");
        t.reset();String legacy=t.store.createCard(t.row("v10原事项"));String originalSource=t.card(legacy).getString("sourceId");
        String original=t.store.source(originalSource).getString("text");
        t.store.getWritableDatabase().execSQL("DROP TABLE growth_records");t.store.getWritableDatabase().setVersion(10);t.store.close();t.store=new CardStore(t.context);
        GrowthStore migrated=new GrowthStore(t.store.getWritableDatabase());t.check(migrated.records().length()==0,"v10 migration creates empty archive");
        t.check(t.card(legacy).getString("title").equals("v10原事项")&&t.store.source(originalSource).getString("text").equals(original),"v10 upgrade preserves original task and text");
        migrated.save(record("升级后经历"));t.check(migrated.records().length()==1,"archive writable after migration");
    }
}
