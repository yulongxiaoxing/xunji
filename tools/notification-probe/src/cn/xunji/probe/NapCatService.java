package cn.xunji.probe;
import android.app.*;
import android.content.*;
import android.os.*;
import org.json.*;
import java.util.*;
public class NapCatService extends Service {
    private volatile boolean stopped;
    private Thread worker;
    static void start(Context c){c.startForegroundService(new Intent(c,NapCatService.class));}
    @Override public void onCreate(){super.onCreate();NotificationManager manager=getSystemService(NotificationManager.class);manager.createNotificationChannel(new NotificationChannel("napcat-receive","QQ 本机接收",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,DemoActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        startForeground(42,new Notification.Builder(this,"napcat-receive").setSmallIcon(cn.xunji.probe.R.drawable.ic_probe).setContentTitle("寻迹正在接收关注群消息").setContentText("QQ接入运行中，点击查看连接状态").setContentIntent(open).setOngoing(true).build());}
    @Override public int onStartCommand(Intent intent,int flags,int startId){if(worker==null){worker=new Thread(this::receive,"xunji-napcat");worker.start();}return START_STICKY;}
    private void receive(){NapCatStore settings=new NapCatStore(this);try{while(!stopped&&settings.enabled()){
        long epoch=settings.epoch();try{String token=new ApiCredential(this,"napcat-credential").get();if(token.isEmpty())throw new IllegalStateException("请先配置本机连接口令");NapCatClient client=new NapCatClient(token);JSONObject login=client.login();String account=login.optString("user_id");if(account.isEmpty())throw new IllegalStateException("请先扫码登录QQ");
            settings.groups(client.groups());int count=0;Set<String> followed=settings.followed();
            for(String group:followed){if(stopped||!settings.enabled())break;long revision=settings.revision(group);JSONArray messages=client.history(group);
                try(CardStore store=new CardStore(this)){for(int i=0;i<messages.length();i++){JSONObject message=messages.optJSONObject(i);if(message==null)continue;
                    String groupId=message.optString("group_id",group),id=message.optString("message_id");long time=message.optLong("time");
                    if(settings.saveIfAllowed(groupId,epoch,revision,()->{
                        if(stopped||!NapCatPolicy.allowed(settings.followed(),message.optString("message_type","group"),groupId,id,time,settings.since(groupId),settings.baseline(groupId)))return false;
                        String body=NapCatClient.text(message);if(body.trim().isEmpty())return false;
                        JSONObject sender=message.optJSONObject("sender");String publisher=sender==null?"":sender.optString("card");if(publisher.isEmpty()&&sender!=null)publisher=sender.optString("nickname",sender.optString("user_id"));
                        return store.addNapCat(account,groupId,id,settings.groupName(groupId),publisher,body,time*1000);
                    }))count++;
                }}
            }
            settings.statusForSession(epoch,"QQ在线 · 已关注 "+settings.followed().size()+" 个群");if(count>0&&settings.enabled()&&settings.epoch()==epoch)AutoExtractService.schedule(this);
        }catch(Exception e){settings.statusForSession(epoch,e instanceof IllegalStateException?e.getMessage():"QQ接入暂未连接，请检查电脑端NapCat与USB连接");}
        try{Thread.sleep(5000);}catch(InterruptedException e){break;}
    }}finally{stopSelf();}}
    @Override public void onDestroy(){stopped=true;if(worker!=null)worker.interrupt();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
