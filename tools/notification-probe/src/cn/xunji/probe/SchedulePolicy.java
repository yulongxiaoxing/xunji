package cn.xunji.probe;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Instant;
import java.time.ZoneId;
import java.util.regex.*;
import java.util.*;

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
    static final class Deadline {
        final String date,time;
        Deadline(String date,String time){this.date=date;this.time=time;}
    }
    static Deadline fromReceived(String text,long received){
        return parse(text,Instant.ofEpochMilli(received).atZone(ZoneId.systemDefault()).toLocalDate());
    }
    static Deadline parse(String text,LocalDate reference){
        if(text==null||text.trim().isEmpty())return new Deadline("","");
        String value=text.trim();if(value.contains("或"))return new Deadline("","");LocalDate date=null;int dateEnd=0;
        Set<LocalDate> candidates=new HashSet<>();
        Pattern absolute=Pattern.compile("(?<![0-9])(?:([0-9]{4})[年/.-])?([0-9]{1,2})[月/.-]([0-9]{1,2})(?:日|号)?(?![0-9])");
        Matcher m=absolute.matcher(value);
        while(m.find()){
            try{candidates.add(LocalDate.of(m.group(1)==null?reference.getYear():Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3))));dateEnd=m.end();}
            catch(Exception e){return new Deadline("","");}
        }
        Matcher relative=Pattern.compile("大后天|大前天|后天|前天|昨天|明天|明晚|今天|今晚|今日").matcher(value);
        while(relative.find()){
            String word=relative.group();int offset=word.equals("大后天")?3:word.equals("后天")?2:word.equals("明天")||word.equals("明晚")?1:word.equals("昨天")?-1:word.equals("前天")?-2:word.equals("大前天")?-3:0;
            candidates.add(reference.plusDays(offset));
        }
        Matcher week=Pattern.compile("(下下|下|上上|上|本|这)?(?:周|星期)([一二三四五六日天])").matcher(value);
        while(week.find()){
            int target="一二三四五六日".indexOf(week.group(2).replace('天','日'))+1;String prefix=week.group(1);
            LocalDate monday=reference.minusDays(reference.getDayOfWeek().getValue()-1);
            LocalDate weekday=monday.plusDays(target-1+("下下".equals(prefix)?14:"下".equals(prefix)?7:"上上".equals(prefix)?-14:"上".equals(prefix)?-7:0));
            // Parenthesized weekday alongside an absolute date is a redundant label.
            if(prefix==null&&!candidates.isEmpty()&&dateEnd>0){boolean agrees=false;for(LocalDate candidate:candidates)if(candidate.getDayOfWeek().getValue()==target)agrees=true;if(agrees)continue;}
            if(prefix==null&&weekday.isBefore(reference))weekday=weekday.plusDays(7);candidates.add(weekday);
        }
        if(candidates.size()!=1)return new Deadline("","");date=candidates.iterator().next();
        if(date==null||date.getYear()<1||date.getYear()>9999)return new Deadline("","");
        String tail=value.substring(dateEnd);Matcher time=Pattern.compile("(?<![0-9])([0-9]{1,2})(?:[:：]([0-9]{2})|[点时](?:([0-9]{1,2})分?|半)?)(?![0-9])").matcher(tail);
        String clock="";
        if(time.find()){
            int hour=Integer.parseInt(time.group(1)),minute=time.group(2)!=null?Integer.parseInt(time.group(2)):time.group(3)!=null?Integer.parseInt(time.group(3)):time.group().endsWith("半")?30:0;
            int timeStart=time.start();boolean multiple=time.find();String period=value.substring(0,dateEnd+timeStart);
            if(!multiple&&hour<=24&&minute<60&&!(hour==24&&minute!=0)){
                if((period.contains("下午")||period.contains("晚上")||period.contains("今晚")||period.contains("明晚")||period.contains("晚"))&&hour>=1&&hour<12)hour+=12;
                if((period.contains("晚上")||period.contains("今晚")||period.contains("明晚"))&&hour==12)hour=24;
                if(period.contains("上午")&&hour==12)hour=0;
                if(hour==24){date=date.plusDays(1);hour=0;}
                if(date.getYear()<=9999)clock=String.format(java.util.Locale.ROOT,"%02d:%02d",hour,minute);else return new Deadline("","");
            }
        }
        return new Deadline(date.toString(),clock);
    }
}
