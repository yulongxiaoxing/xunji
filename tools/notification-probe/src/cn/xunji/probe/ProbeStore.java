package cn.xunji.probe;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import org.json.JSONArray;
import org.json.JSONObject;

final class ProbeStore {
    private final SharedPreferences prefs;
    private final int boot;
    ProbeStore(Context context) {
        prefs = context.getSharedPreferences("probe", Context.MODE_PRIVATE);
        boot = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
    }
    long until() { return boot >= 0 && prefs.getInt("boot", -2) == boot ? prefs.getLong("until", 0) : 0; }
    String chosen() { return prefs.getString("chosen", ""); }
    String chosenLabel() { return prefs.getString("chosenLabel", ""); }
    void sample(long until) { prefs.edit().putLong("until", until).putInt("boot", boot).apply(); }
    void choose(String id, String label) {
        prefs.edit().putString("chosen", id).putString("chosenLabel", label).apply();
    }
    void clear() { prefs.edit().clear().apply(); }
    void startFilterTest(int followedCount) {
        sample(0);
        choose("", "");
        SharedPreferences.Editor edit = prefs.edit().putLong("filterStarted", System.currentTimeMillis()).putBoolean("filterActive", true);
        for (String key : new String[]{"kept", "unfollowed", "private", "summary", "empty", "invalid"}) edit.putInt("count_" + key, 0);
        edit.apply();
        android.util.Log.i("XunjiFilter", "test started followed=" + followedCount);
    }
    void stopFilterTest() { prefs.edit().putBoolean("filterActive", false).apply(); }
    boolean filterTestActive() { return prefs.getBoolean("filterActive", false); }
    void count(String reason) {
        if (!filterTestActive()) return;
        String key = "count_" + reason;
        int count = prefs.getInt(key, 0) + 1;
        prefs.edit().putInt(key, count).apply();
        android.util.Log.i("XunjiFilter", "decision=" + reason + " count=" + count);
    }
    String filterSummary() {
        if (prefs.getLong("filterStarted", 0) == 0) return "过滤验证尚未开始";
        int skipped = 0;
        for (String key : new String[]{"unfollowed", "private", "summary", "empty", "invalid"}) skipped += prefs.getInt("count_" + key, 0);
        return "仅关注群验证：" + (filterTestActive() ? "进行中" : "已结束")
            + "\n保留通知 " + prefs.getInt("count_kept", 0) + " 次，跳过 " + skipped + " 次"
            + "\n未匹配 " + prefs.getInt("count_unfollowed", 0) + " · 明确私聊 " + prefs.getInt("count_private", 0)
            + " · 汇总 " + prefs.getInt("count_summary", 0) + " · 正文空 " + prefs.getInt("count_empty", 0)
            + "\n计数按通知回调次数，可能包含同一通知的更新。";
    }
    JSONArray rows() {
        try { return new JSONArray(prefs.getString("rows", "[]")); }
        catch (Exception ignored) { return new JSONArray(); }
    }
    void append(JSONObject row) {
        JSONArray previous = rows();
        // Notification updates with identical contents are not separate samples.
        String signature = row.optString("key") + "|" + row.optString("title") + "|" + row.optString("text");
        JSONArray next = new JSONArray();
        next.put(row);
        for (int i = 0; i < previous.length() && next.length() < 20; i++) {
            JSONObject old = previous.optJSONObject(i);
            if (old == null) continue;
            String oldSignature = old.optString("key") + "|" + old.optString("title") + "|" + old.optString("text");
            if (!signature.equals(oldSignature)) next.put(old);
        }
        prefs.edit().putString("rows", next.toString()).apply();
    }
}
