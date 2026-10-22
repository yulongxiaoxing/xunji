package cn.xunji.probe;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
final class NapCatClient {
    private final String token;
    NapCatClient(String token){this.token=token;}
    Object call(String action,JSONObject params)throws Exception{
        if(!action.matches("[a-z_]+"))throw new IllegalArgumentException();
        HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:3000/"+action).openConnection();c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(4000);c.setReadTimeout(8000);c.setInstanceFollowRedirects(false);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Authorization","Bearer "+token);
        try{try(OutputStream out=c.getOutputStream()){out.write(params.toString().getBytes(StandardCharsets.UTF_8));}
            if(c.getResponseCode()!=200)throw new IllegalStateException("本机接口未授权或尚未就绪");
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1){buffer.write(bytes,0,n);if(buffer.size()>4000000)throw new IllegalStateException("本次消息批次过大");}}
            JSONObject result=new JSONObject(new String(buffer.toByteArray(),StandardCharsets.UTF_8));
            if(!"ok".equals(result.optString("status"))||result.optInt("retcode",-1)!=0)throw new IllegalStateException("QQ 尚未登录或本机接口暂不可用");return result.get("data");
        }finally{c.disconnect();}
    }
    JSONArray groups()throws Exception{return (JSONArray)call("get_group_list",new JSONObject());}
    JSONObject login()throws Exception{JSONObject status=(JSONObject)call("get_status",new JSONObject());if(!status.optBoolean("online")||!status.optBoolean("good"))throw new IllegalStateException("QQ接入端当前离线，请检查电脑端登录状态");return (JSONObject)call("get_login_info",new JSONObject());}
    JSONArray history(String group)throws Exception{return ((JSONObject)call("get_group_msg_history",new JSONObject().put("group_id",group).put("count",50).put("reverseOrder",false))).getJSONArray("messages");}
    static String text(JSONObject message)throws Exception{
        Object segments=message.opt("message");if(segments instanceof JSONArray){StringBuilder body=new StringBuilder();JSONArray array=(JSONArray)segments;for(int i=0;i<array.length();i++){JSONObject part=array.optJSONObject(i);if(part!=null&&"text".equals(part.optString("type")))body.append(part.getJSONObject("data").optString("text"));}return body.toString();}
        return message.optString("raw_message",segments instanceof String?(String)segments:"");
    }
}
