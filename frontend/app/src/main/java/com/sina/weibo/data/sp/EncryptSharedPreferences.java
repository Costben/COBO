package com.sina.weibo.data.sp;

import android.content.Context;

/** 只为撑住 JNI_OnLoad 里的方法查找，两个入口都不会被调用到。 */
public class EncryptSharedPreferences {

    private native byte[] loadSpFile(Context context, String str);

    private native void saveSpFile(Context context, String str, byte[] data);
}
