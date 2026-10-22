package cn.xunji.probe;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Throwaway QQ 9.3.70 group-list spike. Never reads chat bodies or clicks QQ. */
public class GroupSelectionService extends AccessibilityService {
    private static final String LIST_ID = "com.tencent.mobileqq:id/qb_troop_list_view";
    private static final String NAME_ID = "com.tencent.mobileqq:id/text1";
    static volatile boolean connected;
    private static GroupSelectionService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Boolean> choices = new LinkedHashMap<>();
    private final List<RowOverlay> rowBoxes = new ArrayList<>();
    private final OverlayRefreshGate refreshGate = new OverlayRefreshGate();
    private LinearLayout floating;
    private TextView floatingStatus;
    private long lastScroll = -1000;
    private long lastMove = -1000;
    private int createdWindows;
    private int movedWindows;
    private int removedWindows;
    private String lastReport = "";
    private long sessionUntil;
    private final Runnable inspect = () -> {
        refreshGate.frameStarted(SystemClock.uptimeMillis());
        inspectWindow();
    };
    private void requestRefresh() {
        long delay = refreshGate.request(SystemClock.uptimeMillis());
        if (delay >= 0) handler.postDelayed(inspect, delay);
    }
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!new SelectionStore(GroupSelectionService.this).active(CapturePolicy.QQ)) { hideAll(); choices.clear(); return; }
            requestRefresh();
            handler.postDelayed(this, 240);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        android.util.Log.i("XunjiSelection", "created instance=" + System.identityHashCode(this));
    }
    @Override protected void onServiceConnected() {
        connected = true; instance = this;
        android.util.Log.i("XunjiSelection", "connected instance=" + System.identityHashCode(this));
    }
    @Override public void onDestroy() {
        android.util.Log.i("XunjiSelection", "destroyed instance=" + System.identityHashCode(this) + " current=" + (instance == this));
        connected = false; instance = null; handler.removeCallbacksAndMessages(null); refreshGate.reset(); hideAll(); choices.clear(); super.onDestroy();
    }
    @Override public void onInterrupt() { handler.removeCallbacksAndMessages(null); refreshGate.reset(); hideAll(); }
    static void dismissReturnButton() {
        if (instance != null) { instance.handler.removeCallbacksAndMessages(null); instance.refreshGate.reset(); instance.hideAll(); }
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getPackageName() == null || !CapturePolicy.QQ.contentEquals(event.getPackageName())) return;
        SelectionStore store = new SelectionStore(this);
        if (!store.active(CapturePolicy.QQ)) { hideAll(); choices.clear(); return; }
        long until = getSharedPreferences("selection", MODE_PRIVATE).getLong("until", 0);
        if (sessionUntil != until) {
            choices.clear(); sessionUntil = until;
            createdWindows = movedWindows = removedWindows = 0; lastReport = "";
            lastScroll = lastMove = -1000;
            handler.removeCallbacks(tick); handler.post(tick);
        }
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            lastScroll = SystemClock.uptimeMillis();
            disableRowTouches();
        }
        requestRefresh();
    }

    private void inspectWindow() {
        SelectionStore store = new SelectionStore(this);
        if (!store.active(CapturePolicy.QQ)) { hideAll(); return; }
        AccessibilityNodeInfo root = qqRoot();
        if (root == null) { hideAll(); return; }
        try {
            List<AccessibilityNodeInfo> lists = root.findAccessibilityNodeInfosByViewId(LIST_ID);
            List<GroupRow> rows = new ArrayList<>();
            try {
                for (AccessibilityNodeInfo list : lists) {
                    if (!list.isVisibleToUser()) continue;
                    Rect listBounds = new Rect(); list.getBoundsInScreen(listBounds);
                    List<AccessibilityNodeInfo> labels = list.findAccessibilityNodeInfosByViewId(NAME_ID);
                    try {
                        for (AccessibilityNodeInfo label : labels) {
                            if (!label.isVisibleToUser()) continue;
                            String name = SelectionPolicy.groupListLabel(list.getViewIdResourceName(), label.getViewIdResourceName(),
                                label.getText() == null ? null : label.getText().toString());
                            Rect bounds = new Rect(); label.getBoundsInScreen(bounds);
                            // Require a fully usable checkbox position inside the visible list.
                            if (name.isEmpty() || bounds.isEmpty() || bounds.centerY() < listBounds.top + dp(24)
                                || bounds.centerY() > listBounds.bottom - dp(24)) continue;
                            rows.add(new GroupRow(name, listBounds.right - dp(54), bounds.centerY() - dp(24)));
                        }
                    } finally { for (AccessibilityNodeInfo label : labels) label.recycle(); }
                }
            } finally { for (AccessibilityNodeInfo list : lists) list.recycle(); }
            showFloating();
            if (lists.isEmpty()) {
                hideRows();
                reportIfChanged(store, "请打开 QQ → 联系人 → 群聊，进入普通群列表。当前页面不读取任何聊天内容。");
            } else {
                showRows(rows);
                reportIfChanged(store, "已识别普通群列表 · 可见群名 " + rows.size() + " 个 · 本轮勾选 " + selected().length()
                    + " 个\n点击群名右侧寻迹复选框，滚动可继续选择。禁言状态不参与筛选。");
            }
            updateStatus();
        } catch (RuntimeException error) {
            hideRows(); store.report("群列表正在变化或结构不兼容，请停在普通群列表重试。", selected());
        } finally { root.recycle(); }
    }

    private JSONArray selected() {
        JSONArray names = new JSONArray();
        for (Map.Entry<String, Boolean> entry : choices.entrySet()) if (entry.getValue()) names.put(entry.getKey());
        return names;
    }
    private void reportIfChanged(SelectionStore store, String report) {
        if (!report.equals(lastReport)) { lastReport = report; store.report(report, selected()); }
    }
    private void showRows(List<GroupRow> rows) {
        WindowManager windows = getSystemService(WindowManager.class);
        long now = SystemClock.uptimeMillis();
        boolean changed = rows.size() > rowBoxes.size();
        for (int i = 0; i < Math.min(rows.size(), rowBoxes.size()); i++) {
            GroupRow row = rows.get(i); RowOverlay slot = rowBoxes.get(i);
            if (!row.name.equals(slot.name) || row.x != slot.params.x || row.y != slot.params.y || !slot.visible) changed = true;
        }
        if (changed) lastMove = now;
        boolean touchable = OverlayRefreshGate.touchSafe(now, lastScroll, lastMove);
        while (rowBoxes.size() < rows.size()) {
            GroupRow row = rows.get(rowBoxes.size());
            RowOverlay slot = new RowOverlay(new CheckBox(this), overlayParams(dp(48), dp(48)));
            slot.params.gravity = Gravity.TOP | Gravity.LEFT;
            slot.params.x = row.x; slot.params.y = row.y;
            slot.params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            slot.box.setButtonTintList(ColorStateList.valueOf(Color.rgb(33, 117, 74)));
            GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.rgb(239, 249, 241)); bg.setCornerRadius(dp(12));
            slot.box.setBackground(bg); slot.box.setElevation(dp(3));
            windows.addView(slot.box, slot.params); rowBoxes.add(slot); createdWindows++;
        }
        for (int i = 0; i < rowBoxes.size(); i++) {
            RowOverlay slot = rowBoxes.get(i);
            if (i >= rows.size()) {
                if (slot.visible) { slot.box.setVisibility(View.GONE); slot.visible = false; }
                continue;
            }
            GroupRow row = rows.get(i);
            // A reused window must never fire a callback for the old group while rebinding.
            slot.box.setOnCheckedChangeListener(null);
            if (!row.name.equals(slot.name)) {
                slot.name = row.name;
                slot.box.setContentDescription("寻迹关注群：" + slot.name);
            }
            boolean checked = Boolean.TRUE.equals(choices.get(slot.name));
            if (slot.box.isChecked() != checked) slot.box.setChecked(checked);
            slot.box.setOnCheckedChangeListener((button, value) -> {
                if (!OverlayRefreshGate.touchSafe(SystemClock.uptimeMillis(), lastScroll, lastMove)) {
                    button.setOnCheckedChangeListener(null);
                    button.setChecked(Boolean.TRUE.equals(choices.get(slot.name)));
                    requestRefresh(); return;
                }
                if (value && !choices.containsKey(slot.name) && choices.size() >= 100) {
                    button.setOnCheckedChangeListener(null); button.setChecked(false); requestRefresh();
                    android.widget.Toast.makeText(this, "本轮最多选择100个群名", android.widget.Toast.LENGTH_SHORT).show(); return;
                }
                if (value) choices.put(slot.name, true); else choices.remove(slot.name);
                new SelectionStore(this).report("本轮已勾选 " + selected().length() + " 个，完成后返回核对。", selected());
                updateStatus();
            });
            boolean moved = slot.params.x != row.x || slot.params.y != row.y;
            int flags = touchable ? slot.params.flags & ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                : slot.params.flags | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            boolean flagsChanged = flags != slot.params.flags;
            slot.params.x = row.x; slot.params.y = row.y; slot.params.flags = flags;
            if (moved || flagsChanged) { windows.updateViewLayout(slot.box, slot.params); if (moved) movedWindows++; }
            if (!slot.visible) { slot.box.setVisibility(View.VISIBLE); slot.visible = true; }
        }
        // Unlock promptly after the final scroll frame, even if QQ emits no further events.
        if (!touchable && !rows.isEmpty()) requestRefresh();
    }
    private void disableRowTouches() {
        for (RowOverlay slot : rowBoxes) {
            if (!slot.visible || (slot.params.flags & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0) continue;
            slot.params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            try { getSystemService(WindowManager.class).updateViewLayout(slot.box, slot.params); } catch (RuntimeException ignored) {}
        }
    }
    private void hideRows() {
        for (RowOverlay slot : rowBoxes) {
            try { getSystemService(WindowManager.class).removeView(slot.box); removedWindows++; } catch (RuntimeException ignored) {}
        }
        rowBoxes.clear();
    }
    private AccessibilityNodeInfo qqRoot() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            if (root.getPackageName() != null && CapturePolicy.QQ.contentEquals(root.getPackageName())) return root;
            boolean own = root.getPackageName() != null && getPackageName().contentEquals(root.getPackageName());
            root.recycle(); if (!own || floating == null) return null;
        }
        AccessibilityNodeInfo result = null;
        for (AccessibilityWindowInfo window : getWindows()) {
            try {
                if (result != null || window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION || (!window.isFocused() && !window.isActive())) continue;
                AccessibilityNodeInfo candidate = window.getRoot();
                if (candidate == null) continue;
                if (candidate.getPackageName() != null && CapturePolicy.QQ.contentEquals(candidate.getPackageName())) result = candidate;
                else candidate.recycle();
            } finally { window.recycle(); }
        }
        return result;
    }
    private int dp(int value) { return (int) (getResources().getDisplayMetrics().density * value); }
    private WindowManager.LayoutParams overlayParams(int width, int height) {
        return new WindowManager.LayoutParams(width, height, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
    }
    private void showFloating() {
        if (floating != null) return;
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(8), dp(3), dp(8), dp(3));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.rgb(32, 79, 61)); bg.setCornerRadius(dp(14));
        panel.setBackground(bg); panel.setElevation(dp(10));
        floatingStatus = new TextView(this); floatingStatus.setTextColor(Color.WHITE); floatingStatus.setTextSize(12);
        floatingStatus.setGravity(Gravity.CENTER); panel.addView(floatingStatus);
        Button done = new Button(this); done.setText("完成选群，返回寻迹"); done.setTextSize(14); done.setAllCaps(false);
        done.setOnClickListener(v -> finishSelection(true)); panel.addView(done);
        TextView cancel = new TextView(this); cancel.setText("取消并返回"); cancel.setTextColor(Color.WHITE);
        cancel.setGravity(Gravity.CENTER); cancel.setPadding(0, dp(4), 0, dp(8));
        cancel.setOnClickListener(v -> finishSelection(false)); panel.addView(cancel);
        WindowManager.LayoutParams params = overlayParams(dp(230), WindowManager.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL; params.y = dp(94);
        try { getSystemService(WindowManager.class).addView(panel, params); floating = panel; updateStatus(); }
        catch (RuntimeException ignored) {
            floatingStatus = null;
            new SelectionStore(this).report("系统未能显示寻迹勾选面板，请回到寻迹查看权限状态。", selected());
        }
    }
    private void updateStatus() {
        if (floatingStatus != null) {
            String text = "寻迹勾选层 · 已选 " + selected().length() + " 个\n只读群名，不读取聊天正文";
            if (!text.contentEquals(floatingStatus.getText())) floatingStatus.setText(text);
        }
    }
    private void hideAll() {
        hideRows();
        if (floating != null) {
            try { getSystemService(WindowManager.class).removeView(floating); } catch (RuntimeException ignored) {}
        }
        floating = null; floatingStatus = null;
    }
    private void finishSelection(boolean keep) {
        android.util.Log.i("XunjiSelection", "return clicked keep=" + keep + " count=" + selected().length());
        android.util.Log.i("XunjiSelection", "overlay windows created=" + createdWindows + " moved=" + movedWindows + " removed=" + removedWindows);
        SelectionStore store = new SelectionStore(this);
        store.report(keep ? "已返回，请核对本轮选择的群名。" : "已取消本轮选群。", keep ? selected() : new JSONArray());
        store.stop(); handler.removeCallbacksAndMessages(null); refreshGate.reset(); hideAll(); choices.clear();
        Intent app = new Intent(this, DemoActivity.class);
        app.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try { startActivity(app); }
        catch (RuntimeException ignored) {
            android.widget.Toast.makeText(this, "结果已保留，请点击桌面寻迹图标查看", android.widget.Toast.LENGTH_LONG).show();
        }
    }
    private static final class GroupRow {
        final String name; final int x; final int y;
        GroupRow(String name, int x, int y) { this.name = name; this.x = x; this.y = y; }
    }
    private static final class RowOverlay {
        final CheckBox box; final WindowManager.LayoutParams params;
        String name = ""; boolean visible;
        RowOverlay(CheckBox box, WindowManager.LayoutParams params) { this.box = box; this.params = params; params.windowAnimations = 0; }
    }
}
