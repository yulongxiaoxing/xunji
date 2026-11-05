package cn.xunji.probe;
final class GrowthMemoryPolicyTest {
 static int count;
 static void eq(String actual,String expected){count++;if(!actual.equals(expected))throw new AssertionError(actual+" != "+expected);}
 static void rejects(Runnable action){count++;try{action.run();throw new AssertionError("accepted invalid input");}catch(IllegalArgumentException expected){}}
 public static void main(String[] args){
  long base=java.time.ZonedDateTime.parse("2026-01-05T09:00:00+08:00").toInstant().toEpochMilli();
  eq(GrowthMemoryPolicy.grounded("我参加校园服务，获得二等奖","二等奖",200),"二等奖");
  eq(GrowthMemoryPolicy.grounded("我参加校园服务","一等奖",200),"");
  eq(GrowthMemoryPolicy.grounded("志愿服务","志愿",2),"志愿");
  eq(GrowthMemoryPolicy.grounded("志愿服务","志愿服务",2),"");
  eq(GrowthMemoryPolicy.month("去年十二月参加服务","去年十二月",base),"2025-12");
  eq(GrowthMemoryPolicy.month("上个月参加服务","上个月",base),"2025-12");
  eq(GrowthMemoryPolicy.month("2026年11月参加服务","2026年11月",base),"2026-11");
  eq(GrowthMemoryPolicy.month("2026-11参加服务","2026-11",base),"2026-11");
  eq(GrowthMemoryPolicy.month("十一月参加服务","十一月",base),"");
  eq(GrowthMemoryPolicy.month("参加过服务","2026年11月",base),"");
  eq(GrowthMemoryPolicy.month("今年十三月参加服务","今年十三月",base),"");
  eq(GrowthMemoryPolicy.month("2026年2月30日服务","2026年2月30日",base),"");
  eq(GrowthMemoryPolicy.month("这个月参加服务","这个月",base),"2026-01");
  eq(GrowthMemoryPolicy.hours("服务四小时","四小时"),"4");
  eq(GrowthMemoryPolicy.hours("服务3.5小时","3.5"),"3.5");
  eq(GrowthMemoryPolicy.hours("没有说明时长","4"),"");
  eq(GrowthMemoryPolicy.hours("服务半小时","半小时"),"0.5");
  eq(GrowthMemoryPolicy.hours("服务四小时到六小时","四小时到六小时"),"");
  eq(GrowthMemoryPolicy.hours("服务14小时","4小时"),"");
  eq(GrowthMemoryPolicy.hours("参加四季志愿服务，没有记录时长","四"),"");
  rejects(()->GrowthMemoryPolicy.validateInput(" ","志愿"));
  rejects(()->GrowthMemoryPolicy.validateInput("经历","作业"));
  rejects(()->GrowthMemoryPolicy.validateInput(new String(new char[5001]).replace('\0','字'),"志愿"));
  System.out.println(count+" growth memory grounding/month/hour/input checks passed");
 }
}
