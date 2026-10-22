package cn.xunji.probe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.app.NotificationManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ProbeStore store;
    private LinearLayout content;
    private TextView state;
    private TextView selectionState;
    private TextView filterState;
    private final Runnable poll = new Runnable() {
        @Override public void run() { updateState(); handler.postDelayed(this, 1000); }
    };
    private static final int INK = Color.rgb(31, 47, 39);
    private static final int GREEN = Color.rgb(40, 99, 77);

    @Override public void onCreate(Bundle saved) { super.onCreate(saved); store = new ProbeStore(this); render(); }
    @Override protected void onResume() {
        super.onResume();
        new SelectionStore(this).stop();
        GroupSelectionService.dismissReturnButton();
        render();
        handler.removeCallbacks(poll);
        handler.post(poll);
    }
    @Override protected void onPause() { handler.removeCallbacks(poll); super.onPause(); }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density); }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(247, 249, 246));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(24), dp(22), dp(36));
        scroll.addView(content);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(scroll);
        text("寻迹", 32, true);
        text("QQ 选群与通知验证 · 本地测试版", 14, false);
        text("先验证消息能否被看见", 20, true);
        text("这不是完整 App。本版仅验证 QQ 通知、候选群名与白名单。没有联网权限，不调用 AI；最多保留20条测试记录。", 14, false);
        state = text("", 14, true);
        selectionState = null;
        filterState = null;
        updateState();
        selectionTools();
        button("1  打开通知使用权设置", () -> {
            try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
            catch (Exception error) { message("无法打开设置", "请在系统设置中搜索“通知使用权”，开启寻迹 QQ 通知测试。"); }
        });
        button("2  开始120秒 QQ 测试采样", () -> new AlertDialog.Builder(this)
            .setTitle("只采样你主动制造的测试通知")
            .setMessage("120秒内可能收到的 QQ 通知会在本机短暂保留，包含尚未选择的群或私聊。请先避免其他会话产生消息，再让目标群发一条不含个人信息的测试消息。结束后可一键清空。")
            .setNegativeButton("取消", null)
            .setPositiveButton("开始采样", (d, w) -> { store.stopFilterTest(); store.sample(SystemClock.elapsedRealtime() + 120_000); render(); }).show());
        button("停止采样并刷新记录", () -> { store.sample(0); render(); });
        button("刷新测试结果", this::render);
        text("已选择的来源", 18, true);
        text(store.chosen().isEmpty() ? "还未选择。采样后请核对通知标题确实是目标群。" : store.chosenLabel() + "\n仅做标题精确匹配，群身份仍需实际测试。", 14, false);
        if (!store.chosen().isEmpty()) button("移除所选来源", () -> { store.choose("", ""); render(); });
        text("最近测试记录", 18, true);
        JSONArray rows = store.rows();
        if (rows.length() == 0) text("暂无记录。确认关注群后，将QQ切到后台，让另一位群成员发送测试消息；确认通知栏出现QQ通知，再回来刷新。验证关注群过滤时不要开启广泛采样。", 14, false);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(14), dp(12), dp(14), dp(12));
            GradientDrawable background = new GradientDrawable();
            background.setColor(Color.WHITE); background.setCornerRadius(dp(14));
            box.setBackground(background);
            LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
            layout.setMargins(0, dp(8), 0, dp(8));
            content.addView(box, layout);
            label(box, row.optString("mode") + " · " + new SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(new Date(row.optLong("time"))), 12, false);
            label(box, "通知标题：" + row.optString("title", ""), 15, true);
            label(box, "会话标题：" + (row.optString("conversation").isEmpty() ? "未提供" : row.optString("conversation")), 13, false);
            label(box, row.optString("text"), 15, false);
            if (!row.optString("identity").isEmpty() && !"manual".equals(row.optString("mode"))) {
                Button choose = new Button(this);
                choose.setText("核对后选择这个来源");
                choose.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("请核对这是目标群")
                    .setMessage("候选名称：" + row.optString("label") + "\n系统标题可能是发送人或汇总文字，本版不保证它是稳定群标识。选择后停止广泛采样，只保留与此标题精确匹配的 QQ 通知。请再用同群不同成员及另一个群验证。")
                    .setNegativeButton("取消", null).setPositiveButton("确认是目标群", (d, w) -> {
                        store.choose(row.optString("identity"), row.optString("label")); store.sample(0); render();
                    }).show());
                box.addView(choose);
            }
        }
        button("手动粘贴测试文字", () -> {
            EditText input = new EditText(this); input.setHint("请粘贴不含个人信息的通知文字"); input.setMinLines(3);
            new AlertDialog.Builder(this).setTitle("手动入口验证").setView(input).setNegativeButton("取消", null)
                .setPositiveButton("保存到本地", (d, w) -> {
                    String value = input.getText().toString().trim(); if (value.isEmpty()) return;
                    try { JSONObject row = new JSONObject(); row.put("key", "manual-" + System.currentTimeMillis()); row.put("text", value.substring(0, Math.min(2000, value.length()))); row.put("time", System.currentTimeMillis()); row.put("mode", "manual"); row.put("title", "手动粘贴"); store.append(row); render(); }
                    catch (Exception ignored) {}
                }).show();
        });
        button("清空全部测试记录与来源", () -> new AlertDialog.Builder(this).setTitle("清空测试数据？")
            .setMessage("将停止采样，删除本机测试记录并移除所选来源。")
            .setNegativeButton("取消", null).setPositiveButton("清空", (d, w) -> { store.clear(); new SelectionStore(this).clear(); render(); }).show());
    }

    private void updateState() {
        if (state == null) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        boolean enabled = nm.isNotificationListenerAccessGranted(new ComponentName(this, ProbeService.class));
        long seconds = Math.max(0, (store.until() - SystemClock.elapsedRealtime() + 999) / 1000);
        state.setText("通知权限：" + (enabled ? "已开启" : "未开启") + "\n监听连接：" + (ProbeService.connected ? "已连接" : "未连接")
            + "\n" + (CapturePolicy.sampling(store.until(), SystemClock.elapsedRealtime()) ? "广泛测试采样剩余 " + seconds + " 秒" : "按已关注群过滤 · 已关注 " + new SelectionStore(this).followed().size() + " 个")
            + "\n已保存 " + store.rows().length() + " 条 · 点刷新查看");
        if (selectionState != null) selectionState.setText("辅助服务：" + (GroupSelectionService.connected ? "已连接" : "未连接")
            + "\n" + new SelectionStore(this).status());
        if (filterState != null) filterState.setText(store.filterSummary());
    }
    private void selectionTools() {
        SelectionStore selection = new SelectionStore(this);
        text("在 QQ 群列表上勾选", 20, true);
        text("打开 QQ → 联系人 → 群聊，点击群名右侧由寻迹提供的绿色复选框；滚动可继续选择。完成后点“完成选群，返回寻迹”，核对后才关注。同名群暂不区分。测试读取120秒，只读群名。", 14, false);
        selectionState = text("", 14, true);
        updateState();
        button("打开无障碍设置", () -> {
            try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
            catch (Exception e) { message("请手动打开设置", "在系统无障碍设置中找到“寻迹选群辅助测试”并开启。"); }
        });
        button("打开QQ，开始群列表勾选", () -> {
            if (!GroupSelectionService.connected) { message("辅助服务尚未连接", "请先在无障碍设置中开启“寻迹选群辅助测试”，再回来开始。"); return; }
            Intent qq = getPackageManager().getLaunchIntentForPackage(CapturePolicy.QQ);
            if (qq == null) { message("无法打开QQ", "请确认手机已安装QQ。"); return; }
            store.sample(0);
            selection.start();
            try { startActivity(qq); }
            catch (Exception e) { selection.stop(); message("无法打开QQ", "选群测试已停止，请重试。"); }
        });
        button("停止读取并刷新选群结果", () -> { selection.stop(); render(); });
        JSONArray names = selection.candidates();
        if (names.length() == 0) text("尚未选群。进入QQ普通群列表后，使用群名右侧的寻迹绿色复选框。若没有出现复选框，请查看上面的识别状态。", 14, false);
        for (int i = 0; i < names.length(); i++) {
            String name = names.optString(i);
            if (name.isEmpty()) continue;
            text("待核对：" + name, 15, true);
            if (selection.followed().contains(name)) text("已关注", 13, false);
            else button("确认“" + name + "”是我要关注的群", () -> { selection.follow(name); render(); });
        }
        text("已确认关注的群", 18, true);
        if (selection.followed().isEmpty()) text("暂无。候选名称核对后才会用于后续 QQ 通知的群名精确匹配。", 14, false);
        for (String name : new java.util.TreeSet<>(selection.followed())) {
            text(name, 15, true);
            button("取消关注“" + name + "”", () -> { selection.unfollow(name); render(); });
        }
        button("清除选群候选与辅助测试数据", () -> { selection.clear(); render(); });
        text("通知读取测试", 20, true);
        filterState = text(store.filterSummary(), 14, true);
        button("开始仅关注群过滤验证", () -> {
            int count = selection.followed().size();
            if (count == 0) { message("请先确认关注群", "勾选并返回后，还需点击候选群名旁的确认关注按钮。"); return; }
            store.startFilterTest(count); render();
            message("可以开始发测试通知", "已关闭广泛采样和旧的单来源匹配。本轮只按已确认关注群过滤。请将QQ切到后台，分别由已关注群、未关注群和私聊发测试文字。回来点“刷新测试结果”，应只新增已关注群的测试文字。原有测试记录保留，按新测试文字与时间区分。");
        });
        if (store.filterTestActive()) button("结束本轮过滤验证", () -> { store.stopFilterTest(); render(); });
    }
    private TextView text(String value, int size, boolean bold) { return label(content, value, size, bold); }
    private TextView label(LinearLayout parent, String value, int size, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(INK);
        view.setPadding(0, dp(8), 0, dp(8)); view.setTextIsSelectable(true);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        parent.addView(view); return view;
    }
    private void button(String title, Runnable action) {
        Button view = new Button(this); view.setText(title); view.setTextColor(GREEN); view.setAllCaps(false);
        view.setOnClickListener(v -> action.run()); content.addView(view);
    }
    private void message(String title, String body) { new AlertDialog.Builder(this).setTitle(title).setMessage(body).setPositiveButton("知道了", null).show(); }
}
