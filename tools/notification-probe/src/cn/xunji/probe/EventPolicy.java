package cn.xunji.probe;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Only explicit, unambiguous, same-day intervals become event blocks. */
final class EventPolicy {
    private static final String PERIOD="(?:上午|下午|晚上|中午|早上|凌晨|晚)?";
    private static final String CLOCK=PERIOD+"\\s*[0-9]{1,2}(?:[:：][0-9]{2}|[点时](?:[0-9]{1,2}分?|半)?)";
    private static final Pattern RANGE=Pattern.compile("(?<![0-9])("+CLOCK+")\\s*(?:—+|–|-|~|～|至|到)\\s*("+CLOCK+")(?![0-9])");
    private static final Pattern PERIOD_WORD=Pattern.compile("上午|下午|晚上|中午|早上|凌晨|晚");
    private static final Pattern DATE_RANGE=Pattern.compile("(?<![0-9])(?:[0-9]{1,2}[日号]|(?:[0-9]{4}[-/.])?[0-9]{1,2}[-/.月][0-9]{1,2}[日号]?)\\s*(?:至|到|[-—~～])\\s*(?:[0-9]{1,2}月)?[0-9]{1,2}(?:日|号)?");
    private static final Pattern TIME_ALTERNATIVE=Pattern.compile("(?:上午|下午|晚上|中午|早上|凌晨|晚|[日号天一二三四五六七0-9点时分半])\\s*或(?:者)?\\s*(?:[0-9今明后本下上周星一二三四五六七]|下午|上午|晚上|中午)");
    static final class Interval {
        final String date,start,end;
        Interval(String date,String start,String end){this.date=date;this.start=start;this.end=end;}
    }
    static boolean category(String category){return "活动".equals(category)||"考试".equals(category)||"竞赛".equals(category);}
    static Interval fromReceived(String text,long received){
        text=TimeText.normalize(text);Interval clocks=clockRange(text);if(clocks==null)return null;
        LocalDate reference=Instant.ofEpochMilli(received).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
        String dates=RANGE.matcher(text).replaceAll(" ");
        SchedulePolicy.Deadline day=SchedulePolicy.parse(dates.replace("或",""),reference);
        return day.date.isEmpty()?null:new Interval(day.date,clocks.start,clocks.end);
    }
    static Interval clockRange(String text){
        text=TimeText.normalize(text);
        if(DATE_RANGE.matcher(text).find()||TIME_ALTERNATIVE.matcher(text).find())return null;
        Set<String> intervals=new LinkedHashSet<>();Matcher ranges=RANGE.matcher(text);LocalDate reference=LocalDate.of(2000,1,1);
        while(ranges.find()){
            String from=ranges.group(1),to=ranges.group(2);Matcher period=PERIOD_WORD.matcher(from);
            if(period.find()&&!PERIOD_WORD.matcher(to).find())to=period.group()+to;
            from=from.replace("中午","下午");to=to.replace("中午","下午");
            SchedulePolicy.Deadline start=SchedulePolicy.parse("2000-01-01 "+from,reference),end=SchedulePolicy.parse("2000-01-01 "+to,reference);
            if(start.time.isEmpty()||end.time.isEmpty()||!start.date.equals("2000-01-01")||!end.date.equals("2000-01-01")||start.time.compareTo(end.time)>=0)return null;
            intervals.add(start.time+"|"+end.time);
        }
        if(intervals.size()!=1)return null;String[] times=intervals.iterator().next().split("\\|");return new Interval("",times[0],times[1]);
    }
}
