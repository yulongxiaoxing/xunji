package cn.xunji.probe;

import android.content.Context;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Separate from task extraction; returns editable drafts and never writes a store. */
final class GrowthMemoryExtractor {
 private final Context context;
 GrowthMemoryExtractor(Context context){this.context=context.getApplicationContext();}
 static JSONObject normalize(JSONObject model,String text,String category,long received)throws Exception{
  GrowthMemoryPolicy.validateInput(text,category);
  if(!Boolean.TRUE.equals(model.opt("experienced")))throw new IllegalArgumentException("请描述一条已实际参与或完成的经历；多条经历请分别整理");
  String title=model.opt("title") instanceof String?GrowthMemoryPolicy.grounded(text,model.getString("title"),200):"";
  if(title.isEmpty())throw new IllegalArgumentException("没有识别到明确的经历名称，请补充名称后重新整理");
  String monthText=model.opt("monthText") instanceof String?model.getString("monthText"):"";
  JSONObject details=new JSONObject(),raw=model.optJSONObject("details");
  for(String key:GrowthPolicy.fields(category)){
   String value=raw!=null&&raw.opt(key) instanceof String?raw.getString(key):"";
   value=key.equals("hours")?GrowthMemoryPolicy.hours(text,value):GrowthMemoryPolicy.grounded(text,value,200);
   if(key.equals("award")&&text.matches("(?s).*(?:未获奖|没有获奖|没获奖|未公布|待公布|还没公布|尚未公布|奖项未知|结果未知).*"))value="";
   details.put(key,value);
  }
  java.util.Map<String,String> checked=new java.util.HashMap<>();for(String key:GrowthPolicy.fields(category))checked.put(key,details.optString(key));
  String date=GrowthMemoryPolicy.month(text,monthText,received);
  GrowthPolicy.validate(title,category,date,text);GrowthPolicy.validateDetails(category,checked);
  return new JSONObject().put("title",title).put("category",category).put("date",date).put("result",text).put("details",details);
 }
 JSONObject extract(String apiKey,String text,String category,long received)throws Exception{
  GrowthMemoryPolicy.validateInput(text,category);
  String system=read(context.getAssets().open("growth-memory-rules.md"));
  String base=java.time.Instant.ofEpochMilli(received).atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate().toString();
  String input=new JSONObject().put("category",category).put("text",text).put("baseDate",base).toString();
  return normalize(request(apiKey,system,input),text,category,received);
 }
 JSONObject classify(String apiKey,String input)throws Exception{
  if(input.length()>50000)throw new IllegalArgumentException("事项内容过长，请人工核对");
  String system=read(context.getAssets().open("growth-candidate-rules.md"));return request(apiKey,system,input);
 }
 private JSONObject request(String apiKey,String system,String input)throws Exception{
  JSONObject request=new JSONObject().put("model","deepseek-flash").put("temperature",0).put("thinking",new JSONObject().put("type","disabled"))
   .put("response_format",new JSONObject().put("type","json_object")).put("max_tokens",3000).put("stream",false)
   .put("messages",new JSONArray().put(new JSONObject().put("role","system").put("content",system)).put(new JSONObject().put("role","user").put("content",input)));
  HttpURLConnection connection=(HttpURLConnection)new URL("https://api.deepseek.com/chat/completions").openConnection();
  connection.setConnectTimeout(15000);connection.setReadTimeout(45000);connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setInstanceFollowRedirects(false);
  connection.setRequestProperty("Authorization","Bearer "+apiKey);connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
  try{
   try(OutputStream out=connection.getOutputStream()){out.write(request.toString().getBytes(StandardCharsets.UTF_8));}
   int code=connection.getResponseCode();
   if(code==401)throw new IllegalStateException("密钥无效，请在设置中重新填写；输入内容仍保留");
   if(code==402)throw new IllegalStateException("API余额不足，请在DeepSeek官方平台检查余额；输入内容仍保留");
   if(code==429)throw new IllegalStateException("请求过于频繁，请稍后重试；输入内容仍保留");
   if(code!=200)throw new IllegalStateException("整理服务暂不可用，请稍后重试；输入内容仍保留");
   JSONObject choice=new JSONObject(read(connection.getInputStream())).getJSONArray("choices").getJSONObject(0);
   if(!"stop".equals(choice.optString("finish_reason")))throw new IllegalStateException("整理结果不完整，请缩短描述后重试；输入内容仍保留");
   return new JSONObject(choice.getJSONObject("message").getString("content"));
  }finally{connection.disconnect();}
 }
 private String read(InputStream input)throws Exception{
  try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){
   byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>100000)throw new IllegalStateException("整理响应过大，请稍后重试；输入内容仍保留");}
   return new String(out.toByteArray(),StandardCharsets.UTF_8);
  }
 }
}
