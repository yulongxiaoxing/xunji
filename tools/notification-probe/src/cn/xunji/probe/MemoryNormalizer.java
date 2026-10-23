package cn.xunji.probe;
import org.json.*;

final class MemoryNormalizer {
    static JSONObject normalize(JSONObject row,JSONObject model,String text,long received,boolean single) throws Exception {
        JSONObject result=new JSONObject(row.toString());JSONArray requirements=result.getJSONArray("requirements");
        // Multi-item dates must come from this item's verbatim source fragment.
        String fragment=model.optString("scheduleText").trim();
        if(fragment.isEmpty()||!text.contains(fragment))fragment="";
        String context=single?text:fragment,category=result.optString("category");
        if((category.equals("课程")||category.equals("其他"))&&context.matches("(?s).*(培训会|分享会|讲座|会议).*" )&&!context.contains("课程")&&!context.contains("上课"))category="活动";
        result.put("category",category);result.remove("course");result.remove("event");result.remove("repeat");
        String date="",time="";
        EventPolicy.Interval quoted=EventPolicy.fromReceived(context,received);
        java.time.LocalDate reference=java.time.Instant.ofEpochMilli(received).atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate();
        SchedulePolicy.Deadline point=SchedulePolicy.parse(TimeText.normalize(context),reference);
        if(!point.date.isEmpty()){date=point.date;if(!point.time.isEmpty())time=point.time;}
        JSONObject interval=model.optJSONObject(category.equals("课程")?"course":"event");
        if(interval==null&&category.equals("活动")&&model.optString("category").equals("课程"))interval=model.optJSONObject("course");
        EventPolicy.Interval sourceClocks=EventPolicy.clockRange(context);String start=sourceClocks==null?"":sourceClocks.start,end=sourceClocks==null?"":sourceClocks.end;
        if(sourceClocks==null)time=point.time;
        if(quoted!=null){date=quoted.date;start=quoted.start;end=quoted.end;}
        boolean complete=!date.isEmpty()&&!start.isEmpty()&&!end.isEmpty()&&start.compareTo(end)<0;
        if(complete&&(category.equals("课程")||EventPolicy.category(category))){
            JSONObject normalized=new JSONObject().put("location",interval==null?"":interval.optString("location")).put("startTime",start).put("endTime",end);
            if(category.equals("课程"))normalized.put("teacher",interval==null?"":interval.optString("teacher"));
            result.put(category.equals("课程")?"course":"event",normalized);time=start;
        }else if(category.equals("课程")){
            category="其他";result.put("category",category);requirements.put("课程日期或起止时间待确认；原文："+context);
        }else if(interval!=null&&!complete){requirements.put("起止时间待确认，请核对原文。");}
        if(date.isEmpty())time="";result.put("dueDate",date).put("dueTime",time);
        JSONObject repeat=model.optJSONObject("repeat");
        if(category.equals("课程")&&repeat!=null&&!repeat.optString("rule").equals("none")){
            try{CoursePolicy.repeatDates(date,repeat.optString("rule"),repeat.optInt("weekday"),repeat.optString("mode"),repeat.optInt("count"),repeat.optString("until"));result.put("repeat",repeat);}
            catch(IllegalArgumentException e){requirements.put("重复规律待确认，请核对原文。");}
        }
        return result;
    }
}
