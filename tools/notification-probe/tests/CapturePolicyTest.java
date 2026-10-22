package cn.xunji.probe;

public final class CapturePolicyTest {
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        check(CapturePolicy.eligible(CapturePolicy.QQ, false, "作业明天交"), "QQ text must be eligible");
        check(!CapturePolicy.eligible("com.tencent.mm", false, "正文"), "Other apps must be rejected");
        check(!CapturePolicy.eligible(CapturePolicy.QQ, true, "汇总"), "Group summaries must be rejected");
        check(!CapturePolicy.eligible(CapturePolicy.QQ, false, "  "), "Empty text must be rejected");
        check(!CapturePolicy.allowed("", ""), "Unknown group must not match");
        check(!CapturePolicy.allowed("title:课程群", "title:课程群2"), "Partial names must not match");
        check(!CapturePolicy.allowed("title:课程群", "conversation:课程群"), "Different source fields must not be equated");
        check(CapturePolicy.allowed("conversation:课程群", "conversation:课程群"), "Exact identity should match");
        check(CapturePolicy.sampling(121_000, 1_000), "Sampling should start for 120 seconds");
        check(!CapturePolicy.sampling(121_000, 121_000), "Sampling must expire");
        check(!CapturePolicy.sampling(121_000, 0), "Invalid future sampling window must be rejected");
        check(!CapturePolicy.sampling(0, 1_000), "Stopped sampling must remain stopped");
        check(SelectionPolicy.active(CapturePolicy.QQ, 121_000, 1_000, 5, 5), "QQ selection requires an active session");
        check(!SelectionPolicy.active("com.tencent.mm", 121_000, 1_000, 5, 5), "Other apps must never be inspected");
        check(!SelectionPolicy.active(CapturePolicy.QQ, 121_000, 1_000, 4, 5), "Reboot must invalidate the session");
        check(!SelectionPolicy.active(CapturePolicy.QQ, 121_000, 121_000, 5, 5), "Selection must expire");
        check(!SelectionPolicy.active(CapturePolicy.QQ, 121_000, 1_000, -1, -1), "Unknown boot must reject capture");
        check(SelectionPolicy.selectionHeading("选择群聊"), "Known picker heading should be accepted");
        check(!SelectionPolicy.selectionHeading("请在群里选择好友"), "Chat text must not act as a picker heading");
        check(SelectionPolicy.cleanLabel("模电课程群，已选中").equals("模电课程群"), "Checked suffix should be removed");
        check(SelectionPolicy.cleanLabel("未选中兴趣群").equals("未选中兴趣群"), "Names must not be damaged by global replacements");
        check(SelectionPolicy.cleanLabel("全选").isEmpty(), "Action labels must not become group names");
        check(SelectionPolicy.cleanLabel("1").isEmpty(), "Counts must not become group names");
        check(SelectionPolicy.cleanLabel("聊天\\n正文").isEmpty() == false, "Literal slashes should remain literal");
        check(SelectionPolicy.cleanLabel("聊天\n正文").isEmpty(), "Multiline text must not become group names");
        String list = "com.tencent.mobileqq:id/qb_troop_list_view";
        String label = "com.tencent.mobileqq:id/text1";
        check(SelectionPolicy.groupListLabel(list, label, "模电课程群").equals("模电课程群"), "Ordinary group-list labels must be available without a QQ checkbox");
        check(SelectionPolicy.groupListLabel(list, label, "禁言课程群").equals("禁言课程群"), "Group-list extraction must not require permission to send");
        check(SelectionPolicy.groupListLabel("chat-history", label, "模电课程群").isEmpty(), "Identical text in chat must not be a group row");
        check(SelectionPolicy.groupListLabel(list, "chat-message", "模电课程群").isEmpty(), "Other fields inside list must not be labels");
        check(SelectionPolicy.groupListLabel(list, label, " ").isEmpty(), "Empty group labels must be skipped");
        check(SelectionPolicy.groupListLabel(list, label, "123456").equals("123456"), "Numeric group names must stay valid");
        check(SelectionPolicy.groupListLabel(list, label, "课程群\n消息").isEmpty(), "Multiline content must not be accepted");
        check(SelectionPolicy.groupListLabel(list, label, "课程群…").isEmpty(), "Truncated names must not become exact-match identities");
        java.util.Set<String> followed = new java.util.HashSet<>(java.util.Arrays.asList("模电课程群", "禁言课程群"));
        check(CapturePolicy.matchesFollowed(followed, "成员甲", "模电课程群", true), "A followed group conversation must match independently of sender");
        check(CapturePolicy.matchesFollowed(followed, "禁言课程群", "", null), "Legacy QQ group titles must support followed muted groups");
        check(!CapturePolicy.matchesFollowed(followed, "模电课程群", "未关注群", true), "Explicit conversation must take priority over a misleading title");
        check(!CapturePolicy.matchesFollowed(followed, "未关注群", "", null), "Unfollowed notifications must not match");
        check(!CapturePolicy.matchesFollowed(followed, "模电课程群", "", false), "Explicit private chat must not match even with a group-like title");
        followed.remove("模电课程群");
        check(!CapturePolicy.matchesFollowed(followed, "模电课程群", "模电课程群", true), "Unfollowing must affect the next notification");
        check(!CapturePolicy.matchesFollowed(followed, "", "", null), "Unknown sources must not match");
        System.out.println("40 capture, selection and group-list boundary checks passed");
    }
}
