package cn.xunji.probe;
import java.time.LocalDate;
import java.time.LocalTime;

/** Explicit user-confirmed scheduling, independent of quoted deadline wording. */
final class SchedulePolicy {
    static void validate(String date,String time){
        if(date.isEmpty()){
            if(!time.isEmpty())throw new IllegalArgumentException("请先选择日期，再填写时间");
            return;
        }
        try{
            if(!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")||date.startsWith("0000"))throw new IllegalArgumentException();
            LocalDate.parse(date);
            if(!time.isEmpty()){
                if(!time.matches("[0-9]{2}:[0-9]{2}"))throw new IllegalArgumentException();
                LocalTime.parse(time);
            }
        }catch(Exception error){throw new IllegalArgumentException("请填写有效的日期和时间");}
    }
    static boolean included(String state){return "todo".equals(state)||"done".equals(state);}
}
