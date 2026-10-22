package cn.xunji.hooklab.module;
import android.util.Log;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
public class LoadProbe implements IXposedHookLoadPackage {
    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p)throws Throwable {
        if("cn.xunji.hooklab.target".equals(p.packageName)){
            Log.i("XunjiHookLab","MODULE_LOADED target uid="+android.os.Process.myUid());
            XposedHelpers.findAndHookMethod("cn.xunji.hooklab.target.MainActivity",p.classLoader,"marker",new XC_MethodHook(){@Override protected void afterHookedMethod(MethodHookParam param){param.setResult("HOOK_OK");}});
        }else if("com.tencent.mobileqq".equals(p.packageName)){
            Log.i("XunjiHookLab","MODULE_LOADED QQ interface probe only");
            Class<?> listener=Class.forName("com.tencent.qqnt.kernel.nativeinterface.IKernelMsgListener",false,p.classLoader);
            listener.getDeclaredMethod("onRecvMsg",java.util.ArrayList.class);
            Class<?> text=Class.forName("com.tencent.qqnt.kernel.nativeinterface.TextElement",false,p.classLoader);
            java.lang.reflect.Method get=text.getDeclaredMethod("getContent");
            Log.i("XunjiHookLab","QQ_INTERFACE_OK declaredCallbacks="+listener.getDeclaredMethods().length);
            java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
            XC_MethodHook.Unhook hook=XposedBridge.hookMethod(get,new XC_MethodHook(){@Override protected void afterHookedMethod(MethodHookParam param){calls.incrementAndGet();}});
            try {
                Object controlled=text.getDeclaredConstructor().newInstance();
                text.getDeclaredMethod("setContent",String.class).invoke(controlled,"XUNJI_CONTROLLED_TEXT_V1");
                Object value=get.invoke(controlled);
                if(calls.get()>0&&"XUNJI_CONTROLLED_TEXT_V1".equals(value))Log.i("XunjiHookLab","QQ_METHOD_HOOK_OK controlledObject=true uid="+android.os.Process.myUid());
                else throw new IllegalStateException("Controlled QQ getter hook did not run");
            }finally{hook.unhook();}
        }
    }
}
