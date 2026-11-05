package cn.xunji.probe;
import java.time.*;
final class ScheduleCompletionPolicy {
    static boolean eligible(String category){return category!=null&&!category.isEmpty();}
    static long deadlineMillis(String category,String date,String time){
        if("课程".equals(category)||date==null||date.isEmpty())return Long.MAX_VALUE;
        try{LocalDate day=LocalDate.parse(date);if(day.getYear()<1)return Long.MAX_VALUE;
            return (time==null||time.isEmpty()?day.plusDays(1).atStartOfDay():day.atTime(LocalTime.parse(time))).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
        }catch(Exception e){return Long.MAX_VALUE;}
    }
    static long endMillis(String category,String date,String start,String end){
        if(!eligible(category)||date==null||start==null||end==null||!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")||!start.matches("[0-9]{2}:[0-9]{2}")||!end.matches("[0-9]{2}:[0-9]{2}"))return Long.MAX_VALUE;
        try{LocalDate day=LocalDate.parse(date);LocalTime from=LocalTime.parse(start),to=LocalTime.parse(end);if(day.getYear()<1||!to.isAfter(from))return Long.MAX_VALUE;return day.atTime(to).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();}catch(DateTimeException e){return Long.MAX_VALUE;}
    }
}
