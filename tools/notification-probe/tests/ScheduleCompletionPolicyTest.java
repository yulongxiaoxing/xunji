package cn.xunji.probe;
public class ScheduleCompletionPolicyTest {
 static int checks;static void check(boolean x,String m){checks++;if(!x)throw new AssertionError(m);}
 public static void main(String[] args){
  long end=CoursePolicy.endMillis("2026-11-04","11:00");
  for(String type:new String[]{"课程","活动","考试","竞赛","作业","其他"})check(ScheduleCompletionPolicy.endMillis(type,"2026-11-04","10:00","11:00")==end,"eligible interval "+type);
  check(ScheduleCompletionPolicy.deadlineMillis("作业","2026-11-04","11:00")==end,"homework deadline auto completes");
  check(ScheduleCompletionPolicy.deadlineMillis("报名","2026-11-04","")==CoursePolicy.endMillis("2026-11-05","00:00"),"date-only deadline waits full day");
  check(ScheduleCompletionPolicy.deadlineMillis("课程","2026-11-04","11:00")==Long.MAX_VALUE,"course needs complete interval");
  check(ScheduleCompletionPolicy.endMillis("活动","","10:00","11:00")==Long.MAX_VALUE,"unknown date");
  check(ScheduleCompletionPolicy.endMillis("活动","2026-11-04","","11:00")==Long.MAX_VALUE,"unknown start");
  check(ScheduleCompletionPolicy.endMillis("考试","2026-11-04","11:00","10:00")==Long.MAX_VALUE,"invalid reversed range");
  check(ScheduleCompletionPolicy.endMillis("活动","2026-02-30","10:00","11:00")==Long.MAX_VALUE,"invalid calendar date");
  check(ScheduleCompletionPolicy.endMillis("活动","2026-11-04","25:00","26:00")==Long.MAX_VALUE,"invalid clock");
  System.out.println(checks+" schedule completion category/interval/date boundary checks passed");
 }
}
