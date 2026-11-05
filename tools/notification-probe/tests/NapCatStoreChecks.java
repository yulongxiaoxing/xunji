package cn.xunji.probe;
import org.json.*;
final class NapCatStoreChecks {
 static void run(StoreInstrumentation t)throws Exception {
  t.context.getSharedPreferences("napcat",0).edit().clear().commit();t.reset();
  NapCatClient switched=new NapCatClient("test-token"){
      String remote="12345";
      Object call(String action,JSONObject params)throws Exception{
          if(action.equals("get_status"))return new JSONObject().put("online",true).put("good",true);
          if(action.equals("get_login_info"))return new JSONObject().put("user_id",remote);
          if(action.equals("get_group_msg_history")){remote="99999";return new JSONObject().put("messages",new JSONArray().put(new JSONObject().put("self_id","99999").put("group_id","23456").put("message_id","new").put("time",100)));}
          throw new AssertionError("unexpected action");
      }
  };
  switched.login();boolean accountChanged=false;try{switched.history("23456");}catch(IllegalStateException expected){accountChanged=true;}t.check(accountChanged,"mid-request remote account switch rejects page before persistence");
  NapCatStore n=new NapCatStore(t.context);n.enabled(true);t.check(n.bindAccount("12345"),"bind first account");
  long rev=n.requestFollow("23456");long since=n.since("23456");
  JSONArray baseline=new JSONArray().put(new JSONObject().put("message_id","old").put("time",since-2).put("real_seq","1"))
      .put(new JSONObject().put("message_id","during-follow").put("time",since+1).put("real_seq","2"))
      .put(new JSONObject().put("message_id","same-second").put("time",since).put("real_seq","3"));
  t.check(n.follow("23456",baseline,rev),"follow initializes baseline");long epoch=n.epoch();
  NapCatBackfill.State state=n.progress("23456");t.check(state.cursorId.equals("old"),"baseline checkpoint retained");
  t.check(!n.baseline("23456").contains("during-follow")&&!n.baseline("23456").contains("same-second"),"messages arriving during follow and same second remain eligible");
  state.headId="new";state.headTime=since+1;state.headOrder=2;state.batchEnd=since+10;state.resumeAnchor="middle";
  n.saveProgress("12345","23456",epoch,rev,state);
  NapCatStore reopened=new NapCatStore(t.context);t.check(reopened.progress("23456").resumeAnchor.equals("middle"),"page continuation survives reopen");
  t.check(reopened.progress("23456").cursorId.equals("old")&&reopened.progress("23456").checkedThrough==0,"partial scan cannot mark time complete");
  t.check(!reopened.bindAccount("99999"),"account change cannot reuse authorization");
  String body="明天交作业，保留完整要求";
  t.check(t.store.ensureNapCat("12345","23456","new","测试群","测试人",body,100000),"first source saved");
  t.check(!t.store.ensureNapCat("12345","23456","new","测试群","测试人",body,100000),"duplicate source confirmed without reinsert");
  t.check(t.store.sources().length()==1,"duplicate does not multiply sources");
  JSONObject source=t.store.sources().getJSONObject(0);t.store.replaceCards(source.getString("id"),new JSONArray().put(t.row("我的标题")));String id=t.id(0);t.store.cardState(id,"ignored");
  t.store.ensureNapCat("12345","23456","new","改群名","另一人","不同重复文本",100000);
  t.check(t.store.source(source.getString("id")).getString("text").equals(body),"replay never replaces original");t.check(t.card(id).getString("status").equals("ignored"),"replay keeps user decision");
  t.check(t.store.napCatStats().getInt("ready")==1,"AI progress independent from collection checkpoint");
  n.unfollow("23456");boolean stale=false;try{n.saveProgress("12345","23456",epoch,rev,state);}catch(IllegalStateException expected){stale=true;}t.check(stale,"unfollow rejects in-flight checkpoint");
  t.check(!n.saveIfCurrent("12345","23456",epoch,rev,()->{throw new AssertionError("must not save");}),"unfollow rejects in-flight source");
  rev=n.requestFollow("23456");n.follow("23456",baseline,rev);t.check(n.progress("23456").headId.isEmpty(),"refollow clears obsolete continuation");
  epoch=n.epoch();n.enabled(false);stale=false;try{n.saveProgress("12345","23456",epoch,rev,state);}catch(IllegalStateException expected){stale=true;}t.check(stale,"pause rejects old epoch");
  t.context.getSharedPreferences("napcat",0).edit().clear().commit();
  boolean privateFixture=false;for(String asset:t.context.getAssets().list(""))if(asset.equals("private-qq-history.json"))privateFixture=true;
  if(privateFixture){
      t.reset();String raw;try(java.io.InputStream in=t.context.getAssets().open("private-qq-history.json");java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);raw=new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);}
      JSONArray rows=new JSONObject(raw).getJSONObject("data").getJSONArray("messages");String group=rows.getJSONObject(0).getString("group_id"),account=rows.getJSONObject(0).getString("self_id");
      java.util.List<NapCatBackfill.Entry> entries=NapCatClient.entries(rows,group);NapCatBackfill.Entry first=entries.get(0);for(NapCatBackfill.Entry e:entries)if(e.time<first.time)first=e;
      NapCatBackfill.State initial=new NapCatBackfill.State();initial.cursorId=first.id;initial.cursorTime=first.time;initial.cursorOrder=first.order;
      long boundary=java.time.ZonedDateTime.parse("2026-10-28T00:00:00+08:00").toEpochSecond();int expected=0;for(NapCatBackfill.Entry e:entries)if(e.time>=boundary&&!NapCatClient.text((JSONObject)e.payload).trim().isEmpty())expected++;
      NapCatBackfill.Source feed=new NapCatBackfill.Source(){public java.util.List<NapCatBackfill.Entry> latest(){return entries;}public java.util.List<NapCatBackfill.Entry> older(String anchor){return entries;}};
      NapCatBackfill.Result result=NapCatBackfill.scan(feed,e->{String text=NapCatClient.text((JSONObject)e.payload);if(!text.trim().isEmpty())t.store.ensureNapCat(account,group,e.id,"私有测试群","",text,e.time*1000);},s->{},initial,boundary,System.currentTimeMillis()/1000,20,()->true);
      t.check(result.complete,"private history reaches earlier checkpoint");t.check(t.store.sources().length()==expected,"private fixture text count preserved through scanner and SQLite");
      NapCatBackfill.scan(feed,e->{throw new AssertionError("must not replay idle snapshot");},s->{},result.state,boundary,System.currentTimeMillis()/1000,20,()->true);
      t.check(t.store.sources().length()==expected,"private fixture second scan has no duplicate sources");
      t.check(t.store.napCatStats().getInt("pending")==expected,"private fixture remains pending without calling AI");
  }
 }
}
