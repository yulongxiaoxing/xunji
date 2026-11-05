package cn.xunji.probe;

public class GrowthPolicyTest {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static void rejects(Runnable action){try{action.run();throw new AssertionError("invalid growth accepted");}catch(IllegalArgumentException expected){checks++;}}
    public static void main(String[] args){
        GrowthPolicy.validate("数学竞赛","竞赛","2026-10-28","");checks++;
        GrowthPolicy.validate("待补充经历","其他成果","","");checks++;
        GrowthPolicy.validate("数学竞赛","竞赛","2026-10","");checks++;
        rejects(()->GrowthPolicy.validate("x","竞赛","2026-13",""));
        rejects(()->GrowthPolicy.validate("x","竞赛","0000-10",""));
        java.util.Map<String,String> details=new java.util.HashMap<>();details.put("award","省二等奖");
        GrowthPolicy.validateDetails("竞赛",details);checks++;
        rejects(()->GrowthPolicy.validateDetails("志愿",details));
        details.clear();details.put("hours","12.50");GrowthPolicy.validateDetails("志愿",details);checks++;
        details.put("hours","-1");rejects(()->GrowthPolicy.validateDetails("志愿",details));
        details.put("hours","1e9");rejects(()->GrowthPolicy.validateDetails("志愿",details));
        check(GrowthPolicy.imageId("12345678-1234-1234-1234-123456789abc"),"image UUID valid");
        check(!GrowthPolicy.imageId("../private"),"image traversal rejected");
        rejects(()->GrowthPolicy.validate(" ","竞赛","",""));
        rejects(()->GrowthPolicy.validate("x","作业","",""));
        rejects(()->GrowthPolicy.validate("x","竞赛","2026-02-29",""));
        rejects(()->GrowthPolicy.validate("x","竞赛","0000-01-01",""));
        rejects(()->GrowthPolicy.validate(new String(new char[201]).replace('\0','长'),"竞赛","",""));
        rejects(()->GrowthPolicy.validate("x","竞赛","",new String(new char[5001]).replace('\0','长')));
        check(GrowthPolicy.registration("数学竞赛报名"),"registration title");
        check(GrowthPolicy.registration("报名成功确认"),"registration success is not participation");
        check(!GrowthPolicy.registration("数学竞赛实际参与"),"participation title");
        GrowthPolicy.requireSource("done","竞赛","数学竞赛实际参与",true);checks++;
        rejects(()->GrowthPolicy.requireSource("todo","竞赛","数学竞赛",true));
        rejects(()->GrowthPolicy.requireSource("deleted","活动","志愿服务",true));
        rejects(()->GrowthPolicy.requireSource("done","竞赛","数学竞赛报名",true));
        rejects(()->GrowthPolicy.requireSource("done","课程","图像处理基础",true));
        rejects(()->GrowthPolicy.requireSource("done","活动","培训会",false));
        System.out.println(checks+" growth validation and participation checks passed");
    }
}
