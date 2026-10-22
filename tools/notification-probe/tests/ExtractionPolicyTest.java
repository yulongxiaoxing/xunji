package cn.xunji.probe;
import java.util.*;
public final class ExtractionPolicyTest {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        check(ExtractionPolicy.validCardCount(0),"ordinary chat can successfully produce no tasks");
        check(ExtractionPolicy.validCardCount(12)&&!ExtractionPolicy.validCardCount(13),"retain card count upper bound");
        String marked="提交PDF。 循迹完整性测试1021";
        check(ExtractionPolicy.taskText(marked).equals("提交PDF。"),"remove appended known diagnostic marker");
        check(ExtractionPolicy.taskText("寻迹完整性测试1021").isEmpty(),"marker alone has no task content");
        check(ExtractionPolicy.taskText("作业：完成完整性测试，编号1021").equals("作业：完成完整性测试，编号1021"),"preserve genuine testing assignments");
        check(marked.endsWith("循迹完整性测试1021"),"original stays unchanged");
        check(ExtractionPolicy.requirements("思考题\n01 画地图。\n\n说明：提交PDF。 循迹完整性测试1021","作业",Arrays.asList("提交PDF。 循迹完整性测试1021")).stream().noneMatch(s->s.contains("测试1021")),"source completeness safeguard cannot reintroduce diagnostic marker");
        String source="思考题（一）\n结合实际情况，回答下列问题：\n01 家乡在哪里？\n请写到地级市，并附地图。\n02 城市规划如何？\n03 人口和产业如何？\n03 数字经济如何？\n04 人文传统有哪些？\n05 城市病、乡村病的根源是什么？\n\n说明：10月25日之前提交PDF，不超过5页。";
        List<String> result=ExtractionPolicy.requirements(source,"作业",Arrays.asList("回答下列问题","请写到地级市，并附地图。","提交PDF，不超过5页"));
        check(result.stream().anyMatch(s->s.contains("01 家乡在哪里？")&&s.contains("附地图")),"complete multiline first question");
        check(result.stream().anyMatch(s->s.contains("02 城市规划如何？")),"retain second question");
        check(result.stream().anyMatch(s->s.contains("03 人口")),"retain first 03");
        check(result.stream().anyMatch(s->s.contains("03 数字")),"retain repeated 03");
        check(result.stream().anyMatch(s->s.contains("04 人文")),"retain fourth question");
        check(result.stream().anyMatch(s->s.contains("05 城市病")),"retain final question");
        check(result.stream().noneMatch(s->s.startsWith("05")&&s.contains("说明：")),"submission not swallowed into final question");
        check(result.stream().anyMatch(s->s.contains("提交PDF，不超过5页")),"keep submission conditions");
        check(result.stream().filter(s->s.contains("附地图")).count()==1,"avoid a repeated partial requirement");
        check(result.indexOf(result.stream().filter(s->s.contains("提交PDF，不超过5页")).findFirst().get())>result.indexOf(result.stream().filter(s->s.startsWith("05")).findFirst().get()),"questions before submission");
        check(ExtractionPolicy.requirements("思考题\n1. 画地图。说明：必须含图例和比例尺。\n2. 分析城市问题。","作业",Arrays.asList("1. 画地图。说明：必须含图例和比例尺。")).stream().anyMatch(s->s.contains("说明：必须含图例和比例尺")),"preserve inline question explanations");
        check(ExtractionPolicy.requirements("思考题\n1. 画地图。\n说明：必须含图例和比例尺。\n2. 分析城市问题。","作业",Collections.emptyList()).stream().anyMatch(s->s.contains("说明：必须含图例和比例尺")),"preserve multiline question explanations even if model missed them");
        check(ExtractionPolicy.requirements(source,"作业",Collections.emptyList()).stream().anyMatch(s->s.contains("说明：10月25日之前提交PDF，不超过5页")),"never discard footer when model omits it");
        check(ExtractionPolicy.requirements("1. 报名竞赛\n2. 参加活动","活动",Arrays.asList("参加活动")).equals(Arrays.asList("参加活动")),"do not combine separate events");
        check(ExtractionPolicy.requirements("明天交作业","作业",Arrays.asList("明天交作业")).size()==1,"ordinary requirement unaffected");
        check(ExtractionPolicy.contentKey("正文\r\n第二行 ").equals(ExtractionPolicy.contentKey("正文\n第二行")),"normalize only edge and line-ending differences");
        check(!ExtractionPolicy.contentKey("周一截止").equals(ExtractionPolicy.contentKey("周二截止")),"deadline update gets its own extraction");
        check(!ExtractionPolicy.contentKey("作业A").equals(ExtractionPolicy.contentKey("作业B")),"different content never shares extraction");
        check(ExtractionPolicy.previousCardIndex(Arrays.asList("报告","报告"),Arrays.asList("报告","报告"),0)==-1,"duplicate task titles never transfer state");
        check(ExtractionPolicy.previousCardIndex(Arrays.asList("报告","考试"),Arrays.asList("考试","报告"),0)==1,"unique title keeps correct state after reordering");
        check(ExtractionPolicy.previousCardIndex(Arrays.asList("报告","考试"),Arrays.asList("报告","报告"),0)==-1,"ambiguous incoming title never transfers state");
        check(ExtractionPolicy.previousCardIndex(Arrays.asList("旧标题"),Arrays.asList("新标题"),0)==0,"single task retains state when wording changes");
        List<String> numberedSubmission=ExtractionPolicy.requirements("思考题\n提交要求：1. 上传PDF到LMS；2. 文件名包含学号。\n1. 绘制地图。\n2. 分析城市问题。","作业",Arrays.asList("1. 上传PDF到LMS","2. 文件名包含学号","1. 绘制地图。","2. 分析城市问题。"));
        check(numberedSubmission.stream().anyMatch(s->s.contains("上传PDF到LMS")),"numbered submission rule is not discarded as a question");
        check(numberedSubmission.stream().anyMatch(s->s.contains("文件名包含学号")),"numbered file-naming rule retained");
        System.out.println(checks+" extraction completeness and cache-key checks passed");
    }
}
