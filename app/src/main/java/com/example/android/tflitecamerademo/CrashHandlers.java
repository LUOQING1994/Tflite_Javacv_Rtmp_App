package com.example.android.tflitecamerademo;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

/**
 * 未捕获异常捕捉类
 */
public class CrashHandlers implements Thread.UncaughtExceptionHandler {

    public static final String TAG = "CrashHandlers";

    // 系统默认的UncaughtException处理类
    private Thread.UncaughtExceptionHandler mDefaultHandler;

    // CrashHandler实例
    private static CrashHandlers instance;
    // 程序的Context对象
    private Context mContext;


    /**
     * 保证只有一个CrashHandler实例
     */
    private CrashHandlers() {
    }

    /**
     * 获取CrashHandler实例 ,单例模式
     */
    public synchronized static CrashHandlers getInstance() {
        if (instance == null) {
            instance = new CrashHandlers();
        }
        return instance;
    }

    /**
     * 初始化
     *
     * @param context
     */
    public void init(Context context) {
        mContext = context;
        // 获取系统默认的UncaughtException处理器
        mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        // 设置该CrashHandler为程序的默认处理器
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    /**
     * 当UncaughtException发生时会转入该函数来处理
     */
    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        if (!handleException(thread, ex) && mDefaultHandler != null) {
            // 如果用户没有处理则让系统默认的异常处理器来处理
            mDefaultHandler.uncaughtException(thread, ex);
        } else {
            try {
                //3秒后执行重启
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Log.e(TAG, e.toString());
            }

            //发送广播重新启动APP
//            mContext.sendBroadcast(new Intent("com.xin.crash"));
            //退出程序，否则无法重新启动
//            android.os.Process.killProcess(android.os.Process.myPid());
//            System.exit(1);
        }
    }

    /**
     * 自定义错误处理,收集错误信息 发送错误报告等操作均在此完成.
     *
     * @param ex
     * @return true-->处理了该异常信息
     *          false-->未处理异常.
     */
    private boolean handleException(Thread thread, Throwable ex) {
        if (ex == null) {
            //未处理异常，返回false
            return false;
        }
        // 使用Toast来显示异常信息
        new Thread() {
            @Override
            public void run() {
                Looper.prepare();
                //捕获到异常，弹出提示
                Toast.makeText(mContext, "即将重启", Toast.LENGTH_SHORT).show();
                Looper.loop();
            }
        }.start();
        return true;
    }
}

