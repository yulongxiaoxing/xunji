package cn.xunji.probe;

import org.json.JSONArray;
import java.util.*;

/** Imported draft images are owned by one editor token until explicitly committed. */
final class GrowthImageDraft {
    private final GrowthImages images;
    private String token="";
    private final Set<String> pending=new HashSet<>();
    private final Set<String> existing=new HashSet<>();
    GrowthImageDraft(GrowthImages images){this.images=images;}
    synchronized void begin(String value){clear(Collections.emptySet());token=value;}
    synchronized void begin(String value,JSONArray saved){
        begin(value);if(saved==null)return;
        for(int i=0;i<saved.length();i++){String id=saved.optJSONObject(i)==null?"":saved.optJSONObject(i).optString("id");if(GrowthPolicy.imageId(id)&&existing.add(id))images.reserve(id);}
    }
    synchronized boolean active(String value){return !token.isEmpty()&&token.equals(value);}
    synchronized boolean accept(String value,JSONArray imported)throws Exception{
        if(!active(value)){for(int i=0;i<imported.length();i++)images.delete(imported.getJSONObject(i).getString("id"));return false;}
        for(int i=0;i<imported.length();i++)pending.add(imported.getJSONObject(i).getString("id"));return true;
    }
    synchronized void cancel(String value){if(active(value)){clear(Collections.emptySet());token="";}}
    synchronized void cancelAll(){clear(Collections.emptySet());token="";}
    synchronized void commit(String value,JSONArray kept)throws Exception{
        if(!active(value))throw new IllegalArgumentException("图片表单已失效，请重新打开经历");
        Set<String> ids=new HashSet<>();for(int i=0;i<kept.length();i++)ids.add(kept.getJSONObject(i).getString("id"));clear(ids);token="";
    }
    synchronized Set<String> pendingIds(){return new HashSet<>(pending);}
    private void clear(Set<String> keep){for(String id:pending)if(!keep.contains(id))images.delete(id);else images.release(id);for(String id:existing)images.release(id);pending.clear();existing.clear();}
}
