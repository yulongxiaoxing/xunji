package cn.xunji.probe;

import android.app.Notification;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import org.json.JSONObject;

public class ProbeService extends NotificationListenerService {
    static volatile boolean connected;
    @Override public void onListenerConnected() { connected = true; }
    @Override public void onListenerDisconnected() { connected = false; }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if(new NapCatStore(this).preferred())return;
        // Never read text from another application or replay historical notifications.
        if (!CapturePolicy.QQ.equals(sbn.getPackageName())) return;
        ProbeStore store = new ProbeStore(this);
        java.util.Set<String> followed = new SelectionStore(this).followed();
        boolean sampling = !store.filterTestActive() && CapturePolicy.sampling(store.until(), SystemClock.elapsedRealtime());
        if (!sampling && store.chosen().isEmpty() && followed.isEmpty()) { store.count("unfollowed"); return; }
        Notification n = sbn.getNotification();
        if (n == null) { store.count("invalid"); return; }
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) { store.count("summary"); return; }
        Bundle extras = n.extras;
        if (extras == null) { store.count("invalid"); return; }
        String title = value(extras, Notification.EXTRA_TITLE);
        String conversation = value(extras, Notification.EXTRA_CONVERSATION_TITLE);
        String identity = !conversation.isEmpty() ? "conversation:" + conversation : !title.isEmpty() ? "title:" + title : "";
        Boolean group = extras.containsKey(Notification.EXTRA_IS_GROUP_CONVERSATION)
            ? extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION) : null;
        if (!sampling && Boolean.FALSE.equals(group)) { store.count("private"); return; }
        boolean matched = CapturePolicy.matchesFollowed(followed, title, conversation, group)
            || (!store.filterTestActive() && CapturePolicy.allowed(store.chosen(), identity));
        // Reject unselected sources before extracting the notification body.
        if (!sampling && !matched) { store.count("unfollowed"); return; }
        String expanded = value(extras, Notification.EXTRA_BIG_TEXT);
        if(expanded.isEmpty()) expanded = value(extras, Notification.EXTRA_TEXT);
        if(expanded.isEmpty()) {
            CharSequence[] lines=extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if(lines!=null) { StringBuilder full=new StringBuilder(); for(CharSequence line:lines) {if(full.length()>0)full.append("\n");full.append(line);} expanded=full.toString(); }
        }
        Parcelable[] messages=extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        android.util.Log.i("XunjiCapture","matched textChars="+value(extras,Notification.EXTRA_TEXT).length()+" bigTextChars="+value(extras,Notification.EXTRA_BIG_TEXT).length()+" messages="+(messages==null?0:messages.length));
        boolean hasMessage=false;
        if(messages!=null) for(int i=0;i<messages.length;i++) {
            if(!(messages[i] instanceof Bundle)) continue;
            Bundle message=(Bundle)messages[i]; String text=value(message,"text");
            if(text.isEmpty())continue;
            if(i==messages.length-1) text=MessageText.expanded(text,expanded);
            String publisher=value(message,"sender"); Object person=message.getParcelable("sender_person");
            if(person instanceof android.app.Person && ((android.app.Person)person).getName()!=null) publisher=((android.app.Person)person).getName().toString();
            long time=message.getLong("time",sbn.getPostTime());
            save(store,sbn,identity,title,conversation,publisher,text,time,sampling); hasMessage=true;
        }
        if(!hasMessage) save(store,sbn,identity,title,conversation,"",expanded,sbn.getPostTime(),sampling);
        AutoExtractService.schedule(this);
    }
    private void save(ProbeStore store,StatusBarNotification sbn,String identity,String title,String conversation,String publisher,String text,long time,boolean sampling) {
        if(!CapturePolicy.eligible(sbn.getPackageName(),false,text)) {store.count("empty");return;}
        try {
            JSONObject row=new JSONObject(); row.put("key",sbn.getKey()+"|"+publisher); row.put("identity",identity);
            row.put("label",!conversation.isEmpty()?conversation:title); row.put("title",title); row.put("conversation",conversation);
            row.put("publisher",publisher); row.put("text",text); row.put("time",time); row.put("mode",sampling?"手动采样":"白名单匹配");
            store.append(row);
            try(CardStore durable=new CardStore(this)) {durable.importNotifications(new org.json.JSONArray().put(row));}
            store.count("kept");
        } catch(Exception ignored) {}
    }

    private static String value(Bundle extras, String name) {
        CharSequence text = extras.getCharSequence(name);
        return text == null ? "" : text.toString().trim();
    }
}
