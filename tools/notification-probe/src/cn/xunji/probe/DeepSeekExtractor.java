package cn.xunji.probe;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import android.content.Context;

final class DeepSeekExtractor {
    private final Context context;
    DeepSeekExtractor(Context context){this.context=context.getApplicationContext();}
    private static final Set<String> CATEGORIES = new HashSet<>(Arrays.asList("作业", "竞赛", "考试", "活动", "报名", "志愿", "项目", "其他"));
    JSONArray extract(String apiKey, JSONObject source) throws Exception {return extract(apiKey,source,false);}
    JSONArray extractMemory(String apiKey, JSONObject source) throws Exception {return extract(apiKey,source,true);}
    private JSONArray extract(String apiKey, JSONObject source,boolean memory) throws Exception {
        String taskText=ExtractionPolicy.taskText(source.getString("text"));
        if(taskText.isEmpty())return new JSONArray();
        String system=read(context.getAssets().open(memory?"memory-extraction-rules.md":"extraction-rules.md"));
        if(memory){java.time.ZonedDateTime base=java.time.Instant.ofEpochMilli(source.getLong("receivedAt")).atZone(java.time.ZoneId.of("Asia/Shanghai"));
            system+="\n基准时间（北京时间）："+base.toString()+"；今天="+base.toLocalDate()+"，明天="+base.toLocalDate().plusDays(1)+"，后天="+base.toLocalDate().plusDays(2)+"。例如输入：我明天上午八点半到十一点半有一个图像视觉部的培训会。应输出category为活动，title为图像视觉部培训会，dueDate为"+base.toLocalDate().plusDays(1)+"，dueTime为08:30，event为{location:空字符串,startTime:08:30,endTime:11:30}。实际响应仍须使用合法JSON。";
        }
        JSONObject request = new JSONObject(); request.put("model", "deepseek-flash");
        request.put("temperature",0);
        request.put("thinking", new JSONObject().put("type", "disabled"));
        request.put("response_format", new JSONObject().put("type", "json_object")); request.put("max_tokens", 8000); request.put("stream", false);
        request.put("messages", new JSONArray().put(new JSONObject().put("role", "system").put("content", system))
            .put(new JSONObject().put("role", "user").put("content", taskText)));
        HttpURLConnection connection = (HttpURLConnection) new URL("https://api.deepseek.com/chat/completions").openConnection();
        connection.setConnectTimeout(15000); connection.setReadTimeout(45000); connection.setRequestMethod("POST"); connection.setDoOutput(true);
        connection.setInstanceFollowRedirects(false); connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        try {
            try (OutputStream stream = connection.getOutputStream()) { stream.write(request.toString().getBytes(StandardCharsets.UTF_8)); }
            int code = connection.getResponseCode();
            if (code != 200) {
                if (code == 401) throw new IllegalStateException("密钥无效，请在设置中重新填写");
                if (code == 402) throw new IllegalStateException("API余额不足，请在DeepSeek官方平台检查余额");
                if (code == 429) throw new IllegalStateException("请求过于频繁，请稍后重试");
                throw new IllegalStateException("模型服务暂不可用（HTTP " + code + "），原文已保留，可稍后重试");
            }
            JSONObject response = new JSONObject(read(connection.getInputStream()));
            JSONObject choice = response.getJSONArray("choices").getJSONObject(0);
            if ("length".equals(choice.optString("finish_reason"))) throw new IllegalStateException("整理结果过长，请分段粘贴原文后重试");
            String content = choice.getJSONObject("message").optString("content", "").trim();
            JSONArray cards = new JSONObject(content).getJSONArray("cards");
            if (!ExtractionPolicy.validCardCount(cards.length())) throw new IllegalStateException("模型返回的事项数量不正确，请重试");
            JSONArray result = new JSONArray();
            for (int i = 0; i < cards.length(); i++) {
                JSONObject card = cards.getJSONObject(i);
                String title = ExtractionPolicy.taskText(card.getString("title")); if (title.isEmpty() || title.length() > 160) throw new IllegalStateException("模型返回的标题不完整，请重试");
                String category = card.optString("category"); if(memory?!Arrays.asList("课程","作业","考试","竞赛","活动","其他").contains(category):!CATEGORIES.contains(category))category="其他";
                JSONArray requirements = card.getJSONArray("requirements"); if (requirements.length() > 128) throw new IllegalStateException("模型返回的要求过多，请重试");
                List<String> modelRequirements=new ArrayList<>();
                for (int j = 0; j < requirements.length(); j++) {
                    Object requirement = requirements.get(j);
                    if (!(requirement instanceof String) || ((String) requirement).length() > 6000) throw new IllegalStateException("模型返回的要求格式不正确，请重试");
                    String cleaned=ExtractionPolicy.taskText((String)requirement);
                    if(!cleaned.isEmpty())modelRequirements.add(cleaned);
                }
                JSONArray checked=new JSONArray(cards.length()==1?ExtractionPolicy.requirements(taskText,category,modelRequirements):modelRequirements);
                String deadline = ExtractionPolicy.taskText(card.optString("deadline", ""));
                if (deadline.length() > 120 || !taskText.contains(deadline)) deadline = "";
                JSONObject row=new JSONObject().put("title",title).put("category",category).put("requirements",checked).put("deadline",deadline);
                if(memory)row=MemoryNormalizer.normalize(row,card,taskText,source.getLong("receivedAt"),cards.length()==1);
                result.put(row);
            }
            return result;
        } finally { connection.disconnect(); }
    }
    private String read(InputStream input) throws Exception {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int length;
            while ((length = stream.read(buffer)) != -1) { output.write(buffer, 0, length); if (output.size() > 300000) throw new IllegalStateException("模型响应过大，请重试"); }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
