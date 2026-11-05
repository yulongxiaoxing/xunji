package cn.xunji.probe;
import android.app.*;
import android.content.*;
import android.os.*;
import org.json.*;
import java.util.*;
public class NapCatService extends Service {
    private volatile boolean stopped;
    private Thread worker;
    private final Object wakeup=new Object();
    private static volatile NapCatService running;
    static void start(Context c){c.startForegroundService(new Intent(c,NapCatService.class));NapCatService s=running;if(s!=null)s.wake();}
    private void wake(){synchronized(wakeup){wakeup.notifyAll();}}
    @Override public void onCreate(){super.onCreate();running=this;NotificationManager manager=getSystemService(NotificationManager.class);manager.createNotificationChannel(new NotificationChannel("napcat-receive","QQ 消息同步",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,DemoActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        startForeground(42,new Notification.Builder(this,"napcat-receive").setSmallIcon(cn.xunji.probe.R.drawable.ic_probe).setContentTitle("寻迹 QQ 消息同步已开启").setContentText("连接电脑后检查关注群历史，点击查看进度").setContentIntent(open).setOngoing(true).build());}
    @Override public int onStartCommand(Intent intent,int flags,int startId){if(worker==null){worker=new Thread(this::receive,"xunji-napcat");worker.start();}else wake();return START_STICKY;}
    private void receive(){NapCatStore settings=new NapCatStore(this);try{while(!stopped&&settings.enabled()){
        long epoch=settings.epoch();try{String token=new ApiCredential(this,"napcat-credential").get();if(token.isEmpty())throw new IllegalStateException("请先配置本机连接口令");NapCatClient client=new NapCatClient(token);JSONObject login=client.login();String account=login.optString("user_id");if(account.isEmpty())throw new IllegalStateException("请先扫码登录QQ");
            if(!settings.bindAccount(account))throw new IllegalStateException("QQ账号与已关注群不一致，请切回原账号后连接");
            settings.groups(client.groups());int errors=0,continuing=0;Set<String> followed=settings.followed();
            for(String group:followed){if(stopped||!settings.enabled()||settings.epoch()!=epoch)break;long revision=settings.revision(group);
                try(CardStore store=new CardStore(this)){
                    settings.groupStatus(account,group,epoch,revision,"checking","正在检查上次位置之后的消息");
                    NapCatBackfill.Result result=NapCatBackfill.scan(new NapCatBackfill.Source(){
                        public List<NapCatBackfill.Entry> latest()throws Exception{return NapCatClient.entries(client.history(group),group);}
                        public List<NapCatBackfill.Entry> older(String anchor)throws Exception{return NapCatClient.entries(client.history(group,anchor),group);}
                    },entry->{
                        if(!settings.saveIfCurrent(account,group,epoch,revision,()->{
                            JSONObject m=(JSONObject)entry.payload;
                            if(!NapCatPolicy.allowed(settings.followed(),m.optString("message_type","group"),group,entry.id,entry.time,settings.since(group),settings.baseline(group)))return true;
                            String body=NapCatClient.text(m);if(body.trim().isEmpty())return true;
                            JSONObject sender=m.optJSONObject("sender");String publisher=sender==null?"":sender.optString("card");if(publisher.isEmpty()&&sender!=null)publisher=sender.optString("nickname",sender.optString("user_id"));
                            store.ensureNapCat(account,group,entry.id,settings.groupName(group),publisher,body,entry.time*1000);return true;
                        }))throw new IllegalStateException("同步已暂停或关注已改变，进度保留");
                    },state->settings.saveProgress(account,group,epoch,revision,state),settings.progress(group),settings.since(group),System.currentTimeMillis()/1000,20,()->!stopped&&settings.current(account,group,epoch,revision));
                    if(!result.complete)continuing++;
                    settings.groupStatus(account,group,epoch,revision,result.complete?"synced":"continuing",result.complete?"历史已接上原位置；文字原文已保存":"本轮已保存，下一轮从续点继续补取");
                }catch(Exception e){errors++;settings.groupStatus(account,group,epoch,revision,"error",e instanceof IllegalStateException?e.getMessage():"本群历史暂未读取成功，进度已保留；连接恢复后重试");}
                AutoExtractService.schedule(this);
            }
            settings.statusForSession(epoch,"电脑QQ在线 · 已关注 "+settings.followed().size()+" 个群"+(errors>0?" · "+errors+" 群待重试":continuing>0?" · 正在分批补取":" · 查看下方原文同步进度"));
        }catch(Exception e){settings.statusForSession(epoch,e instanceof IllegalStateException?e.getMessage():"暂未连接电脑，请检查 NapCat、USB 授权和启动脚本；进度已保留");}
        synchronized(wakeup){if(!stopped&&settings.enabled())try{wakeup.wait(15000);}catch(InterruptedException e){if(stopped)break;}}
    }}finally{if(running==this)running=null;stopSelf();}}
    @Override public void onDestroy(){stopped=true;if(running==this)running=null;wake();if(worker!=null)worker.interrupt();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
