package cn.xunji.probe;
import java.time.*;
public class EventPolicyTest {
    static int checks;
    static final long RECEIVED=Instant.parse("2026-10-23T04:00:00Z").toEpochMilli();
    static void interval(String text,String date,String start,String end){
        EventPolicy.Interval value=EventPolicy.fromReceived(text,RECEIVED);checks++;
        if(value==null||!date.equals(value.date)||!start.equals(value.start)||!end.equals(value.end))throw new AssertionError(text);
    }
    static void absent(String text){checks++;if(EventPolicy.fromReceived(text,RECEIVED)!=null)throw new AssertionError(text);}
    static void clock(String text,String expected){checks++;if(!TimeText.clock(text).equals(expected))throw new AssertionError(text);}
    public static void main(String[] args){
        interval("比赛定于2026年10月28日14:00—17:00举行","2026-10-28","14:00","17:00");
        interval("考试时间为2026年10月27日9:00—11:00","2026-10-27","09:00","11:00");
        interval("活动将于2026年10月26日18:30—20:00举行","2026-10-26","18:30","20:00");
        interval("明天下午3点至5点","2026-10-24","15:00","17:00");
        interval("我明天上午八点半到十一点半有一个图像视觉部的培训会。","2026-10-24","08:30","11:30");
        interval("后天下午两点三十分到四点十分开会","2026-10-25","14:30","16:10");
        clock("8:30","08:30");clock("下午两点三十五分","14:35");clock("8:30—11:30","");clock("25:00","");clock("","");
        interval("2026-10-28 14:00-17:00\n2026年10月28日14:00—17:00","2026-10-28","14:00","17:00");
        interval("2026/10/28 下午3:00～晚上7:30","2026-10-28","15:00","19:30");
        interval("2026年10月28日\n9:00至11:00","2026-10-28","09:00","11:00");
        interval("活动将于2026年10月26日18:30—20:00举行，可携带手机或相机。","2026-10-26","18:30","20:00");
        interval("2026年10月28日中午1点至2点","2026-10-28","13:00","14:00");
        interval("2026年10月28日中午12点至1点","2026-10-28","12:00","13:00");
        absent("活动定于2026年10月28日至30日14:00—17:00举行");
        absent("活动定于2026-10-28至30日14:00—17:00举行");
        absent("2026年10月28日14:00或15:00—17:00");
        absent("2026年10月28日9点或10点至11点");
        absent("2026年10月28日上午或下午3点至5点");
        absent("2026年10月28日9时或者10时至11时");
        absent("2026年10月28日18:00截止");absent("14:00—17:00");
        absent("2026年10月28日14:00—17:00或18:00—19:00");
        absent("2026年10月28日14:00—17:00，2026年10月29日14:00—17:00");
        absent("2026年10月28日23:00—01:00");absent("2026年10月28日9:00—9:00");
        absent("2026年10月28日25:00—26:00");absent("2026年10月28日23:00—24:00");
        absent("报名截止2026年10月25日，活动2026年10月28日14:00—17:00");
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Los_Angeles"));
        interval("今天9:00—11:00","2026-10-23","09:00","11:00");
        EventPolicy.Interval nextDay=EventPolicy.fromReceived("明天上午八点半到十一点半有培训会",Instant.parse("2026-10-24T04:00:00Z").toEpochMilli());checks++;if(nextDay==null||!nextDay.date.equals("2026-10-25"))throw new AssertionError("tomorrow must follow each received date");
        System.out.println(checks+" source-grounded event interval checks passed");
    }
}
