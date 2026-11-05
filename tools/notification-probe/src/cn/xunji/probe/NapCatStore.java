package cn.xunji.probe;
import android.content.*;
import org.json.*;
import java.util.*;
final class NapCatStore {
    static final Object LOCK=new Object();
    private final SharedPreferences p;
    NapCatStore(Context c){p=c.getSharedPreferences("napcat",0);}
    boolean enabled(){return p.getBoolean("enabled",false);}
    void enabled(boolean value){synchronized(LOCK){p.edit().putBoolean("enabled",value).putLong("epoch",epoch()+1).apply();}}
    long epoch(){return p.getLong("epoch",0);}
    long revision(String id){return p.getLong("revision:"+id,0);}
    boolean preferred(){return true;}
    JSONArray groups(){try{return new JSONArray(p.getString("groups","[]"));}catch(Exception e){return new JSONArray();}}
    void groups(JSONArray value){p.edit().putString("groups",value.toString()).apply();}
    Set<String> followed(){return new HashSet<>(p.getStringSet("followed",new HashSet<String>()));}
    long since(String group){return p.getLong("since:"+group,Long.MAX_VALUE);}
    Set<String> baseline(String group){return new HashSet<>(p.getStringSet("baseline:"+group,new HashSet<String>()));}
    long requestFollow(String id){synchronized(LOCK){long rev=revision(id)+1;p.edit().putLong("revision:"+id,rev).putBoolean("pending:"+id,true).putLong("since:"+id,System.currentTimeMillis()/1000).remove(progressKey(id)).remove("syncPhase:"+id).remove("syncDetail:"+id).apply();return rev;}}
    boolean follow(String group,JSONArray history,long rev)throws Exception{synchronized(LOCK){
        if(revision(group)!=rev||!p.getBoolean("pending:"+group,false))return false;
        Set<String> ids=new HashSet<>();NapCatBackfill.Entry newest=null;
        for(NapCatBackfill.Entry m:NapCatClient.entries(history,group)){if(m.time>=since(group))continue;ids.add(m.id);if(newest==null||m.time>newest.time||m.time==newest.time&&m.order>newest.order)newest=m;}
        NapCatBackfill.State state=new NapCatBackfill.State();if(newest!=null){state.cursorId=newest.id;state.cursorTime=newest.time;state.cursorOrder=newest.order;}
        Set<String> groups=followed();groups.add(group);
        if(!p.edit().putStringSet("followed",groups).putStringSet("baseline:"+group,ids).putBoolean("pending:"+group,false).putString(progressKey(group),encode(state).toString()).commit())throw new IllegalStateException("关注设置保存失败，请重试");return true;
    }}
    void failFollow(String group,long rev){synchronized(LOCK){if(revision(group)==rev)p.edit().putBoolean("pending:"+group,false).apply();}}
    JSONArray pending(){JSONArray ids=new JSONArray();JSONArray groups=groups();for(int i=0;i<groups.length();i++){String id=groups.optJSONObject(i).optString("group_id");if(p.getBoolean("pending:"+id,false))ids.put(id);}return ids;}
    void unfollow(String id){synchronized(LOCK){Set<String> groups=followed();groups.remove(id);p.edit().putStringSet("followed",groups).putBoolean("pending:"+id,false).putLong("revision:"+id,revision(id)+1).apply();}}
    boolean saveIfAllowed(String id,long epoch,long rev,java.util.concurrent.Callable<Boolean> save)throws Exception{synchronized(LOCK){return enabled()&&epoch()==epoch&&revision(id)==rev&&followed().contains(id)&&save.call();}}
    boolean bindAccount(String account){synchronized(LOCK){if(!account.matches("[1-9][0-9]{0,19}"))return false;String old=p.getString("account","");if(!old.isEmpty())return old.equals(account);if(!p.edit().putString("account",account).commit())throw new IllegalStateException("账号设置尚未保存，请重试");return true;}}
    private String progressKey(String group){return "sync:"+p.getString("account","")+":"+group;}
    boolean current(String account,String group,long epoch,long rev){synchronized(LOCK){return enabled()&&epoch()==epoch&&revision(group)==rev&&followed().contains(group)&&account.equals(p.getString("account",""));}}
    boolean saveIfCurrent(String account,String group,long epoch,long rev,java.util.concurrent.Callable<Boolean> save)throws Exception{synchronized(LOCK){return current(account,group,epoch,rev)&&save.call();}}
    private JSONObject encode(NapCatBackfill.State s)throws Exception{return new JSONObject().put("cursorId",s.cursorId).put("cursorTime",s.cursorTime).put("cursorOrder",s.cursorOrder).put("checkedThrough",s.checkedThrough).put("resumeAnchor",s.resumeAnchor).put("headId",s.headId).put("headTime",s.headTime).put("headOrder",s.headOrder).put("batchEnd",s.batchEnd);}
    NapCatBackfill.State progress(String group)throws Exception{synchronized(LOCK){
        NapCatBackfill.State s=new NapCatBackfill.State();String value=p.getString(progressKey(group),"");if(value.isEmpty())return s;
        JSONObject j;try{j=new JSONObject(value);}catch(Exception e){throw new IllegalStateException("同步进度无法读取，已停止本群补取");}
        s.cursorId=j.optString("cursorId");s.cursorTime=j.optLong("cursorTime");s.cursorOrder=j.optLong("cursorOrder");s.checkedThrough=j.optLong("checkedThrough");s.resumeAnchor=j.optString("resumeAnchor");s.headId=j.optString("headId");s.headTime=j.optLong("headTime");s.headOrder=j.optLong("headOrder");s.batchEnd=j.optLong("batchEnd");return s;
    }}
    void saveProgress(String account,String group,long epoch,long rev,NapCatBackfill.State s)throws Exception{synchronized(LOCK){if(!current(account,group,epoch,rev))throw new IllegalStateException("同步已暂停或关注已改变，进度保留");if(!p.edit().putString(progressKey(group),encode(s).toString()).commit())throw new IllegalStateException("进度保存失败，下次将去重重试");}}
    void groupStatus(String account,String group,long epoch,long rev,String phase,String detail){synchronized(LOCK){if(current(account,group,epoch,rev))p.edit().putString("syncPhase:"+group,phase).putString("syncDetail:"+group,detail).apply();}}
    void statusForSession(long epoch,String value){synchronized(LOCK){if(enabled()&&epoch()==epoch)status(value);}}
    String groupName(String id){JSONArray groups=groups();for(int i=0;i<groups.length();i++){JSONObject group=groups.optJSONObject(i);if(group!=null&&id.equals(group.optString("group_id")))return group.optString("group_name",id);}return id;}
    void status(String value){p.edit().putString("status",value).apply();}
    String status(){return p.getString("status","等待连接电脑 QQ 接入端");}
    JSONObject snapshot(Context c)throws Exception{
        JSONArray sync=new JSONArray();for(String id:followed()){NapCatBackfill.State s=progress(id);sync.put(new JSONObject().put("groupId",id).put("groupName",groupName(id)).put("since",since(id)).put("checkedThrough",s.checkedThrough).put("lastMessageTime",s.cursorTime).put("batchEnd",s.batchEnd).put("phase",p.getString("syncPhase:"+id,"waiting")).put("detail",p.getString("syncDetail:"+id,"等待连接后检查历史")).put("resuming",!s.resumeAnchor.isEmpty()));}
        return new JSONObject().put("enabled",enabled()).put("groups",groups()).put("followed",new JSONArray(followed())).put("pending",pending()).put("status",status()).put("sync",sync).put("hasToken",new ApiCredential(c,"napcat-credential").configured());
    }
}
