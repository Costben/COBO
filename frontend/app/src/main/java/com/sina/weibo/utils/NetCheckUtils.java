package com.sina.weibo.utils;

import android.content.Context;

/** libwbutil.so 里的 cum 计算入口，静态方法，和原版同名同签名。 */
public class NetCheckUtils {

    /** 传入完整 path（含开头的 /2/）。 */
    public static native String getParam(Context context, String path);
}
