package com.sina.weibo;

import android.app.Application;
import android.content.Context;

import com.sina.weibo.net.e;

import java.io.File;

/**
 * 微博安全 SDK 的 JNI 入口。
 *
 * 包名、类名、方法名和方法签名都不能改：libwbutil.so 的 JNI_OnLoad 会按名字查找
 * 这一整套方法并缓存 methodID，少一个就在 loadLibrary 阶段抛 NoSuchMethodError，
 * JNI 层表现为 NewGlobalRef + pending exception，进程直接 abort。
 *
 * 真正用到的只有 init / newCalculateS / getIValue，其余是被 JNI_OnLoad 查表要求存在。
 */
public class WeiboApplication extends Application {

    private static Context sContext;

    static {
        System.loadLibrary("wbutil");
    }

    /** 我们不是把 Cobo 的 Application 换成这个类，所以要手动喂一个 context 进来。 */
    public static void attach(Context context) {
        sContext = context.getApplicationContext();
    }

    public static Context getContext() {
        return sContext;
    }

    public native String calculateS(String str);

    public native String generateCheckToken(String str, String str2);

    public native String getDecryptionString(String str);

    public native String getIValue(String str);

    public native e getNetInstance(Context context, String str);

    public native e getNetInstanceFromHotFix(
            Context context, String str, File file, String str2, String str3, String str4);

    /** 任何签名调用之前必须先执行，否则 s 会退化成重复字符。 */
    public native void init(String str);

    public native void initDaemon(
            int i, boolean z, String str, String str2, String str3, String str4);

    /** 容器接口的 s 参数，按 uid 计算，可缓存。 */
    public native String newCalculateS(String str);
}
