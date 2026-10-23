package cn.xunji.probe;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.provider.Settings;
import android.webkit.*;
import android.widget.Toast;
import org.json.*;
import java.util.concurrent.Executors;

public class DemoActivity extends Activity {
    private WebView web;
    private CardStore cards;
    private String sharedText="";
    private String setupToken="";
    private boolean pageReady;
    private volatile boolean visible;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); cards = new CardStore(this);
        receiveShare(getIntent());
        new ProbeStore(this).startFilterTest(new SelectionStore(this).followed().size());
        web = new WebView(this); web.setBackgroundColor(android.graphics.Color.rgb(245,247,252));
        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        frame.setBackgroundColor(android.graphics.Color.rgb(245,247,252));
        frame.setOnApplyWindowInsetsListener((v,insets)-> {
            if(android.os.Build.VERSION.SDK_INT>=30) { android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout() | android.view.WindowInsets.Type.ime()); v.setPadding(bars.left,bars.top,bars.right,bars.bottom); }
            else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        web.getSettings().setJavaScriptEnabled(true); web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowContentAccess(false); web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowUniversalAccessFromFileURLs(false); web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView view,String url) { pageReady=true;showShare(); }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return true; }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (!request.getUrl().toString().startsWith("file:///android_asset/")) return new WebResourceResponse("text/plain","UTF-8",new java.io.ByteArrayInputStream(new byte[0]));
                return null;
            }
        });
        web.addJavascriptInterface(new Bridge(), "Xunji"); frame.addView(web,new android.widget.FrameLayout.LayoutParams(-1,-1)); setContentView(frame); frame.requestApplyInsets(); web.loadUrl("file:///android_asset/index.html");
    }
    @Override public void onResume() { super.onResume(); visible=true; new SelectionStore(this).stop(); GroupSelectionService.dismissReturnButton(); if(new NapCatStore(this).enabled())NapCatService.start(this);AutoExtractService.whileVisible(this);AutoExtractService.schedule(this); refresh(); }
    @Override public void onPause() {visible=false;super.onPause();}
    private void refresh() { if(web!=null) runOnUiThread(()->web.evaluateJavascript("window.refreshNative && window.refreshNative()",null)); }
    private void message(String text) { runOnUiThread(()->Toast.makeText(this,text,Toast.LENGTH_LONG).show()); }
    private void receiveShare(Intent intent) { String setup=intent.getStringExtra("xunji.napcat.setup");if(setup!=null&&setup.length()>=10&&setup.length()<=300)setupToken=setup; if(Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {CharSequence text=intent.getCharSequenceExtra(Intent.EXTRA_TEXT); if(text!=null) sharedText=text.toString();} }
    private void showShare() { if(!pageReady||web==null)return;if(!setupToken.isEmpty()){String token=setupToken;setupToken="";web.evaluateJavascript("prefillNapCatToken("+JSONObject.quote(token)+")",null);} if(!sharedText.isEmpty()) {String text=sharedText;sharedText=""; web.evaluateJavascript("paste("+JSONObject.quote(text)+")",null);} }
    @Override public void onNewIntent(Intent intent) {super.onNewIntent(intent);setIntent(intent);receiveShare(intent);showShare();}
    @Override public void onBackPressed() { web.evaluateJavascript("(function(){var d=document.getElementById('modal');if(d&&d.open){if(document.getElementById('cardForm'))closeEditor();else{if(document.getElementById('wakeupForm'))act('wakeupCancel');if(document.getElementById('memoryForm'))act('memoryCancel');d.close();}return true;}return false;})()",result->{if(!"true".equals(result))finish();}); }
    private int wakeupRevision=0;
    private JSONObject wakeupPreview;
    private String wakeupPreviewId="";
    private synchronized int nextWakeup(){wakeupPreview=null;wakeupPreviewId="";return ++wakeupRevision;}
    private void wakeupResult(int revision,JSONObject preview,String error){
        try{
            final JSONObject result=new JSONObject().put("ok",preview!=null);
            synchronized(this){
                if(revision!=wakeupRevision)return;wakeupPreview=preview;wakeupPreviewId=preview==null?"":java.util.UUID.randomUUID().toString();
                if(preview!=null)result.put("id",wakeupPreviewId).put("tableName",preview.getString("tableName")).put("termStart",preview.getString("termStart")).put("meetings",preview.getJSONArray("meetings")).put("count",preview.getJSONArray("sessions").length());
                else result.put("error",error);
            }
            runOnUiThread(()->{synchronized(DemoActivity.this){if(revision!=wakeupRevision)return;}web.evaluateJavascript("window.wakeupPreviewReady && window.wakeupPreviewReady("+result.toString()+")",null);});
        }catch(Exception ignored){}
    }
    private void previewWakeup(String text){final int revision=nextWakeup();new Thread(()->{
        try{wakeupResult(revision,WakeupImport.fetch(text),"");}
        catch(IllegalArgumentException e){wakeupResult(revision,null,e.getMessage());}
        catch(Exception e){wakeupResult(revision,null,"读取失败，请检查网络或使用WakeUp导出文件；已有数据保留");}
    },"xunji-wakeup-preview").start();}
    private int memoryRevision=0;
    private JSONArray memoryDrafts;private String memoryOriginal="",memoryId="";private long memoryReceived;
    private synchronized int cancelMemory(){memoryDrafts=null;memoryId="";return ++memoryRevision;}
    private void memoryCallback(int revision,JSONObject result){runOnUiThread(()->{synchronized(DemoActivity.this){if(revision!=memoryRevision)return;}web.evaluateJavascript("window.memoryReady && window.memoryReady("+result.toString()+")",null);});}
    private void previewMemory(String text,String requestToken){
        final int revision=cancelMemory();final long received=System.currentTimeMillis();
        new Thread(()->{try{
            if(text.trim().isEmpty()||text.length()>50000)throw new IllegalArgumentException("请填写1至50000字的口头记忆");
            ApiCredential credential=new ApiCredential(DemoActivity.this);if(!credential.configured())throw new IllegalArgumentException("请先在设置中配置DeepSeek密钥；输入内容仍保留");
            JSONArray draft=new DeepSeekExtractor(DemoActivity.this).extractMemory(credential.get(),new JSONObject().put("text",text).put("receivedAt",received));
            JSONObject result=new JSONObject().put("ok",true).put("cards",draft).put("requestToken",requestToken);
            synchronized(DemoActivity.this){if(revision!=memoryRevision)return;memoryDrafts=draft;memoryOriginal=text;memoryReceived=received;memoryId=java.util.UUID.randomUUID().toString();result.put("id",memoryId);}
            memoryCallback(revision,result);
        }catch(Exception e){try{memoryCallback(revision,new JSONObject().put("ok",false).put("requestToken",requestToken).put("error",e instanceof IllegalArgumentException||e instanceof IllegalStateException?e.getMessage():"整理失败，请检查网络后重试；输入内容仍保留"));}catch(Exception ignored){}}},"xunji-memory-preview").start();
    }
    private void startDictation(){
        try{Intent intent=new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE,"zh-CN").putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT,"说出要记住的事项、时间和地点");startActivityForResult(intent,72);}
        catch(android.content.ActivityNotFoundException e){web.evaluateJavascript("window.dictationUnavailable && window.dictationUnavailable()",null);}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==72){if(result==RESULT_OK&&data!=null){java.util.ArrayList<String> matches=data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);if(matches!=null&&!matches.isEmpty())web.evaluateJavascript("window.dictationReady && window.dictationReady("+JSONObject.quote(matches.get(0))+")",null);}else web.evaluateJavascript("window.dictationCancelled && window.dictationCancelled()",null);return;}
        if(request!=71||result!=RESULT_OK||data==null||data.getData()==null)return;
        final android.net.Uri uri=data.getData();final int revision=nextWakeup();
        new Thread(()->{try(java.io.InputStream in=getContentResolver().openInputStream(uri);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
            if(in==null)throw new IllegalArgumentException("无法读取课表文件");byte[] buffer=new byte[4096];int n;
            while((n=in.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>2000000)throw new IllegalArgumentException("课表文件过大，未导入");}
            String content=new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);
            wakeupResult(revision,content.replaceFirst("^\uFEFF", "").trim().startsWith("BEGIN:VCALENDAR")?IcsImport.parse(content):WakeupImport.parse(content),"");
        }catch(IllegalArgumentException e){wakeupResult(revision,null,e.getMessage());}catch(Exception e){wakeupResult(revision,null,"此文件无法完整识别为ICS或WakeUp课表，未导入");}},"xunji-wakeup-file").start();
    }
    private class Bridge {
        @JavascriptInterface public String snapshot() {
            try { if(visible)AutoExtractService.whileVisible(DemoActivity.this);AutoExtractService.schedule(DemoActivity.this); SelectionStore selection=new SelectionStore(DemoActivity.this);
                return new JSONObject().put("cards",cards.cards()).put("sources",cards.sources()).put("groups",new JSONArray(selection.followed()))
                    .put("napcat",new NapCatStore(DemoActivity.this).snapshot(DemoActivity.this))
                    .put("candidates",selection.candidates()).put("hasKey",new ApiCredential(DemoActivity.this).configured())
                    .put("accessibility",GroupSelectionService.connected).put("notifications",ProbeService.connected).toString();
            } catch(Exception e) { return "{\"error\":\"读取数据失败，请重启应用\"}"; }
        }
        @JavascriptInterface public String mutate(String type,String value) {
            try{
                JSONObject row=new JSONObject(value);
                if(type.equals("createCard"))cards.createCards(row);
                else if(type.equals("updateCard"))cards.updateCard(row);
                else if(type.equals("saveMemory")){
                    synchronized(DemoActivity.this){if(memoryDrafts==null||memoryDrafts.length()==0||!memoryId.equals(row.optString("id")))throw new IllegalArgumentException("口头记忆预览已失效，请重新整理");cards.saveMemory(memoryOriginal,memoryDrafts,memoryReceived);cancelMemory();}
                }
                else if(type.equals("importWakeup")){
                    synchronized(DemoActivity.this){
                        if(wakeupPreview==null||!wakeupPreviewId.equals(row.optString("id")))throw new IllegalArgumentException("课表预览已失效，请重新读取");
                        JSONObject summary=cards.importCourses(wakeupPreview);int todayCount=0;String nextDate="",today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString();JSONArray occurrences=wakeupPreview.getJSONArray("sessions");for(int i=0;i<occurrences.length();i++){String date=occurrences.getJSONObject(i).getString("date");if(today.equals(date))todayCount++;if(date.compareTo(today)>=0&&(nextDate.isEmpty()||date.compareTo(nextDate)<0))nextDate=date;}wakeupPreview=null;wakeupPreviewId="";return summary.put("ok",true).put("todayCount",todayCount).put("nextDate",nextDate).toString();
                    }
                }
                else if(type.equals("schedule"))cards.setSchedule(row.getString("id"),row.optString("dueDate"),row.optString("dueTime"));
                else if(type.equals("card"))cards.cardState(row.getString("id"),row.getString("state"));
                else throw new IllegalArgumentException("操作不可用");
                return "{\"ok\":true}";
            }catch(IllegalArgumentException e){return "{\"ok\":false,\"error\":"+JSONObject.quote(e.getMessage())+"}";}
            catch(Exception e){return "{\"ok\":false,\"error\":\"保存失败，请重试；原有内容已保留\"}";}
        }
        @JavascriptInterface public void action(String type,String value) {
            runOnUiThread(()-> { try {
                SelectionStore selection=new SelectionStore(DemoActivity.this);
                if(type.equals("memoryPreview")){JSONObject request=new JSONObject(value);previewMemory(request.getString("text"),request.getString("requestToken"));return;}
                else if(type.equals("memoryCancel")){cancelMemory();return;}
                else if(type.equals("dictate")){startDictation();return;}
                else if(type.equals("wakeupPreview")){previewWakeup(value);return;}
                else if(type.equals("wakeupCancel")){nextWakeup();return;}
                else if(type.equals("wakeupFile")){Intent file=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");startActivityForResult(file,71);return;}
                else if(type.equals("napcatConnect")) {NapCatStore n=new NapCatStore(DemoActivity.this);if(!value.trim().isEmpty())new ApiCredential(DemoActivity.this,"napcat-credential").save(value.trim());if(!new ApiCredential(DemoActivity.this,"napcat-credential").configured()){message("请先填写本机连接口令");return;}n.enabled(true);n.status("正在连接手机上的QQ接入端");NapCatService.start(DemoActivity.this);}
                else if(type.equals("napcatStop")){new NapCatStore(DemoActivity.this).enabled(false);stopService(new Intent(DemoActivity.this,NapCatService.class));new NapCatStore(DemoActivity.this).status("接收已暂停，已有原文和卡片保留");}
                else if(type.equals("napcatFollow")){if(!value.matches("[1-9][0-9]{0,19}"))return;NapCatStore n=new NapCatStore(DemoActivity.this);long revision=n.requestFollow(value);refresh();new Thread(()->{try{boolean known=false;JSONArray groups=n.groups();for(int i=0;i<groups.length();i++)if(value.equals(groups.getJSONObject(i).optString("group_id")))known=true;if(!known)throw new IllegalStateException();NapCatClient client=new NapCatClient(new ApiCredential(DemoActivity.this,"napcat-credential").get());if(n.follow(value,client.history(value),revision))message("已关注，之后的新消息将进入寻迹");refresh();}catch(Exception e){n.failFollow(value,revision);message("关注未完成，请确认本机QQ已登录后重试");refresh();}},"xunji-follow").start();return;}
                else if(type.equals("napcatUnfollow"))new NapCatStore(DemoActivity.this).unfollow(value);
                else if(type.equals("select")) {
                    if(!GroupSelectionService.connected) { message("请先在设置中开启寻迹选群辅助服务"); return; }
                    Intent qq=getPackageManager().getLaunchIntentForPackage("com.tencent.mobileqq");
                    if(qq==null) {message("未找到QQ，请先安装QQ");return;}
                    new ProbeStore(DemoActivity.this).startFilterTest(selection.followed().size()); selection.start(); startActivity(qq);
                } else if(type.equals("follow")) { selection.follow(value); selection.report("关注群已保存",new JSONArray()); }
                else if(type.equals("unfollow")) selection.unfollow(value);
                else if(type.equals("confirmGroups")) { JSONArray names=selection.candidates(); for(int i=0;i<names.length();i++) selection.follow(names.getString(i)); selection.report("关注群已保存",new JSONArray()); new ProbeStore(DemoActivity.this).startFilterTest(selection.followed().size()); }
                else if(type.equals("card")) { JSONObject data=new JSONObject(value); cards.cardState(data.getString("id"),data.getString("state")); }
                else if(type.equals("paste")) { JSONObject data=new JSONObject(value); String text=data.optString("text").trim(); if(text.isEmpty()||text.length()>50000) {message("请输入1至50000字原文，超出请分段");return;} cards.addManual(data.optString("group","手动输入"),data.optString("publisher"),text); }
                else if(type.equals("saveKey")) { new ApiCredential(DemoActivity.this).save(value.trim()); cards.retryErrors(); message("密钥已加密保存在本机，等待中的消息将自动整理"); }
                else if(type.equals("clearKey")) new ApiCredential(DemoActivity.this).clear();
                else if(type.equals("notifications")) startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                else if(type.equals("accessibility")) startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                else if(type.equals("diagnostics")) startActivity(new Intent(DemoActivity.this,MainActivity.class));
                else if(type.equals("retryPending")) cards.retryErrors();
                AutoExtractService.schedule(DemoActivity.this); refresh();
            } catch(Exception e) { message("操作未完成，请检查输入后重试"); } });
        }
    }
}
