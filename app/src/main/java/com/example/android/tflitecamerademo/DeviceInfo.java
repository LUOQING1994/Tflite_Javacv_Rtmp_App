package com.example.android.tflitecamerademo;

import android.content.Context;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.WindowManager;


public class DeviceInfo{
    /**
     * 获取当前设备屏幕的宽度，以像素为单位。
     * @return 当前设备屏幕的宽度。
     */
    public static int screenWidth(Context ctx)
    {
        WindowManager windowManager = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1){
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
        }else{
            windowManager.getDefaultDisplay().getMetrics(metrics);
        }
        return metrics.widthPixels;
    }

    /**
     * 获取当前设备屏幕的高度，以像素为单位。
     * @return 当前设备屏幕的高度。
     */
    public static int screenHeight(Context ctx)
    {
        WindowManager windowManager = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
        } else {
            windowManager.getDefaultDisplay().getMetrics(metrics);
        }
        return metrics.heightPixels;
    }
}