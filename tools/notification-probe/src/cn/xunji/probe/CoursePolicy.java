package cn.xunji.probe;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.nio.charset.StandardCharsets;

final class CoursePolicy {
    static List<String> dates(String start,int day,int from,int to,int type){
        if(day<1||day>7||from<1||to<from||to>60||type<0||type>2)throw new IllegalArgumentException("课程周次或星期无效，未导入");
        LocalDate monday=LocalDate.parse(start).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<String> dates=new ArrayList<>();
        for(int week=from;week<=to;week++)if(type==0||type==1&&week%2==1||type==2&&week%2==0)dates.add(monday.plusDays((week-1)*7L+day-1).toString());
        return dates;
    }
    static void validateTime(String start,String end){
        try{if(!start.matches("[0-9]{2}:[0-9]{2}")||!end.matches("[0-9]{2}:[0-9]{2}")||!LocalTime.parse(end).isAfter(LocalTime.parse(start)))throw new IllegalArgumentException();}
        catch(Exception e){throw new IllegalArgumentException("课程起止时间不完整或无效，未导入");}
    }
    static List<String> repeatDates(String start,String rule,int weekday,String mode,int count,String until){
        if("none".equals(rule))return Collections.singletonList(start);
        if(!"weekly".equals(rule)&&!"biweekly".equals(rule))throw new IllegalArgumentException("请选择有效重复规律");
        if(weekday<1||weekday>7||start.isEmpty())throw new IllegalArgumentException("请设置首次日期和星期");
        LocalDate base=LocalDate.parse(start).with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.of(weekday)));
        boolean byCount="count".equals(mode);if(!byCount&&!"until".equals(mode))throw new IllegalArgumentException("请选择重复结束方式");
        if(byCount&&(count<1||count>104))throw new IllegalArgumentException("重复次数应为1至104次");
        LocalDate limit=byCount?base.plusYears(2):LocalDate.parse(until);
        if(limit.isBefore(base)||limit.isAfter(base.plusYears(2)))throw new IllegalArgumentException("截止日须在首次上课后两年内");
        List<String> result=new ArrayList<>();int step="biweekly".equals(rule)?2:1;
        for(LocalDate day=base;!day.isAfter(limit)&&(!byCount||result.size()<count);day=day.plusWeeks(step)){
            if(result.size()>=104)throw new IllegalArgumentException("重复安排不能超过104次");result.add(day.toString());
        }
        if(byCount&&result.size()!=count)throw new IllegalArgumentException("重复安排超出两年范围");return result;
    }
    static long endMillis(String date,String end){return LocalDate.parse(date).atTime(LocalTime.parse(end)).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();}
    static boolean ended(String date,String end,long now){return now>=endMillis(date,end);}
    static String key(String course,String date,int node,int step){return "course:"+UUID.nameUUIDFromBytes((course+"|"+date+"|"+node+"|"+step).getBytes(StandardCharsets.UTF_8));}
}
