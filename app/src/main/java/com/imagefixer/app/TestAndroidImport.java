package com.imagefixer.app;

import android.app.ActivityManager;
import android.content.Context;

/**
 * 测试Android SDK导入是否正常
 */
public class TestAndroidImport {
    
    public void testImport(Context context) {
        // 测试导入android.app包
        if (context != null) {
            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            // 这里只是为了测试导入，不会实际执行
        }
    }
}