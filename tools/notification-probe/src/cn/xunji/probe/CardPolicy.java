package cn.xunji.probe;
import java.util.*;

/** User decisions survive extraction; originals stay immutable. */
final class CardPolicy {
    private static final List<String> STATES=Arrays.asList("inbox","todo","done","ignored");
    static String transition(String from,String to){
        if(!STATES.contains(from)||(!STATES.contains(to)&&!"deleted".equals(to)))
            throw new IllegalArgumentException("事项状态已改变，请刷新后重试");
        return to;
    }
    static String restoreState(String previous){return STATES.contains(previous)?previous:"inbox";}
    static void validate(String title,String category,List<String> requirements,String deadline){
        if(title.trim().isEmpty()||title.length()>300)throw new IllegalArgumentException("标题请填写1至300字");
        if(!Arrays.asList("作业","考试","竞赛","活动","其他").contains(category))throw new IllegalArgumentException("请选择有效分类");
        if(deadline.length()>500)throw new IllegalArgumentException("时间信息请控制在500字内");
        int length=0;for(String line:requirements)length+=line.length();
        if(length>50000)throw new IllegalArgumentException("具体要求请控制在50000字内");
    }
    static boolean preserveSource(List<String> previous,List<String> incoming,List<Boolean> protectedCards){
        for(int i=0;i<previous.size();i++)if(protectedCards.get(i)){
            boolean matched=false;
            for(int j=0;j<incoming.size();j++)if(ExtractionPolicy.previousCardIndex(previous,incoming,j)==i){matched=true;break;}
            if(!matched)return true;
        }
        return false;
    }
}
