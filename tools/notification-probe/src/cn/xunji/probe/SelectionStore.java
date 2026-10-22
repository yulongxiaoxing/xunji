package cn.xunji.probe;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.HashSet;
import java.util.Set;

final class SelectionStore {
    private final SharedPreferences prefs;
    private final int boot;
    SelectionStore(Context context) {
        prefs = context.getSharedPreferences("selection", Context.MODE_PRIVATE);
        boot = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
    }
    void start() {
        prefs.edit().putLong("until", SystemClock.elapsedRealtime() + 120_000).putInt("boot", boot)
            .putString("candidates", "[]").putString("status", "等待 QQ 选群界面").apply();
    }
    void stop() { prefs.edit().putLong("until", 0).apply(); }
    boolean active(String pkg) {
        return SelectionPolicy.active(pkg, prefs.getLong("until", 0), SystemClock.elapsedRealtime(), prefs.getInt("boot", -2), boot);
    }
    String status() { return prefs.getString("status", "尚未开始选群读取测试"); }
    JSONArray candidates() {
        try { return new JSONArray(prefs.getString("candidates", "[]")); }
        catch (Exception ignored) { return new JSONArray(); }
    }
    void report(String status, JSONArray candidates) {
        SharedPreferences.Editor edit = prefs.edit().putString("status", status);
        if (candidates != null) edit.putString("candidates", candidates.toString());
        edit.apply();
    }
    Set<String> followed() { return new HashSet<>(prefs.getStringSet("followed", new HashSet<>())); }
    void follow(String name) {
        String clean = SelectionPolicy.groupListLabel("com.tencent.mobileqq:id/qb_troop_list_view", "com.tencent.mobileqq:id/text1", name);
        if (clean.isEmpty()) return;
        Set<String> names = followed(); names.add(clean); prefs.edit().putStringSet("followed", names).apply();
    }
    void unfollow(String name) { Set<String> names = followed(); names.remove(name); prefs.edit().putStringSet("followed", names).apply(); }
    void clear() { prefs.edit().clear().apply(); }
}
