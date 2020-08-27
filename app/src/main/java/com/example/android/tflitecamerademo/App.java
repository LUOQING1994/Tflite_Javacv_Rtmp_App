package com.example.android.tflitecamerademo;


import android.util.Log;

public class App extends CrashApplication {


    @Override
    public void onCreate() {
        super.onCreate();
        // 设置崩溃后自动重启 APP
        UncaughtExceptionHandlerImpl.getInstance().init(this, BuildConfig.DEBUG, true, 0, CameraActivity.class);

        // 禁止重启
//        UncaughtExceptionHandlerImpl.getInstance().init(this,BuildConfig.DEBUG);
    }
}

