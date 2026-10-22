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
    static String key(String course,String date,int node,int step){return "course:"+UUID.nameUUIDFromBytes((course+"|"+date+"|"+node+"|"+step).getBytes(StandardCharsets.UTF_8));}
}
