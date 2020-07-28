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

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;

/** Main {@code Activity} class for the Camera app. */
public class CameraActivity extends BaseActivity {
  String TAG = "CameraActivity";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // 在初始化主Activity时 设置当前显示窗口的xml文件
    setContentView(R.layout.activity_camera);

    boolean success = OpenCVLoader.initDebug();
    if (success) {
      Log.i("==", "load library successfully");
    } else {
      Log.i("==", "load library faile");
    }
    // 判断用户是否第一次打开程序
    // 第一次进入 则把之前的fragment替换成 Camera2BasicFragment
    // 否则 不需要进行页面的初始化
    // 补充：.add() 添加一个fragment .remove()移除一个fragment .replace()替换一个fragment
    // fragment可以理解成组件 多个组件组成一个Activity
    if (null == savedInstanceState) {
      getFragmentManager()
          .beginTransaction()
          // newInstance()：实例化一个fragment
          .replace(R.id.container, Camera2BasicFragment.newInstance())
          .commit();

    }
  }

}
