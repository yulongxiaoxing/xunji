package cn.xunji.probe;

import org.json.*;
import java.time.*;
import java.time.format.*;
import java.time.temporal.*;
import java.util.*;
import java.util.regex.*;

/** Bounded ICS course import. Unsupported recurrence semantics fail the whole preview. */
final class IcsImport {
    static final ZoneId LOCAL=ZoneId.of("Asia/Shanghai");
    static final DateTimeFormatter FORMAT=DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss").withResolverStyle(ResolverStyle.STRICT);
    static final class Property {
        String name,value;Map<String,String> params=new HashMap<>();
        Property(String line){
            int colon=-1;boolean quoted=false;
            for(int i=0;i<line.length();i++){char c=line.charAt(i);if(c=='"')quoted=!quoted;if(c==':'&&!quoted){colon=i;break;}}
            if(colon<1)throw fail("属性格式错误");String[] head=line.substring(0,colon).split(";");name=head[0].toUpperCase(Locale.ROOT);value=line.substring(colon+1);
            for(int i=1;i<head.length;i++){int eq=head[i].indexOf('=');if(eq<1)throw fail("参数格式错误");params.put(head[i].substring(0,eq).toUpperCase(Locale.ROOT),head[i].substring(eq+1).replace("\"",""));}
        }
    }
    static IllegalArgumentException fail(String message){return new IllegalArgumentException("ICS课表："+message+"；未导入任何课程");}
    static String text(String value){
        StringBuilder out=new StringBuilder();for(int i=0;i<value.length();i++){char c=value.charAt(i);if(c=='\\'){if(++i>=value.length())throw fail("文字转义不完整");c=value.charAt(i);if(c=='n'||c=='N')c='\n';else if(c!='\\'&&c!=','&&c!=';')throw fail("文字转义无法识别");}out.append(c);}return out.toString();
    }
    static Property one(Map<String,List<Property>> event,String name,boolean required){
        List<Property> list=event.get(name);if(list==null){if(required)throw fail("缺少"+name);return null;}if(list.size()!=1)throw fail(name+"重复");return list.get(0);
    }
    static String value(Map<String,List<Property>> event,String name){Property p=one(event,name,false);return p==null?"":text(p.value);}
    static ZonedDateTime date(Property p,String input){
        if(p.params.containsKey("VALUE")&&!p.params.get("VALUE").equals("DATE-TIME"))throw fail("全天课程没有完整起止时间");
        try{
            boolean utc=input.endsWith("Z");if(utc&&p.params.containsKey("TZID"))throw fail("时区标记冲突");
            ZoneId zone=utc?ZoneOffset.UTC:ZoneId.of(p.params.getOrDefault("TZID","Asia/Shanghai"));
            LocalDateTime local=LocalDateTime.parse(utc?input.substring(0,input.length()-1):input,FORMAT);
            if(zone.getRules().getValidOffsets(local).size()!=1)throw fail("上课时间处于时区切换歧义中");
            return local.atZone(zone);
        }catch(IllegalArgumentException e){throw e;}catch(Exception e){throw fail("时间或时区无法识别");}
    }
    static JSONObject parse(String raw) throws Exception {
        String unfolded=raw.replaceFirst("^\uFEFF","").replace("\r\n","\n").replaceAll("\n[ \t]","");
        String[] lines=unfolded.split("\n");List<Map<String,List<Property>>> events=new ArrayList<>();Map<String,List<Property>> event=null;
        Deque<String> stack=new ArrayDeque<>();String table="ICS课表";boolean calendar=false,ended=false;
        for(String line:lines){if(line.isEmpty())continue;Property p=new Property(line);
            if(p.name.equals("BEGIN")){
                if(stack.isEmpty()){if(calendar||!p.value.equals("VCALENDAR"))throw fail("不是单份日历");calendar=true;}
                else if(p.value.equals("VEVENT")){if(stack.size()!=1)throw fail("事件结构异常");event=new LinkedHashMap<>();}
                else if(!p.value.equals("VALARM")&&!p.value.equals("VTIMEZONE")&&!p.value.equals("STANDARD")&&!p.value.equals("DAYLIGHT"))throw fail("不支持组件"+p.value);
                stack.push(p.value);continue;
            }
            if(p.name.equals("END")){if(stack.isEmpty()||!stack.pop().equals(p.value))throw fail("文件结构不完整");if(p.value.equals("VEVENT")){events.add(event);event=null;}if(p.value.equals("VCALENDAR"))ended=true;continue;}
            if(stack.isEmpty())throw fail("日历结构异常");
            if(stack.peek().equals("VEVENT"))event.computeIfAbsent(p.name,k->new ArrayList<>()).add(p);
            else if(stack.peek().equals("VCALENDAR")&&p.name.equals("X-WR-CALNAME"))table=text(p.value);
        }
        if(!calendar||!ended||!stack.isEmpty()||events.isEmpty()||events.size()>500)throw fail("事件缺失或文件不完整");
        JSONArray sessions=new JSONArray(),meetings=new JSONArray();Set<String> ids=new HashSet<>();String first="";
        for(Map<String,List<Property>> e:events){
            if(e.containsKey("RECURRENCE-ID")||e.containsKey("EXRULE"))throw fail("暂不支持例外改期，请导出展开后的课程");
            String status=value(e,"STATUS");if(!status.isEmpty()&&!status.equals("CONFIRMED")&&!status.equals("TENTATIVE"))throw fail("包含取消或未知状态的课程");
            Property start=one(e,"DTSTART",true),end=one(e,"DTEND",true);ZonedDateTime from=date(start,start.value),to=date(end,end.value);
            Duration duration=Duration.between(from.toInstant(),to.toInstant());if(duration.isNegative()||duration.isZero()||duration.toHours()>=24)throw fail("课程起止时间无效");
            String name=value(e,"SUMMARY").trim();if(name.isEmpty())throw fail("缺少课程名称");
            String description=value(e,"DESCRIPTION"),location=value(e,"LOCATION"),teacher=value(e,"X-TEACHER");
            if(teacher.isEmpty()){Matcher m=Pattern.compile("(?m)^\\s*(?:任课老师|任课教师|授课教师|教师|老师|Teacher)\\s*[:：]\\s*([^\\n]+)",Pattern.CASE_INSENSITIVE).matcher(description);if(m.find())teacher=m.group(1).trim();}
            TreeSet<ZonedDateTime> dates=new TreeSet<>(Comparator.comparing(ZonedDateTime::toInstant));dates.add(from);
            Property rule=one(e,"RRULE",false);
            if(rule!=null){
                Map<String,String> r=new HashMap<>();for(String item:rule.value.split(";")){String[] kv=item.split("=",-1);if(kv.length!=2||r.put(kv[0],kv[1])!=null)throw fail("重复规则无效");}
                for(String key:r.keySet())if(!Arrays.asList("FREQ","INTERVAL","COUNT","UNTIL","BYDAY","WKST").contains(key))throw fail("暂不支持重复规则"+key);
                boolean weekly="WEEKLY".equals(r.get("FREQ"));if(!weekly&&!"DAILY".equals(r.get("FREQ")))throw fail("暂只支持每日/每周重复");
                int interval=Integer.parseInt(r.getOrDefault("INTERVAL","1")),count=r.containsKey("COUNT")?Integer.parseInt(r.get("COUNT")):Integer.MAX_VALUE;if(interval<1||interval>60||count<1||r.containsKey("COUNT")&&count>3000)throw fail("重复次数超出范围");
                if(!r.containsKey("COUNT")&&!r.containsKey("UNTIL")||r.containsKey("COUNT")&&r.containsKey("UNTIL"))throw fail("重复课程须有唯一结束条件");
                ZonedDateTime until=r.containsKey("UNTIL")?date(new Property("DTSTART"+(r.get("UNTIL").endsWith("Z")?"":";TZID="+from.getZone().getId())+":"+r.get("UNTIL")),r.get("UNTIL")):null;
                List<String> days=Arrays.asList("MO","TU","WE","TH","FR","SA","SU");String weekStart=r.getOrDefault("WKST","MO");if(!days.contains(weekStart))throw fail("周起始日无效");
                Set<Integer> byDays=new HashSet<>();if(r.containsKey("BYDAY")){for(String d:r.get("BYDAY").split(",")){if(!days.contains(d))throw fail("星期重复规则无法识别");byDays.add(days.indexOf(d)+1);}}
                if(weekly&&byDays.isEmpty())byDays.add(from.getDayOfWeek().getValue());
                LocalDate weekBase=from.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.of(days.indexOf(weekStart)+1)));
                if(!byDays.isEmpty()&&!byDays.contains(from.getDayOfWeek().getValue()))throw fail("首次课程与重复星期不一致");
                dates.clear();boolean finished=false;
                for(int i=0;i<=3660;i++){
                    ZonedDateTime next=from.plusDays(i);if(until!=null&&next.toInstant().isAfter(until.toInstant())){finished=true;break;}
                    long cycle=weekly?ChronoUnit.WEEKS.between(weekBase,next.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.of(days.indexOf(weekStart)+1)))):i;
                    if(cycle%interval==0&&(byDays.isEmpty()||byDays.contains(next.getDayOfWeek().getValue()))){if(!next.toLocalTime().equals(from.toLocalTime())||next.getZone().getRules().getValidOffsets(next.toLocalDateTime()).size()!=1)throw fail("重复课程遇到时区切换歧义");dates.add(next);if(dates.size()>3000)throw fail("课程超过3000次");if(dates.size()==count){finished=true;break;}}
                }if(!finished)throw fail("重复范围超过十年");
            }
            for(Property p:e.getOrDefault("RDATE",Collections.emptyList()))for(String d:p.value.split(","))dates.add(date(p,d));
            for(Property p:e.getOrDefault("EXDATE",Collections.emptyList()))for(String d:p.value.split(","))dates.remove(date(p,d));
            String uid=value(e,"UID");if(uid.isEmpty())uid=new JSONArray().put(name).put(location).put(from.toString()).toString();
            for(ZonedDateTime occurrence:dates){
                ZonedDateTime a=occurrence.withZoneSameInstant(LOCAL),b=occurrence.plus(duration).withZoneSameInstant(LOCAL);
                if(!a.toLocalDate().equals(b.toLocalDate())||a.getSecond()!=0||b.getSecond()!=0)throw fail("跨日或含秒的课程暂不支持");
                String day=a.toLocalDate().toString(),begin=a.toLocalTime().toString(),finish=b.toLocalTime().toString();CoursePolicy.validateTime(begin,finish);
                JSONObject c=new JSONObject().put("format","ics").put("name",name).put("location",location).put("teacher",teacher).put("description",description).put("startTime",begin).put("endTime",finish).put("date",day).put("day",a.getDayOfWeek().getValue());
                c.put("importIdentity",new JSONArray().put(name).put(location).put(teacher).put(description).put(begin).put(finish).toString());
                String id=CoursePolicy.key("ics:"+uid,day,a.getHour()*60+a.getMinute(),0);if(!ids.add(id))throw fail("存在重复课次标识");
                sessions.put(new JSONObject().put("id",id).put("date",day).put("course",c));meetings.put(c);if(first.isEmpty()||day.compareTo(first)<0)first=day;
                if(sessions.length()>3000)throw fail("课程超过3000次");
            }
        }
        if(sessions.length()==0)throw fail("没有有效课次");
        return new JSONObject().put("format","ics").put("tableName",table).put("termStart",first).put("meetings",meetings).put("sessions",sessions).put("raw",raw);
    }
}
