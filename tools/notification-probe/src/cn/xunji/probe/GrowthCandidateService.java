package cn.xunji.probe;
import android.app.job.*;
import android.content.*;
import android.net.*;
import org.json.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class GrowthCandidateService extends JobService {
 private static final int JOB=43;private static final AtomicBoolean working=new AtomicBoolean();
 static void schedule(Context context){
  if(working.get())return;
  try(CardStore cards=new CardStore(context)){GrowthCandidateStore store=new GrowthCandidateStore(cards.getWritableDatabase());store.sync(cards.cards());if(store.next()==null||!new ApiCredential(context).configured())return;}catch(Exception e){return;}
  JobScheduler scheduler=context.getSystemService(JobScheduler.class);if(scheduler!=null&&scheduler.getPendingJob(JOB)==null)scheduler.schedule(new JobInfo.Builder(JOB,new ComponentName(context,GrowthCandidateService.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(1000).build());
 }
 static void whileVisible(Context context){
  ConnectivityManager cm=context.getSystemService(ConnectivityManager.class);NetworkCapabilities net=cm==null?null:cm.getNetworkCapabilities(cm.getActiveNetwork());
  if(net!=null&&net.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))start(context,null);else schedule(context);
 }
 private static boolean start(Context context,Runnable finished){
  Context app=context.getApplicationContext();if(!new ApiCredential(app).configured()||working.get())return false;
  try(CardStore cards=new CardStore(app)){GrowthCandidateStore store=new GrowthCandidateStore(cards.getWritableDatabase());store.sync(cards.cards());if(store.next()==null)return false;}catch(Exception e){return false;}
  if(!working.compareAndSet(false,true))return false;
  new Thread(()->{try(CardStore cards=new CardStore(app)){
   GrowthCandidateStore store=new GrowthCandidateStore(cards.getWritableDatabase());store.sync(cards.cards());
   for(int i=0;i<10;i++){JSONObject next=store.next();if(next==null)break;String id=next.getString("id"),input=next.getString("input");
    try{JSONObject decision=new GrowthMemoryExtractor(app).classify(new ApiCredential(app).get(),input);store.sync(cards.cards());store.apply(id,input,decision);}catch(Exception e){store.error(id,input);}
   }
  }catch(Exception ignored){}finally{working.set(false);if(finished!=null)finished.run();new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->schedule(app),1000);}},"xunji-growth-candidates").start();return true;
 }
 @Override public boolean onStartJob(JobParameters params){return start(this,()->jobFinished(params,false));}
 @Override public boolean onStopJob(JobParameters params){return true;}
}
