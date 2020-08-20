/* Copyright 2017 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package com.example.android.tflitecamerademo;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import org.opencv.android.OpenCVLoader;

/** Main {@code Activity} class for the Camera app. */
public class CameraActivity extends BaseActivity implements View.OnClickListener{
  String TAG = "CameraActivity";
  private PowerManager.WakeLock mWakeLock = null;   //  休眠锁
  Button btn;


  @SuppressLint("InvalidWakeLockTag")
  private void acquireWakeLock()
  {
    if(mWakeLock == null)
    {
      PowerManager mPM = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
      mWakeLock = mPM.newWakeLock(PowerManager.FULL_WAKE_LOCK|
              PowerManager.ON_AFTER_RELEASE,"PlayService");
      if(mWakeLock!=null)
      {
        mWakeLock.acquire();
      }
    }
  }

  /**
   * 释放锁
   */
  private void releaseWakeLock()
  {
    if(mWakeLock!=null)
    {
      mWakeLock.release();
      mWakeLock = null;
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // 在初始化主Activity时 设置当前显示窗口的xml文件
    setContentView(R.layout.activity_camera);

// 创建一个Button对象 并指向前面UI界面中的button组件
    btn = (Button)findViewById(R.id.button);
    // 为id等于test_button的按钮对象添加一个点击事件
    // 继承View.OnClickListener接口
    btn.setOnClickListener(this);

    boolean success = OpenCVLoader.initDebug();
    if (success) {
      Log.i("==", "load library successfully");
    } else {
      Log.i("==", "load library faile");
    }
    acquireWakeLock();
    // 判断用户是否第一次打开程序
    // 第一次进入 则把之前的fragment替换成 Camera2BasicFragment
    // 否则 不需要进行页面的初始化
    // 补充：.add() 添加一个fragment .remove()移除一个fragment .replace()替换一个fragment
    // fragment可以理解成组件 多个组件组成一个Activity
    if (null == savedInstanceState) {
//      getFragmentManager()
//          .beginTransaction()
//          // newInstance()：实例化一个fragment
//          .replace(R.id.container, Camera2BasicFragment.newInstance())
//          .commit();
      getFragmentManager()
          .beginTransaction()
          // newInstance()：实例化一个fragment
          .replace(R.id.container, PushFragment.newInstance())
          .commit();

    }
  }

  @Override
  public void onClick(View view) {
      // 主要用来初始化陀螺仪角度
      // 当按钮被点击时 更改配置文件中的陀螺仪相关参数
    if (Integer.parseInt(this.props.getProperty("initi_angle")) == 1){
      btn.setText("进行检测");
      this.props.setProperty("initi_angle", String.valueOf(0));
    } else {
      btn.setText("停止检测");
      this.props.setProperty("initi_angle", String.valueOf(1));
    }
  }
}
