package cn.xunji.probe;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.json.JSONArray;
import org.json.JSONObject;

final class EditionData {
    static void initialize(Context context, CardStore cards) throws Exception {
        long received = System.currentTimeMillis();
        LocalDate tomorrow = Instant.ofEpochMilli(received)
            .atZone(ZoneId.of("Asia/Shanghai")).toLocalDate().plusDays(1);
        SQLiteDatabase db = cards.getWritableDatabase();
        db.beginTransaction();
        try {
            if (cards.source("hackathon-demo:0") == null) {
                JSONObject sample = readSample(context, tomorrow);
                JSONArray notifications = sample.getJSONArray("notifications");
                for (int i = 0; i < notifications.length(); i++) {
                    JSONObject item = notifications.getJSONObject(i);
                    String id = "hackathon-demo:" + i;
                    if (!cards.insertSource(id, item.getString("group"), item.getString("publisher"),
                            item.getString("original"), received - i)) {
                        throw new IllegalStateException("Could not save demo notification " + i);
                    }
                    cards.replaceCards(id, new JSONArray().put(item.getJSONObject("card")));
                }
                JSONArray agenda = sample.getJSONArray("agenda");
                for (int i = 0; i < agenda.length(); i++) {
                    cards.createCard(agenda.getJSONObject(i));
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static JSONObject readSample(Context context, LocalDate tomorrow) throws Exception {
        StringBuilder json = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open("hackathon-sample.json"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line);
            }
        }
        return new JSONObject(json.toString()
            .replace("{tomorrow}", tomorrow.toString())
            .replace("{dayAfter}", tomorrow.plusDays(1).toString()));
    }
}
