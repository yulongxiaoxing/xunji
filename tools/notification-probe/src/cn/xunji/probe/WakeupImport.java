package cn.xunji.probe;
import org.json.*;
import java.util.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.*;

/** Strict, lossless import of the verified WakeUp five-part share payload. */
final class WakeupImport {
    static String shareKey(String text){
        Matcher m=Pattern.compile("(?i)(?<![a-z0-9])[0-9a-f]{32}(?![a-z0-9])").matcher(text.trim());
        if(!m.find())throw new IllegalArgumentException("请粘贴完整WakeUp分享口令");String key=m.group();
        if(m.find())throw new IllegalArgumentException("发现多个口令，请一次只导入一份课表");return key;
    }
    static JSONObject fetch(String text) throws Exception {
        String key=shareKey(text);
        HttpURLConnection connection=(HttpURLConnection)new URL("https://i.wakeup.fun/share_schedule/get?key="+key).openConnection();
        connection.setConnectTimeout(15000);connection.setReadTimeout(20000);connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent","Xunji/0.22");
        try{
            if(connection.getResponseCode()!=200)throw new IllegalArgumentException("WakeUp连接失败，请稍后重试；已有数据保留");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=connection.getInputStream()){
                byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){bytes.write(buffer,0,n);if(bytes.size()>2000000)throw new IllegalArgumentException("课表过大，未导入");}
            }
            JSONObject response=new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
            if(response.optInt("status")==5000004)throw new IllegalArgumentException("此口令使用新版WakeUp分享协议，当前分享接口无法读取；未导入任何课程");
            Object data=response.opt("data");if(!(data instanceof String)||((String)data).trim().isEmpty())throw new IllegalArgumentException("口令已失效或课表未返回，请在WakeUp重新分享");
            return parse((String)data);
        }finally{connection.disconnect();}
    }
    static String time(String value){
        try{LocalTime t=LocalTime.parse(value);if(t.getSecond()!=0||t.getNano()!=0)throw new IllegalArgumentException();return t.format(DateTimeFormatter.ofPattern("HH:mm",Locale.ROOT));}
        catch(Exception e){throw new IllegalArgumentException("课表含无法完整识别的作息时间，未导入");}
    }
    static JSONObject parse(String raw) throws Exception {
        String[] lines=raw.trim().split("\\r?\\n");if(lines.length!=5)throw new IllegalArgumentException("暂不识别此WakeUp课表格式，未导入");
        JSONArray times=new JSONArray(lines[1]),bases=new JSONArray(lines[3]),details=new JSONArray(lines[4]);JSONObject table=new JSONObject(lines[2]);
        String start=table.getString("startDate"),tableName=table.optString("tableName","WakeUp课表");LocalDate.parse(start);
        if(details.length()==0||details.length()>500)throw new IllegalArgumentException("课表没有课程或数量超出范围，未导入");
        Map<Integer,JSONObject> slots=new HashMap<>(),names=new HashMap<>();
        for(int i=0;i<times.length();i++){JSONObject slot=times.getJSONObject(i);if(slots.put(slot.getInt("node"),slot)!=null)throw new IllegalArgumentException("课表存在重复节次，未导入");}
        for(int i=0;i<bases.length();i++){JSONObject base=bases.getJSONObject(i);if(names.put(base.getInt("id"),base)!=null)throw new IllegalArgumentException("课程编号重复，未导入");}
        JSONArray meetings=new JSONArray(),sessions=new JSONArray();Set<String> seen=new HashSet<>();
        for(int i=0;i<details.length();i++){
            JSONObject detail=details.getJSONObject(i),base=names.get(detail.getInt("id"));if(base==null)throw new IllegalArgumentException("课程名称资料缺失，未导入");
            String title=base.getString("courseName").trim();if(title.isEmpty())throw new IllegalArgumentException("存在无名称课程，未导入");
            int node=detail.getInt("startNode"),step=detail.getInt("step");if(node<1||step<1||step>30)throw new IllegalArgumentException("课程节次无效，未导入");
            String from="",to="";
            if(detail.has("ownTime")&&!(detail.opt("ownTime") instanceof Boolean))throw new IllegalArgumentException("自定义时间标记无法识别，未导入");
            if(detail.optBoolean("ownTime")){
                if(!detail.has("startTime")||!detail.has("endTime"))throw new IllegalArgumentException("自定义时刻课程缺少完整时间，未导入");
                from=time(detail.getString("startTime"));to=time(detail.getString("endTime"));
            }else{
                for(int n=node;n<node+step;n++){
                    JSONObject slot=slots.get(n);if(slot==null)throw new IllegalArgumentException("第"+n+"节作息缺失，未导入");
                    String slotFrom=time(slot.getString("startTime")),slotTo=time(slot.getString("endTime"));CoursePolicy.validateTime(slotFrom,slotTo);
                    if(n==node)from=slotFrom;else if(slotFrom.compareTo(to)<0)throw new IllegalArgumentException("课程作息顺序异常，未导入");to=slotTo;
                }
            }
            CoursePolicy.validateTime(from,to);
            int day=detail.getInt("day"),first=detail.getInt("startWeek"),last=detail.getInt("endWeek"),type=detail.getInt("type");
            List<String> dates=CoursePolicy.dates(start,day,first,last,type);List<Integer> activeWeeks=new ArrayList<>();boolean explicit=detail.has("stepWeekList");
            if(explicit){
                JSONArray weeks=detail.getJSONArray("stepWeekList");if(weeks.length()==0)throw new IllegalArgumentException("课程指定周次为空，未导入");
                TreeSet<Integer> ordered=new TreeSet<>();for(int w=0;w<weeks.length();w++){int week=weeks.getInt(w);if(week<1||week>60)throw new IllegalArgumentException("指定周次无效，未导入");ordered.add(week);}
                dates=new ArrayList<>();for(int week:ordered){activeWeeks.add(week);dates.addAll(CoursePolicy.dates(start,day,week,week,0));}
            }else for(int week=first;week<=last;week++)if(type==0||type==1&&week%2==1||type==2&&week%2==0)activeWeeks.add(week);
            String teacher=detail.optString("teacher","").trim();if(teacher.isEmpty())teacher=base.optString("teacher","");
            JSONObject course=new JSONObject().put("name",title).put("teacher",teacher)
                .put("location",detail.optString("room","")).put("startTime",from).put("endTime",to).put("day",day)
                .put("startWeek",first).put("endWeek",last).put("weekType",type).put("startNode",node).put("step",step)
                .put("explicitWeeks",explicit).put("weeks",new JSONArray(activeWeeks)).put("termStart",start).put("tableName",tableName).put("rawBase",base).put("rawDetail",detail);
            course.put("importIdentity",new JSONArray().put(title).put(teacher).put(course.getString("location")).put(from).put(to).put(day).put(first).put(last).put(type).put(new JSONArray(activeWeeks)).toString());
            meetings.put(course);
            for(String date:dates){
                String id=CoursePolicy.key(new JSONArray().put(start).put(tableName).put(base.getInt("id")).put(title).toString(),date,node,step);
                if(!seen.add(id))throw new IllegalArgumentException("存在同日同节次的重复课程资料，请先检查课表");
                sessions.put(new JSONObject().put("id",id).put("date",date).put("course",course));
                if(sessions.length()>3000)throw new IllegalArgumentException("课次数量超出范围，未导入");
            }
        }
        if(sessions.length()==0)throw new IllegalArgumentException("没有对应周次的课次，未导入");
        return new JSONObject().put("tableName",tableName).put("termStart",start).put("meetings",meetings).put("sessions",sessions).put("raw",raw);
    }
}
