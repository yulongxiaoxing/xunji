package cn.xunji.probe;

import java.time.LocalDate;
import java.util.regex.*;

/** Canonical clock text for parsing only; original user wording stays unchanged. */
final class TimeText {
    private static final String DIGITS="零〇一二两三四五六七八九十";
    private static int number(String text){
        if(text.equals("十"))return 10;int ten=text.indexOf('十');
        if(ten>=0){if(text.indexOf('十',ten+1)>=0||ten>1||text.length()-ten>2)return -1;int high=ten==0?1:digit(text.charAt(0)),low=ten==text.length()-1?0:digit(text.charAt(ten+1));return high>0&&low>=0?high*10+low:-1;}
        if(text.length()==1)return digit(text.charAt(0));
        if(text.length()==2&&(text.charAt(0)=='零'||text.charAt(0)=='〇'))return digit(text.charAt(1));return -1;
    }
    private static int digit(char c){return c=='两'?2:c=='〇'?0:"零一二三四五六七八九".indexOf(c);}
    static String normalize(String text){
        if(text==null)return "";String value=text.replace('：',':');
        Matcher hour=Pattern.compile("(["+DIGITS+"]{1,3})(?=[点时])").matcher(value);StringBuffer out=new StringBuffer();
        while(hour.find()){int n=number(hour.group(1));hour.appendReplacement(out,n>=0?String.valueOf(n):Matcher.quoteReplacement(hour.group()));}hour.appendTail(out);
        Matcher minute=Pattern.compile("([点时])(["+DIGITS+"]{1,3})(分?)").matcher(out);StringBuffer result=new StringBuffer();
        while(minute.find()){int n=number(minute.group(2));minute.appendReplacement(result,n>=0?minute.group(1)+n+minute.group(3):Matcher.quoteReplacement(minute.group()));}minute.appendTail(result);return result.toString();
    }
    static String clock(String text){
        String value=normalize(text).trim();Matcher m=Pattern.compile("([0-9]{1,2}):([0-9]{2})").matcher(value);
        if(m.matches()){int h=Integer.parseInt(m.group(1)),n=Integer.parseInt(m.group(2));return h<24&&n<60?String.format(java.util.Locale.ROOT,"%02d:%02d",h,n):"";}
        if(!value.matches("(?:上午|下午|晚上|中午|早上|凌晨|晚)?\\s*[0-9]{1,2}[点时](?:[0-9]{1,2}分?|半)?"))return "";
        return SchedulePolicy.parse("2000-01-01 "+value.replace("中午","下午"),LocalDate.of(2000,1,1)).time;
    }
}
