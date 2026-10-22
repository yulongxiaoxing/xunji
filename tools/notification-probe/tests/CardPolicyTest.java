package cn.xunji.probe;
import java.util.*;
public class CardPolicyTest {
 static int count;
 static void check(boolean v,String message){count++;if(!v)throw new AssertionError(message);}
 public static void main(String[] args){
  check(CardPolicy.transition("inbox","ignored").equals("ignored"),"decline goes to ignored");
  check(CardPolicy.transition("ignored","inbox").equals("inbox"),"restore ignored to inbox");
  check(CardPolicy.transition("todo","deleted").equals("deleted"),"delete todo");
  check(CardPolicy.restoreState("done").equals("done"),"restore completed task");
  check(CardPolicy.restoreState("deleted").equals("inbox"),"invalid restore state falls back");
  try{CardPolicy.transition("deleted","todo");throw new AssertionError("deleted cannot bypass restore");}catch(IllegalArgumentException expected){count++;}
  try{CardPolicy.validate(" ","作业",Arrays.asList("a"),"");throw new AssertionError("blank title accepted");}catch(IllegalArgumentException expected){count++;}
  try{CardPolicy.validate("task","bad",Arrays.asList("a"),"");throw new AssertionError("unknown category accepted");}catch(IllegalArgumentException expected){count++;}
  CardPolicy.validate("task","其他",Arrays.asList("line one","line two"),"");count++;
  check(CardPolicy.preserveSource(Arrays.asList("old"),Arrays.asList("new","extra"),Arrays.asList(true)),"ambiguous protected split freezes source");
  check(!CardPolicy.preserveSource(Arrays.asList("old"),Arrays.asList("new"),Arrays.asList(true)),"one to one protected card can match");
  check(!CardPolicy.preserveSource(Arrays.asList("one","two"),Arrays.asList("two","one"),Arrays.asList(true,false)),"unique original titles match across reorder");
  check(CardPolicy.preserveSource(Arrays.asList("one","one"),Arrays.asList("one","one"),Arrays.asList(true,false)),"duplicate titles protected");
  check(CardPolicy.preserveSource(Arrays.asList("old"),Collections.emptyList(),Arrays.asList(true)),"empty re-extraction retains protected records");
  check(!CardPolicy.preserveSource(Arrays.asList("old"),Collections.emptyList(),Arrays.asList(false)),"empty result may replace untouched inbox");
  System.out.println(count+" card lifecycle, validation and re-extraction protection checks passed");
 }
}
