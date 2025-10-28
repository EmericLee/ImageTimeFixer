package com.imagefixer.app.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

/**
 * 版本管理工具类，用于获取应用版本信息
 */
public class VersionUtils {
    private static final String TAG = "VersionUtils";

    /**
     * 获取应用版本名称
     * @param context 上下文
     * @return 版本名称
     */
    public static String getVersionName(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), 0);
            return packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "获取版本名称失败", e);
            return "未知版本";
        }
    }

    /**
     * 获取应用版本号
     * @param context 上下文
     * @return 版本号
     */
    public static int getVersionCode(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "获取版本号失败", e);
            return 0;
        }
    }

    /**
     * 获取完整版本信息
     * @param context 上下文
     * @return 格式：版本名称 (版本号)
     */
    public static String getFullVersionInfo(Context context) {
        return getVersionName(context) + " (" + getVersionCode(context) + ")";
    }
}