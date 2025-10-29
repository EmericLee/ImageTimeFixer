/**
 * Android Images time fixer (手机照片时间修正器)
 * 
 * 版权所有 © 2023 Android Images time fixer 保留所有权利
 * 
 * 本项目采用MIT许可证
 * 版本：0.5.0
 * 更新时间：2024-12-01
 * 
 * 主要开发者：disminde.lee limingrui emeric
 * 邮箱：2714845535@qq.com kometo@gmail.com
 * GitHub：https://github.com/disminde
 */
package com.imagefixer.app.utils;

import android.util.Log;
import android.content.Context;
import android.content.Intent;

/**
 * 自定义日志工具类，重载系统Log类的方法，同时发送日志广播到前端
 */
public class LogUtils {
    // 广播动作常量，与原ScanService保持一致
    public static final String ACTION_LOG_MESSAGE = "com.imagefixer.app.ACTION_LOG_MESSAGE";
    public static final String EXTRA_LOG_MESSAGE = "log_message";
    public static final String EXTRA_LOG_LEVEL = "log_level";
    
    private static Context context;
    private static boolean isInitialized = false;
    
    /**
     * 初始化日志工具类
     * @param ctx 上下文，用于发送广播
     */
    public static void init(Context ctx) {
        context = ctx;
        isInitialized = true;
    }
    
    /**
     * DEBUG级别日志
     */
    public static int d(String tag, String message) {
        int result = Log.d(tag, message);
        sendLogBroadcast(message, "DEBUG");
        return result;
    }
    
    public static int d(String tag, String message, Throwable tr) {
        int result = Log.d(tag, message, tr);
        String fullMessage = message + " - " + Log.getStackTraceString(tr);
        sendLogBroadcast(fullMessage, "DEBUG");
        return result;
    }
    
    /**
     * INFO级别日志
     */
    public static int i(String tag, String message) {
        int result = Log.i(tag, message);
        sendLogBroadcast(message, "INFO");
        return result;
    }
    
    public static int i(String tag, String message, Throwable tr) {
        int result = Log.i(tag, message, tr);
        String fullMessage = message + " - " + Log.getStackTraceString(tr);
        sendLogBroadcast(fullMessage, "INFO");
        return result;
    }
    
    /**
     * WARNING级别日志
     */
    public static int w(String tag, String message) {
        int result = Log.w(tag, message);
        sendLogBroadcast(message, "WARNING");
        return result;
    }
    
    public static int w(String tag, String message, Throwable tr) {
        int result = Log.w(tag, message, tr);
        String fullMessage = message + " - " + Log.getStackTraceString(tr);
        sendLogBroadcast(fullMessage, "WARNING");
        return result;
    }
    
    /**
     * ERROR级别日志
     */
    public static int e(String tag, String message) {
        int result = Log.e(tag, message);
        sendLogBroadcast(message, "ERROR");
        return result;
    }
    
    public static int e(String tag, String message, Throwable tr) {
        int result = Log.e(tag, message, tr);
        String fullMessage = message + " - " + Log.getStackTraceString(tr);
        sendLogBroadcast(fullMessage, "ERROR");
        return result;
    }
    
    /**
     * 发送日志广播
     */
    private static void sendLogBroadcast(String message, String level) {
        if (isInitialized && context != null) {
            Intent intent = new Intent(ACTION_LOG_MESSAGE);
            intent.putExtra(EXTRA_LOG_MESSAGE, message);
            intent.putExtra(EXTRA_LOG_LEVEL, level);
            // 设置包名以避免UnsafeImplicitIntentLaunch错误，确保只发送到当前应用内的接收器
            intent.setPackage(context.getPackageName());
            context.sendBroadcast(intent);
        }
    }
}