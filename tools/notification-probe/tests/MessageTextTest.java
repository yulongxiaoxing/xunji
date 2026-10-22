package cn.xunji.probe;
public class MessageTextTest {
    public static void main(String[] args) {
        check(MessageText.expanded("思考题（一）01 家乡在哪里？", "思考题（一）01 家乡在哪里？02 城市特点。说明：10月25日晚24点之前提交").contains("10月25日"));
        check(MessageText.expanded("短消息", "另一条完全不同的长消息").equals("短消息"));
        check(MessageText.expanded("通知…", "通知完整正文").equals("通知完整正文"));
        check(MessageText.expanded("通知完整正文", "通知").equals("通知完整正文"));
        StringBuilder longText=new StringBuilder("通知");for(int i=0;i<10000;i++)longText.append("字");
        check(MessageText.expanded("通知",longText.toString()).length()==10002);
        check(MessageText.expanded("", "完整原文").equals("完整原文"));
        check(MessageText.expanded("正文", null).equals("正文"));
        System.out.println("7 full-text and same-message expansion checks passed");
    }
    static void check(boolean good) {if(!good)throw new AssertionError("message text regression");}
}
