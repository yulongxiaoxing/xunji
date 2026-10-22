package cn.xunji.probe;

/** Pure Java policy, shared by the listener and its host-side checks. */
public final class CapturePolicy {
    public static final String QQ = "com.tencent.mobileqq";
    public static boolean matchesFollowed(java.util.Set<String> followed, String title, String conversation, Boolean group) {
        if (followed == null || followed.isEmpty() || Boolean.FALSE.equals(group)) return false;
        String source = conversation != null && !conversation.trim().isEmpty() ? conversation.trim() : title == null ? "" : title.trim();
        return !source.isEmpty() && followed.contains(source);
    }

    public static boolean eligible(String pkg, boolean summary, String text) {
        return QQ.equals(pkg) && !summary && text != null && !text.trim().isEmpty();
    }

    public static boolean allowed(String chosen, String identity) {
        return chosen != null && !chosen.isEmpty() && chosen.equals(identity);
    }

    public static boolean sampling(long untilElapsed, long nowElapsed) {
        return untilElapsed > nowElapsed && untilElapsed - nowElapsed <= 120_000L;
    }

    private CapturePolicy() {}
}
