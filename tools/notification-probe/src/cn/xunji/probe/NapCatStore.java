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
    long requestFollow(String id){synchronized(LOCK){long rev=revision(id)+1;p.edit().putLong("revision:"+id,rev).putBoolean("pending:"+id,true).putLong("since:"+id,System.currentTimeMillis()/1000).apply();return rev;}}
    boolean follow(String group,JSONArray history,long rev){synchronized(LOCK){if(revision(group)!=rev||!p.getBoolean("pending:"+group,false))return false;Set<String> ids=new HashSet<>();for(int i=0;i<history.length();i++){JSONObject m=history.optJSONObject(i);if(m!=null)ids.add(m.optString("message_id"));}Set<String> groups=followed();groups.add(group);p.edit().putStringSet("followed",groups).putStringSet("baseline:"+group,ids).putBoolean("pending:"+group,false).apply();return true;}}
    void failFollow(String group,long rev){synchronized(LOCK){if(revision(group)==rev)p.edit().putBoolean("pending:"+group,false).apply();}}
    JSONArray pending(){JSONArray ids=new JSONArray();JSONArray groups=groups();for(int i=0;i<groups.length();i++){String id=groups.optJSONObject(i).optString("group_id");if(p.getBoolean("pending:"+id,false))ids.put(id);}return ids;}
    void unfollow(String id){synchronized(LOCK){Set<String> groups=followed();groups.remove(id);p.edit().putStringSet("followed",groups).putBoolean("pending:"+id,false).putLong("revision:"+id,revision(id)+1).apply();}}
    boolean saveIfAllowed(String id,long epoch,long rev,java.util.concurrent.Callable<Boolean> save)throws Exception{synchronized(LOCK){return enabled()&&epoch()==epoch&&revision(id)==rev&&followed().contains(id)&&save.call();}}
    void statusForSession(long epoch,String value){synchronized(LOCK){if(enabled()&&epoch()==epoch)status(value);}}
    String groupName(String id){JSONArray groups=groups();for(int i=0;i<groups.length();i++){JSONObject group=groups.optJSONObject(i);if(group!=null&&id.equals(group.optString("group_id")))return group.optString("group_name",id);}return id;}
    void status(String value){p.edit().putString("status",value).apply();}
    String status(){return p.getString("status","等待连接手机上的 NapCat");}
    JSONObject snapshot(Context c)throws Exception{return new JSONObject().put("enabled",enabled()).put("groups",groups()).put("followed",new JSONArray(followed())).put("pending",pending()).put("status",status()).put("hasToken",new ApiCredential(c,"napcat-credential").configured());}
}
