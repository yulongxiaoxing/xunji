package cn.xunji.probe;

public final class SelectionPolicy {
    public static String groupListLabel(String containerId, String nodeId, String name) {
        if (!"com.tencent.mobileqq:id/qb_troop_list_view".equals(containerId)
            || !"com.tencent.mobileqq:id/text1".equals(nodeId) || name == null) return "";
        String s = name.trim();
        if (s.isEmpty() || s.length() > 128 || s.contains("\n") || s.endsWith("…") || s.endsWith("...")) return "";
        return s;
    }
    public static boolean active(String pkg, long until, long now, int savedBoot, int boot) {
        return CapturePolicy.QQ.equals(pkg) && boot >= 0 && boot == savedBoot && CapturePolicy.sampling(until, now);
    }
    public static boolean selectionHeading(String label) {
        if (label == null) return false;
        String s = label.trim();
        return s.equals("选择联系人") || s.equals("选择群聊") || s.equals("选择群")
            || s.equals("选择好友") || s.equals("选择对象") || s.equals("选择聊天")
            || s.equals("选择最近聊天") || s.equals("发送到") || s.equals("发送给");
    }
    public static String cleanLabel(String label) {
        if (label == null) return "";
        String s = label.trim();
        for (String suffix : new String[]{"，未选中", "，已选中", ",未选中", ",已选中", " 未选中", " 已选中"}) {
            if (s.endsWith(suffix)) s = s.substring(0, s.length() - suffix.length()).trim();
        }
        if (s.isEmpty() || s.length() > 80 || s.contains("\n") || s.matches("[0-9]+")) return "";
        if (selectionHeading(s) || s.equals("复选框") || s.equals("CheckBox") || s.equals("checkbox") || s.equals("未选中") || s.equals("已选中") || s.equals("全选")
            || s.equals("取消") || s.equals("确定") || s.equals("发送") || s.equals("多选")) return "";
        return s;
    }
    private SelectionPolicy() {}
}
