package cn.xunji.probe;
import java.time.LocalDate;
public class DeadlinePolicyTest {
 static int count;static final LocalDate REF=LocalDate.of(2026,10,22);
 static void expect(String text,String date,String time){SchedulePolicy.Deadline r=SchedulePolicy.parse(text,REF);count++;if(!r.date.equals(date)||!r.time.equals(time))throw new AssertionError(text+": "+r.date+" "+r.time);}
 public static void main(String[] args){
  expect("2026年10月23日晚上20:00前","2026-10-23","20:00");
  expect("2026-10-25 18:00","2026-10-25","18:00");
  expect("2026/10/24 14:30","2026-10-24","14:30");
  expect("10月25日晚24点之前","2026-10-26","00:00");
  expect("2026年12月31日24:00","2027-01-01","00:00");
  expect("10月25日","2026-10-25","");
  expect("明天下午2点半","2026-10-23","14:30");
  expect("后天上午9点30分","2026-10-24","09:30");
  expect("今晚12点","2026-10-23","00:00");
  expect("今晚8点","2026-10-22","20:00");
  expect("周五前","2026-10-23","");
  expect("下周一18点前","2026-10-26","18:00");
  expect("本周三","2026-10-21","");
  expect("2026年2月29日","","");
  expect("2026年10月23日25:00","2026-10-23","");
  expect("大后天18点","2026-10-25","18:00");
  expect("上周五","2026-10-16","");
  expect("本周五和下周一","","");
  expect("2026年10月23日及下周一","","");
  expect("明天（10月23日）晚上8点","2026-10-23","20:00");
  expect("明天或后天","","");
  expect("2026年10月23日下午2点到3点","2026-10-23","");
  expect("另行通知","","");expect("","","");expect("20:00前","","");
  expect("10月23日至10月25日","","");
  SchedulePolicy.Deadline next=SchedulePolicy.parse("明天18点",LocalDate.of(2026,12,31));count++;if(!next.date.equals("2027-01-01"))throw new AssertionError("relative year rollover");
  System.out.println(count+" automatic deadline interpretation checks passed");
 }
}
