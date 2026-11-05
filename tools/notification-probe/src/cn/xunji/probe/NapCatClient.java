package cn.xunji.probe;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
class NapCatClient {
    private final String token;
    private String account="";
    NapCatClient(String token){this.token=token;}
    Object call(String action,JSONObject params)throws Exception{
        if(!action.matches("[a-z_]+"))throw new IllegalArgumentException();
        HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:3000/"+action).openConnection();c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(4000);c.setReadTimeout(action.equals("get_group_msg_history")?45000:8000);c.setInstanceFollowRedirects(false);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Authorization","Bearer "+token);
        try{try(OutputStream out=c.getOutputStream()){out.write(params.toString().getBytes(StandardCharsets.UTF_8));}
            int code=c.getResponseCode();if(code!=200)throw new IllegalStateException(code==401||code==403?"连接口令不匹配，请核对电脑 onebot 口令":"电脑接口尚未就绪，请检查启动脚本和 USB 连接");
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1){buffer.write(bytes,0,n);if(buffer.size()>4000000)throw new IllegalStateException("本次消息批次过大");}}
            JSONObject result=new JSONObject(new String(buffer.toByteArray(),StandardCharsets.UTF_8));
            if(!"ok".equals(result.optString("status"))||result.optInt("retcode",-1)!=0)throw new IllegalStateException("QQ 尚未登录或本机接口暂不可用");return result.get("data");
        }finally{c.disconnect();}
    }
    JSONArray groups()throws Exception{return (JSONArray)call("get_group_list",new JSONObject());}
    JSONObject login()throws Exception{
        JSONObject status=(JSONObject)call("get_status",new JSONObject());if(!status.optBoolean("online")||!status.optBoolean("good"))throw new IllegalStateException("QQ接入端当前离线，请检查电脑端登录状态");
        JSONObject login=(JSONObject)call("get_login_info",new JSONObject());String actual=login.optString("user_id");
        if(!actual.matches("[1-9][0-9]{0,19}"))throw new IllegalStateException("电脑 QQ 身份尚未就绪，请稍后重试");
        if(!account.isEmpty()&&!account.equals(actual))throw new IllegalStateException("读取期间 QQ 账号发生变化，进度保留；请切回原账号");
        account=actual;return login;
    }
    JSONArray history(String group)throws Exception{return history(group,"");}
    JSONArray history(String group,String anchor)throws Exception{
        login();
        JSONObject params=new JSONObject().put("group_id",group).put("count",50).put("reverse_order",!anchor.isEmpty()).put("parse_mult_msg",false).put("disable_get_url",true);
        if(!anchor.isEmpty())params.put("message_seq",anchor);
        JSONArray rows=((JSONObject)call("get_group_msg_history",params)).getJSONArray("messages");
        login();
        for(int i=0;i<rows.length();i++)if(!account.equals(rows.getJSONObject(i).optString("self_id")))throw new IllegalStateException("历史消息账号不一致，已拒绝本页并保留进度");
        return rows;
    }
    static List<NapCatBackfill.Entry> entries(JSONArray rows,String group)throws Exception{
        List<NapCatBackfill.Entry> result=new ArrayList<>();
        for(int i=0;i<rows.length();i++){
            JSONObject m=rows.getJSONObject(i);
            if(!"group".equals(m.optString("message_type","group"))||!group.equals(m.optString("group_id",group)))throw new IllegalStateException("历史返回了其他会话，已停止本群同步");
            String id=m.optString("message_id");long time=m.optLong("time",0);if(id.isEmpty()||time<=0)throw new IllegalStateException("历史消息缺少标识或时间，进度未推进");
            result.add(new NapCatBackfill.Entry(id,time,m.optLong("real_seq",0),m));
        }
        return result;
    }
    static String text(JSONObject message)throws Exception{
        Object segments=message.opt("message");if(segments instanceof JSONArray){StringBuilder body=new StringBuilder();JSONArray array=(JSONArray)segments;for(int i=0;i<array.length();i++){JSONObject part=array.optJSONObject(i);if(part!=null&&"text".equals(part.optString("type")))body.append(part.getJSONObject("data").optString("text"));}return body.toString();}
        return message.optString("raw_message",segments instanceof String?(String)segments:"");
    }
}
