package cn.xunji.probe;
import java.util.Set;
final class NapCatPolicy {
    static boolean allowed(Set<String> followed,String type,String group,String id,long time,long since,Set<String> baseline) {
        return "group".equals(type) && group.matches("[1-9][0-9]{0,19}") && followed.contains(group) && !id.isEmpty() && time>=since && !baseline.contains(id);
    }
}
