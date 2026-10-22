package cn.xunji.hooklab.target;
import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.util.Log;
public class MainActivity extends Activity {
    public static String marker(){return "ORIGINAL";}
    @Override public void onCreate(Bundle state){super.onCreate(state);String result=marker();Log.i("XunjiHookLab","RESULT="+result+" uid="+android.os.Process.myUid());TextView text=new TextView(this);text.setText("Isolated rootless hook experiment\n\n"+result);text.setTextSize(22);text.setPadding(32,72,32,32);setContentView(text);try(java.io.FileOutputStream out=openFileOutput("result.txt",0)){out.write(result.getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(Exception e){Log.e("XunjiHookLab","Result storage failed");}}
}
