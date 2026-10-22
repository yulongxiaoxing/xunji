package cn.xunji.probe;
import java.util.*;
import java.util.regex.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Source-grounded safeguards; no task answers or inferred requirements. */
final class ExtractionPolicy {
    static final int VERSION=5;
    static boolean validCardCount(int count){return count>=0&&count<=12;}
    // Only the exact marker appended during the known end-to-end diagnostic.
    static String taskText(String text){return text.replaceAll("(?:寻迹|循迹)完整性测试1021(?![0-9])", "").trim();}
    private static final Pattern NUMBERED=Pattern.compile("(?m)^[\\t ]*(?:[0-9]{1,2}[、.．)）:：]|[0-9]{1,2}[\\t ]+)");
    static List<String> requirements(String source,String category,List<String> model) {
        source=taskText(source);
        List<String> cleaned=new ArrayList<>();
        for(String value:model){String text=taskText(value);if(!text.isEmpty())cleaned.add(text);}
        model=cleaned;
        if(!"作业".equals(category) || !(source.contains("思考题")||source.contains("回答下列问题")||source.contains("完成下列题目")))return new ArrayList<>(model);
        List<Integer> starts=new ArrayList<>();Matcher matcher=NUMBERED.matcher(source);
        while(matcher.find())starts.add(matcher.start());
        if(starts.isEmpty())return new ArrayList<>(model);
        List<String> questions=new ArrayList<>(),footers=new ArrayList<>();
        for(int i=0;i<starts.size();i++){
            int start=starts.get(i),end=i+1<starts.size()?starts.get(i+1):source.length();
            // Split a separate final note paragraph, but never throw its content away.
            // Inline or question-local explanations remain part of that question.
            if(i==starts.size()-1){
                Matcher footer=Pattern.compile("\\n[\\t ]*\\n[\\t ]*说明[：:]").matcher(source);
                footer.region(start,end);
                if(footer.find()){footers.add(source.substring(footer.start(),end).trim());end=footer.start();}
            }
            if(end>start){String question=source.substring(start,end).trim();if(!question.isEmpty())questions.add(question);}
        }
        List<String> intro=new ArrayList<>(),other=new ArrayList<>();
        for(String raw:model){String requirement=raw.trim();if(requirement.isEmpty())continue;
            String normalized=compact(requirement);boolean partOfQuestion=false;
            for(String question:questions)if(compact(question).contains(normalized)){partOfQuestion=true;break;}
            for(String footer:footers)if(compact(footer).contains(normalized)){partOfQuestion=true;break;}
            if(partOfQuestion)continue;
            if(requirement.contains("回答下列问题"))intro.add(requirement);else other.add(requirement);
        }
        LinkedHashSet<String> result=new LinkedHashSet<>();result.addAll(intro);result.addAll(questions);result.addAll(other);result.addAll(footers);
        return new ArrayList<>(result);
    }
    private static String compact(String text){return text.replaceAll("\\s+","");}
    static int previousCardIndex(List<String> previous,List<String> incoming,int index){
        if(previous.size()==1&&incoming.size()==1)return 0;
        String title=incoming.get(index);
        if(Collections.frequency(previous,title)!=1||Collections.frequency(incoming,title)!=1)return -1;
        return previous.indexOf(title);
    }
    static String contentKey(String source){
        try{
            String text=VERSION+"|"+source.replace("\r\n","\n").replace('\r','\n').trim();
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();
            for(byte b:hash)out.append(String.format("%02x",b&255));return out.toString();
        }catch(Exception error){throw new IllegalStateException(error);}
    }
}
