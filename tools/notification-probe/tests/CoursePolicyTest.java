package cn.xunji.probe;
import java.time.LocalDate;
import java.util.*;
public class CoursePolicyTest {
 static int count;static void check(boolean v,String m){count++;if(!v)throw new AssertionError(m);}
 public static void main(String[] args){
  check(CoursePolicy.dates("2026-09-02",1,1,4,1).equals(Arrays.asList("2026-08-31","2026-09-14")),"odd weeks use first week's Monday");
  check(CoursePolicy.dates("2026-09-02",5,1,4,2).equals(Arrays.asList("2026-09-11","2026-09-25")),"even weeks preserved");
  check(CoursePolicy.dates("2026-12-28",1,1,2,0).equals(Arrays.asList("2026-12-28","2027-01-04")),"cross year");
  try{CoursePolicy.dates("2026-09-01",8,1,4,0);throw new AssertionError("bad weekday accepted");}catch(IllegalArgumentException e){count++;}
  try{CoursePolicy.dates("2026-09-01",1,0,4,0);throw new AssertionError("bad weeks accepted");}catch(IllegalArgumentException e){count++;}
  CoursePolicy.validateTime("08:00","09:40");count++;
  try{CoursePolicy.validateTime("10:00","09:00");throw new AssertionError("inverted times accepted");}catch(IllegalArgumentException e){count++;}
  check(CoursePolicy.key("课程id","2026-10-23",1,2).equals(CoursePolicy.key("课程id","2026-10-23",1,2)),"id stable");
  check(!CoursePolicy.key("课程id","2026-10-23",1,2).equals(CoursePolicy.key("课程id","2026-10-24",1,2)),"occurrences independent");
  check(CoursePolicy.repeatDates("2026-10-23","weekly",1,"count",2,"").equals(Arrays.asList("2026-10-26","2026-11-02")),"repeat starts next selected weekday");
  check(CoursePolicy.repeatDates("2026-10-23","biweekly",5,"until",0,"2026-11-20").equals(Arrays.asList("2026-10-23","2026-11-06","2026-11-20")),"repeat until inclusive");
  check(CoursePolicy.repeatDates("2026-12-25","weekly",5,"count",2,"").get(1).equals("2027-01-01"),"manual repeat crosses year");
  try{CoursePolicy.repeatDates("2026-10-23","weekly",5,"count",0,"");throw new AssertionError("zero repeat accepted");}catch(IllegalArgumentException e){count++;}
  try{CoursePolicy.repeatDates("2026-10-23","weekly",5,"until",0,"2026-10-22");throw new AssertionError("past until accepted");}catch(IllegalArgumentException e){count++;}
  try{CoursePolicy.repeatDates("2026-10-23","weekly",5,"until",0,"2030-10-23");throw new AssertionError("unbounded repeat accepted");}catch(IllegalArgumentException e){count++;}
  long boundary=java.time.LocalDateTime.parse("2099-10-23T09:40:00").atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
  check(!CoursePolicy.ended("2099-10-23","09:40",boundary-1),"class not completed before end");check(CoursePolicy.ended("2099-10-23","09:40",boundary),"class complete at end boundary");
  System.out.println(count+" course week, time and occurrence checks passed");
 }
}
