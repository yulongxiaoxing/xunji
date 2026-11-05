package cn.xunji.probe;

import java.time.*;
import java.util.regex.*;

/** Source-grounded, preview-only rules for dictated experiences. */
final class GrowthMemoryPolicy {
 static void validateInput(String text,String category){
  if(text==null||text.trim().isEmpty()||text.length()>5000)throw new IllegalArgumentException("请填写1至5000字的经历描述");
  if(!GrowthPolicy.CATEGORIES.contains(category))throw new IllegalArgumentException("请先选择成长分类");
 }
 static String grounded(String source,String value,int limit){
  if(value==null)return "";value=value.trim();
  return !value.isEmpty()&&value.length()<=limit&&source.contains(value)?value:"";
 }
 private static int number(String value){
  if(value.matches("[0-9]{1,6}"))return Integer.parseInt(value);
  if(value.equals("两"))return 2;
  String digits="零一二三四五六七八九";
  if(value.length()==1&&digits.indexOf(value)>=0)return digits.indexOf(value);
  if(value.equals("十"))return 10;
  if(value.matches("[一二三四五六七八九]?十[一二三四五六七八九]?")){
   int i=value.indexOf('十');return (i==0?1:digits.indexOf(value.charAt(0)))*10+(i==value.length()-1?0:digits.indexOf(value.charAt(i+1)));
  }
  throw new IllegalArgumentException();
 }
 static String month(String source,String evidence,long received){
  String raw=grounded(source,evidence,80);if(raw.isEmpty())return "";
  LocalDate base=Instant.ofEpochMilli(received).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
  try{
   if(raw.equals("上个月")||raw.equals("上月"))return YearMonth.from(base.minusMonths(1)).toString();
   if(raw.equals("这个月")||raw.equals("本月"))return YearMonth.from(base).toString();
   if(raw.matches("[0-9]{4}-[0-9]{2}(-[0-9]{2})?")){
    if(raw.startsWith("0000"))return "";
    return raw.length()==10?YearMonth.from(LocalDate.parse(raw)).toString():YearMonth.parse(raw).toString();
   }
   Matcher m=Pattern.compile("([0-9]{4}年|今年|去年|前年)([0-9一二三四五六七八九十两零]{1,3})月(?:([0-9一二三四五六七八九十两零]{1,3})[日号])?").matcher(raw);
   if(!m.matches())return "";
   String year=m.group(1);int y=year.equals("今年")?base.getYear():year.equals("去年")?base.getYear()-1:year.equals("前年")?base.getYear()-2:Integer.parseInt(year.substring(0,4));
   if(y<1||y>9999)return "";int month=number(m.group(2));
   if(m.group(3)!=null)LocalDate.of(y,month,number(m.group(3)));
   return YearMonth.of(y,month).toString();
  }catch(Exception invalid){return "";}
 }
 static String hours(String source,String evidence){
  String raw=grounded(source,evidence,200);if(raw.isEmpty())return "";
  try{
   String boundary="[0-9.零一二三四五六七八九十百千万两半]";
   String expression=raw.matches(".*(?:个?小时|[hH])$")?Pattern.quote(raw):Pattern.quote(raw)+"\\s*(?:个?小时|[hH])";
   if(!Pattern.compile("(?<!"+boundary+")"+expression+"(?![a-zA-Z])").matcher(source).find())return "";
   String value=raw.replaceFirst("(?:个?小时|[hH])$","").trim();
   if(value.equals("半"))value="0.5";
   else if(!value.matches("[0-9]{1,6}(\\.[0-9]{1,2})?"))value=String.valueOf(number(value));
   java.math.BigDecimal n=new java.math.BigDecimal(value);
   if(n.signum()<0||n.compareTo(new java.math.BigDecimal("100000"))>0)return "";
   return n.stripTrailingZeros().toPlainString();
  }catch(Exception invalid){return "";}
 }
}
