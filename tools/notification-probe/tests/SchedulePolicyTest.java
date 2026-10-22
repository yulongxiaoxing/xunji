package cn.xunji.probe;
public class SchedulePolicyTest {
 static int count;static void check(boolean v,String msg){count++;if(!v)throw new AssertionError(msg);}
 static void invalid(String date,String time){try{SchedulePolicy.validate(date,time);throw new AssertionError("invalid accepted: "+date+" "+time);}catch(IllegalArgumentException expected){count++;}}
 public static void main(String[] args){
  SchedulePolicy.validate("2026-10-22","23:59");count++;
  SchedulePolicy.validate("2028-02-29","");count++;
  SchedulePolicy.validate("","");count++;
  invalid("2026-02-29","");invalid("2026-13-01","");invalid("2026-10-32","");invalid("2026-1-2","");invalid("0000-01-01","");invalid("2026-10-22","24:00");invalid("2026-10-22","9:30");invalid("","12:00");
  check(SchedulePolicy.included("todo"),"todo in schedule");check(SchedulePolicy.included("done"),"done optional in schedule");check(!SchedulePolicy.included("ignored"),"ignored excluded");check(!SchedulePolicy.included("deleted"),"deleted excluded");check(!SchedulePolicy.included("inbox"),"unconfirmed inbox excluded");
  System.out.println(count+" explicit schedule date/time checks passed");
 }
}
