package cn.xunji.probe;

import android.app.job.*;
import android.content.*;
import android.net.*;
import org.json.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** One shared queue for visible-app work and network-constrained background jobs. */
public class AutoExtractService extends JobService {
    private static final int JOB = 41;
    private static final AtomicBoolean working = new AtomicBoolean(false);
    static void schedule(Context context) {
        if(!new ApiCredential(context).configured() || working.get()) return;
        try(CardStore store=new CardStore(context)) {if(!store.hasWork())return;}
        JobScheduler scheduler=(JobScheduler)context.getSystemService(JOB_SCHEDULER_SERVICE);
        if(scheduler.getPendingJob(JOB)==null) scheduler.schedule(new JobInfo.Builder(JOB,new ComponentName(context,AutoExtractService.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(200).build());
    }
    static void whileVisible(Context context) {
        ConnectivityManager manager=context.getSystemService(ConnectivityManager.class);
        NetworkCapabilities network=manager==null?null:manager.getNetworkCapabilities(manager.getActiveNetwork());
        if(network==null || !network.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            || !network.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return;
        startWorker(context,null);
    }
    private static boolean startWorker(Context context,Runnable finished) {
        Context app=context.getApplicationContext();
        if(!new ApiCredential(app).configured() || working.get()) return false;
        try(CardStore store=new CardStore(app)) {if(!store.hasWork())return false;}
        if(!working.compareAndSet(false,true)) return false;
        new Thread(()-> {
            try(CardStore store=new CardStore(app)) {
                // Only a previous process can have left a processing row when no worker owns the queue.
                JSONArray recovery=store.sources();
                for(int i=0;i<recovery.length();i++) { JSONObject s=recovery.getJSONObject(i); if("processing".equals(s.optString("aiState"))) store.state(s.getString("id"),"pending",""); }
                while(true) {
                    String key=new ApiCredential(app).get(); if(key.isEmpty()) break;
                    JSONObject next=null; JSONArray sources=store.sources();
                    for(int i=0;i<sources.length();i++) if("pending".equals(sources.getJSONObject(i).optString("aiState"))) { next=sources.getJSONObject(i); break; }
                    if(next==null) break;
                    String id=next.getString("id"); store.state(id,"processing","");
                    try { if(next.getString("text").length()>50000) throw new IllegalStateException("原文过长，请分段分享或粘贴；完整原文已保留");
                        android.util.Log.i("XunjiExtract","Processing source chars="+next.getString("text").length());
                        JSONArray extracted=store.cachedExtraction(next.getString("text"));
                        if(extracted==null)extracted=new DeepSeekExtractor(app).extract(key,next);
                        else android.util.Log.i("XunjiExtract","Identical content reused for current rules");
                        store.replaceCards(id,extracted);
                        android.util.Log.i("XunjiExtract","Source extraction saved");
                    } catch(Exception e) {
                        android.util.Log.w("XunjiExtract","Extraction failed: "+e.getClass().getSimpleName());
                        store.state(id,"error",e instanceof IllegalStateException?e.getMessage():"联网或结果解析失败，原文已保留");
                    }
                }
            } catch(Exception e) { android.util.Log.w("XunjiExtract","Queue deferred: "+e.getClass().getSimpleName()); }
            finally {
                working.set(false);
                if(finished!=null)finished.run();
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->schedule(app),1000);
            }
        },"xunji-extract").start();
        return true;
    }
    @Override public boolean onStartJob(JobParameters params) {return startWorker(this,()->jobFinished(params,false));}
    @Override public boolean onStopJob(JobParameters params) { return true; }
}
