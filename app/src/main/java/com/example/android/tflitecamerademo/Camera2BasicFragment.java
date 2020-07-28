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
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v13.app.FragmentCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.tensorflow.lite.TensorFlowLite;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Basic fragments for the Camera. */
/** 继承Fragment 说明该类为一个Fragment子类 并实现了权限回调接口 **/
public class Camera2BasicFragment extends Fragment
        implements FragmentCompat.OnRequestPermissionsResultCallback {

  /** Tag for the {@link Log}. */
  private static final String TAG = "TfLiteCameraDemo";

  private static final String FRAGMENT_DIALOG = "dialog";

  private static final String HANDLE_THREAD_NAME = "CameraBackground";

  private static final int PERMISSIONS_REQUEST_CODE = 1;

  private final Object lock = new Object();
  private boolean runClassifier = false;
  private boolean checkedPermissions = false;
  private TextView textView;
  private ImageClassifier classifier;
  private CarBehaviorAnalysisByOpenCv carBehaviorAnalysisByOpenCv;
  private CameraActivity angle_activity;     // 获取该对象中的角度
  // 设置摄像头最大可接受的分辨率
  /** Max preview width that is guaranteed by Camera2 API */
  private static final int MAX_PREVIEW_WIDTH = 1920;
  /** Max preview height that is guaranteed by Camera2 API */
  private static final int MAX_PREVIEW_HEIGHT = 1080;

  /**
   * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a {@link
   * TextureView}.
   */
  private final TextureView.SurfaceTextureListener surfaceTextureListener =
          new TextureView.SurfaceTextureListener() {

            // 当textureView可用时 对摄像头进行初始化操作
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
              openCamera(width, height);

            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
              configureTransform(width, height);
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
              return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture texture) {
            }
          };

  /** ID of the current {@link CameraDevice}. */
  private String cameraId;

  /** An {@link AutoFitTextureView} for camera preview. */
  private AutoFitTextureView textureView;

  /** A {@link CameraCaptureSession } for camera preview. */
  private CameraCaptureSession captureSession;

  /** A reference to the opened {@link CameraDevice}. */
  private CameraDevice cameraDevice;

  /** The {@link android.util.Size} of camera preview. */
  private Size previewSize;

  /** {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state. */
  private final CameraDevice.StateCallback stateCallback =
          new CameraDevice.StateCallback() {

            @Override
            public void onOpened(@NonNull CameraDevice currentCameraDevice) {
              // This method is called when the camera is opened.  We start camera preview here.
              cameraOpenCloseLock.release();
              cameraDevice = currentCameraDevice;
              // 创建并显示预览图像
              createCameraPreviewSession();
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice currentCameraDevice) {
              cameraOpenCloseLock.release();
              currentCameraDevice.close();
              cameraDevice = null;
            }

            @Override
            public void onError(@NonNull CameraDevice currentCameraDevice, int error) {
              cameraOpenCloseLock.release();
              currentCameraDevice.close();
              cameraDevice = null;
              Activity activity = getActivity();
              if (null != activity) {
                activity.finish();
              }
            }
          };

  /** An additional thread for running tasks that shouldn't block the UI. */
  private HandlerThread backgroundThread;

  /** A {@link Handler} for running tasks in the background. */
  private Handler backgroundHandler;

  /** An {@link ImageReader} that handles image capture. */
  private ImageReader imageReader;

  /** {@link CaptureRequest.Builder} for the camera preview */
  private CaptureRequest.Builder previewRequestBuilder;

  /** {@link CaptureRequest} generated by {@link #previewRequestBuilder} */
  private CaptureRequest previewRequest;

  /** A {@link Semaphore} to prevent the app from exiting before closing the camera. */
  private Semaphore cameraOpenCloseLock = new Semaphore(1);

  /** A {@link CameraCaptureSession.CaptureCallback} that handles events related to capture. */
  private CameraCaptureSession.CaptureCallback captureCallback =
          new CameraCaptureSession.CaptureCallback() {

            @Override
            public void onCaptureProgressed(
                    @NonNull CameraCaptureSession session,
                    @NonNull CaptureRequest request,
                    @NonNull CaptureResult partialResult) {
            }

            @Override
            public void onCaptureCompleted(
                    @NonNull CameraCaptureSession session,
                    @NonNull CaptureRequest request,
                    @NonNull TotalCaptureResult result) {
            }
          };

  /**
   * Shows a {@link Toast} on the UI thread for the classification results.
   *
   * @param text The message to show
   */
  private void showToast(final String text) {
    final Activity activity = getActivity();
    if (activity != null) {
      activity.runOnUiThread(
              new Runnable() {
                @Override
                public void run() {
                  textView.setText(text);
                }
              });
    }
  }

  /**
   * Resizes image.
   *
   * Attempting to use too large a preview size could  exceed the camera bus' bandwidth limitation,
   * resulting in gorgeous previews but the storage of garbage capture data.
   *
   * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that is
   * at least as large as the respective texture view size, and that is at most as large as the
   * respective max size, and whose aspect ratio matches with the specified value. If such size
   * doesn't exist, choose the largest one that is at most as large as the respective max size, and
   * whose aspect ratio matches with the specified value.
   *
   * @param choices The list of sizes that the camera supports for the intended output class
   * @param textureViewWidth The width of the texture view relative to sensor coordinate
   * @param textureViewHeight The height of the texture view relative to sensor coordinate
   * @param maxWidth The maximum width that can be chosen
   * @param maxHeight The maximum height that can be chosen
   * @param aspectRatio The aspect ratio
   * @return The optimal {@code Size}, or an arbitrary one if none were big enough
   */
  private static Size chooseOptimalSize(
          Size[] choices,
          int textureViewWidth,
          int textureViewHeight,
          int maxWidth,
          int maxHeight,
          Size aspectRatio) {

    // Collect the supported resolutions that are at least as big as the preview Surface
    // 收集至少和预览图片一样大的尺寸
    List<Size> bigEnough = new ArrayList<>();
    // Collect the supported resolutions that are smaller than the preview Surface
    // 收集小于预览图所支持的分辨率
    List<Size> notBigEnough = new ArrayList<>();
    // 当前设备所支持的最大宽高
    int w = aspectRatio.getWidth();
    int h = aspectRatio.getHeight();
    // 遍历当前摄像头所支持的所有尺寸
    for (Size option : choices) {
      if (option.getWidth() <= maxWidth
              && option.getHeight() <= maxHeight
              && option.getHeight() == option.getWidth() * h / w) {
        if (option.getWidth() >= textureViewWidth && option.getHeight() >= textureViewHeight) {
          bigEnough.add(option);
        } else {
          notBigEnough.add(option);
        }
      }
    }

    // Pick the smallest of those big enough. If there is no one big enough, pick the
    // largest of those not big enough.
    // 若bigEnough有值 则选择所有bigEnough中最小的
    if (bigEnough.size() > 0) {
      return Collections.min(bigEnough, new CompareSizesByArea());
    // 若notBigEnough有值 则选择所有notBigEnough中最大的
    } else if (notBigEnough.size() > 0) {
      return Collections.max(notBigEnough, new CompareSizesByArea());
    } else {
      Log.e(TAG, "Couldn't find any suitable preview size");
      return choices[0];
    }
  }

  public static Camera2BasicFragment newInstance() {
    return new Camera2BasicFragment();
  }

  /** Layout the preview and buttons. */
  @Override
  public View onCreateView(
          LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

    // 获取角度的activity
    angle_activity = (CameraActivity)getActivity();

    return inflater.inflate(R.layout.fragment_camera2_basic, container, false);
  }

  /** Connect the buttons to their event handler. */
  @Override
  public void onViewCreated(final View view, Bundle savedInstanceState) {
    textureView = (AutoFitTextureView) view.findViewById(R.id.texture);
    textView = (TextView) view.findViewById(R.id.text);
  }

  /** Load the model and labels. */
  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    try {
      classifier = new ImageClassifier(getActivity());
      carBehaviorAnalysisByOpenCv = new CarBehaviorAnalysisByOpenCv();
    } catch (IOException e) {
      Log.e(TAG, e.toString());
      Log.e(TAG, "Failed to initialize an image classifier.");
    }
    startBackgroundThread();
  }

  @Override
  public void onResume() {
    super.onResume();
    startBackgroundThread();
    // When the screen is turned off and turned back on, the SurfaceTexture is already
    // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
    // a camera and start preview from here (otherwise, we wait until the surface is ready in
    // the SurfaceTextureListener).
    if (textureView.isAvailable()) {
      openCamera(textureView.getWidth(), textureView.getHeight());
    } else {
      textureView.setSurfaceTextureListener(surfaceTextureListener);
    }
  }

  @Override
  public void onPause() {
    closeCamera();
    stopBackgroundThread();
    super.onPause();
  }

  @Override
  public void onDestroy() {
    classifier.close();
    super.onDestroy();
  }

  /**
   * Sets up member variables related to camera.
   *
   * @param width The width of available size for camera preview
   * @param height The height of available size for camera preview
   */
  @SuppressLint("NewApi")
  private void setUpCameraOutputs(int width, int height) {
    Activity activity = getActivity();
    // 获取上下文中的系统摄像头服务对象
    CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
    try {
      // 罗列出设备可用的所有摄像头
      for (String cameraId : manager.getCameraIdList()) {
        // 通过摄像头id 获取该摄像头相关信息
        CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

        // We don't use a front facing camera in this sample.
        // 获取当前摄像头前置或者后置摄像头
        Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        // 若获取成功 且当前摄像头为前置摄像头则跳出当前循环
        if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
          continue;
        }
        // 获取当前摄像头所支持的所有输出格式和尺寸
        StreamConfigurationMap map =
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        // 获取失败则跳出循环
        if (map == null) {
          continue;
        }

        // For still image captures, we use the largest available size.
        // 当前摄像头会支持很多尺寸的输出，通过定义一个比较策略CompareSizesByArea
        // 得到摄像头所支持的最大尺寸
        Size largest =
                Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CompareSizesByArea());
        // 设置读取图片时的格式
        // 图片的宽和高，图片的格式，缓存两张图片
        imageReader =
                ImageReader.newInstance(
                        largest.getWidth(), largest.getHeight(), ImageFormat.JPEG, /*maxImages*/ 2);

        // ==============================   根据摄像头的旋转方向 动态的调整画面展示角度 =======================
        // Find out if we need to swap dimension to get the preview size relative to sensor
        // coordinate.
        // 当前摄像头展示的角度
        int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        // noinspection ConstantConditions
        /* 摄像头传感器方向 Orientation of the camera sensor */
        int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        boolean swappedDimensions = false;
        switch (displayRotation) {
          case Surface.ROTATION_0:
          case Surface.ROTATION_180:
            if (sensorOrientation == 90 || sensorOrientation == 270) {
              swappedDimensions = true;
            }
            break;
          case Surface.ROTATION_90:
          case Surface.ROTATION_270:
            if (sensorOrientation == 0 || sensorOrientation == 180) {
              swappedDimensions = true;
            }
            break;
          default:
            Log.e(TAG, "Display rotation is invalid: " + displayRotation);
        }
        // 获取当前屏幕的尺寸 并赋值给displaySize
        Point displaySize = new Point();
        activity.getWindowManager().getDefaultDisplay().getSize(displaySize);
        // 没有转换前的宽高 和 最大宽高
        int rotatedPreviewWidth = width;
        int rotatedPreviewHeight = height;
        int maxPreviewWidth = displaySize.x;
        int maxPreviewHeight = displaySize.y;
        // 转换前的宽高 和 可以转换的宽高
        if (swappedDimensions) {
          rotatedPreviewWidth = height;
          rotatedPreviewHeight = width;
          maxPreviewWidth = displaySize.y;
          maxPreviewHeight = displaySize.x;
        }
        // 可以转换的宽高是否超过预设的宽
        if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
          maxPreviewWidth = MAX_PREVIEW_WIDTH;
        }
        // 可以转换的宽高是否超过预设的高
        if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
          maxPreviewHeight = MAX_PREVIEW_HEIGHT;
        }
        // 参数讲解
        // 当前摄像头的所有输出尺寸
        // 屏幕旋转前的宽高
        // 屏幕旋转后的宽高（当旋转后的宽高大于预设宽高时 设置为默认宽高）
        // 当前摄像头所支持的最大输出尺寸
        // 功能：当前旋转后 摄像头所支持的尺寸 若等于预览尺寸 则直接使用预览尺寸 若小于预览尺寸
        //       则使用比预览尺寸略小的尺寸
        previewSize =
                chooseOptimalSize(
                        map.getOutputSizes(SurfaceTexture.class),
                        rotatedPreviewWidth,
                        rotatedPreviewHeight,
                        maxPreviewWidth,
                        maxPreviewHeight,
                        largest);

        // We fit the aspect ratio of TextureView to the size of preview we picked.
        // 调整当前长宽比 使得其和预览尺寸相匹配
        // 获取当前摄像头方向
        int orientation = getResources().getConfiguration().orientation;
        // 若为横屏 则设置数值大的为宽、数值小的为高
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
//          textureView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
          textureView.setAspectRatio(DeviceInfo.screenWidth(getContext()), DeviceInfo.screenHeight(getContext()));
        // 若为竖屏 则设置数值小的为宽、数值大的为高
        } else {
          textureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
        }
        // 绑定需要操作的摄像头id
        this.cameraId = cameraId;
        return;
      }
    } catch (CameraAccessException e) {
      e.printStackTrace();
    } catch (NullPointerException e) {
      // Currently an NPE is thrown when the Camera2API is used but not supported on the
      // device this code runs.
      ErrorDialog.newInstance(getString(R.string.camera_error))
              .show(getChildFragmentManager(), FRAGMENT_DIALOG);
    }
  }

  private String[] getRequiredPermissions() {
    Activity activity = getActivity();
    try {
      PackageInfo info =
              activity
                      .getPackageManager()
                      .getPackageInfo(activity.getPackageName(), PackageManager.GET_PERMISSIONS);
      String[] ps = info.requestedPermissions;
      if (ps != null && ps.length > 0) {
        return ps;
      } else {
        return new String[0];
      }
    } catch (Exception e) {
      return new String[0];
    }
  }

  /** Opens the camera specified by {@link Camera2BasicFragment#cameraId}. */
  @SuppressLint("MissingPermission")
  private void openCamera(int width, int height) {
    // 用户打开摄像头时 会申请一次调用摄像头权限
    // 为了保证用户允许调用摄像头 此处进行一次检验
    if (!checkedPermissions && !allPermissionsGranted()) {
      FragmentCompat.requestPermissions(this, getRequiredPermissions(), PERMISSIONS_REQUEST_CODE);
      return;
    } else {
      checkedPermissions = true;
    }
    //  获取后置摄像头信息  设置图片输入尺寸、格式  设置屏幕旋转后textureView的尺寸
    setUpCameraOutputs(width, height);
    // 当发生旋转时 缩放图片
    configureTransform(width, height);

    Activity activity = getActivity();
    // 获取系统摄像头服务
    CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
    try {
      if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
        throw new RuntimeException("Time out waiting to lock camera opening.");
      }
    // 打开摄像头 并执行回调函数 同时开启一个后台线程
      manager.openCamera(cameraId, stateCallback, backgroundHandler);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    } catch (InterruptedException e) {
      throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
    }
  }

  private boolean allPermissionsGranted() {
    for (String permission : getRequiredPermissions()) {
      if (ContextCompat.checkSelfPermission(getActivity(), permission)
          != PackageManager.PERMISSION_GRANTED) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
  }

  /** Closes the current {@link CameraDevice}. */
  private void closeCamera() {
    try {
      cameraOpenCloseLock.acquire();
      if (null != captureSession) {
        captureSession.close();
        captureSession = null;
      }
      if (null != cameraDevice) {
        cameraDevice.close();
        cameraDevice = null;
      }
      if (null != imageReader) {
        imageReader.close();
        imageReader = null;
      }
    } catch (InterruptedException e) {
      throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
    } finally {
      cameraOpenCloseLock.release();
    }
  }

  /** Starts a background thread and its {@link Handler}. */
  private void startBackgroundThread() {
    backgroundThread = new HandlerThread(HANDLE_THREAD_NAME);
    backgroundThread.start();
    backgroundHandler = new Handler(backgroundThread.getLooper());
    synchronized (lock) {
      runClassifier = true;
    }

    backgroundHandler.post(periodicClassify);
  }

  /** Stops the background thread and its {@link Handler}. */
  private void stopBackgroundThread() {
    backgroundThread.quitSafely();
    try {
      backgroundThread.join();
      backgroundThread = null;
      backgroundHandler = null;
      synchronized (lock) {
        runClassifier = false;
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  /** Takes photos and classify them periodically. */
  private Runnable periodicClassify =
      new Runnable() {
        @Override
        public void run() {
          synchronized (lock) {
            if (runClassifier) {
              classifyFrame();
            }

          }
          backgroundHandler.post(periodicClassify);
        }
      };

  /** Creates a new {@link CameraCaptureSession} for camera preview. */
  private void createCameraPreviewSession() {
    try {
      SurfaceTexture texture = textureView.getSurfaceTexture();
      assert texture != null;

      // We configure the size of default buffer to be the size of camera preview we want.
      // 配置图片默认缓冲器大小
      texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

      // This is the output Surface we need to start preview.
      // 获取Surface显示预览数据
      Surface surface = new Surface(texture);

      // We set up a CaptureRequest.Builder with the output Surface.
      // 设置一个预览请求的缓冲区
      previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
      // 向缓冲器中加入需要装载的目标
      previewRequestBuilder.addTarget(surface);

      // 在这里，我们为相机预览创建一个CameraCaptureSession。
      cameraDevice.createCaptureSession(
          Arrays.asList(surface),
          new CameraCaptureSession.StateCallback() {

            @Override
            public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
              // The camera is already closed
              if (null == cameraDevice) {
                return;
              }

              // When the session is ready, we start displaying the preview.
              // 预览或者拍照时 会使用到的一个核心类
              captureSession = cameraCaptureSession;
              try {
                // 相机预览时自动对焦应。
                previewRequestBuilder.set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                // Finally, we start displaying the camera preview.
                previewRequest = previewRequestBuilder.build();
                captureSession.setRepeatingRequest(
                    previewRequest, captureCallback, backgroundHandler);
              } catch (CameraAccessException e) {
                e.printStackTrace();
              }
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
              showToast("Failed");
            }
          },
          null);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  /**
   * Configures the necessary {@link android.graphics.Matrix} transformation to `textureView`. This
   * method should be called after the camera preview size is determined in setUpCameraOutputs and
   * also the size of `textureView` is fixed.
   *
   * @param viewWidth The width of `textureView`
   * @param viewHeight The height of `textureView`
   */
  private void configureTransform(int viewWidth, int viewHeight) {
    Activity activity = getActivity();
    if (null == textureView || null == previewSize || null == activity) {
      return;
    }
    // 得到设备方向
    int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
    Matrix matrix = new Matrix();
    RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
    RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
    float centerX = viewRect.centerX();
    float centerY = viewRect.centerY();
    if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
      bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
      matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
      float scale =
          Math.max(
              (float) viewHeight / previewSize.getHeight(),
              (float) viewWidth / previewSize.getWidth());
      matrix.postScale(scale, scale, centerX, centerY);
      matrix.postRotate(90 * (rotation - 2), centerX, centerY);
    } else if (Surface.ROTATION_180 == rotation) {
      matrix.postRotate(180, centerX, centerY);
    }
    // 当发生旋转时 对图片进行缩放
    textureView.setTransform(matrix);
  }


  int timeF = 3; // 每隔timeF取一张图片
  int timeF_switch_bg = 20;  // 当提取三张图片后 再替换对比底图

  Mat last_image = new Mat();  // 上一时刻的图片
  Mat tmp_last_image = new Mat(); // 记录当前时刻图片的中间状态
  List<Mat> much_catch_image = new ArrayList<>(); // 缓存多张图片 用于服务器上传


  int number = 0;  // 用于模拟陀螺仪
  int tmp_angle = 0; // 记录当前陀螺仪的角度

  // 数据转换的临时变量
  Mat tmp_now_image = new Mat();
  Mat tmp_cut_image = new Mat();
  Integer tmp_car_state = 0; // 车辆行为识别结果

  Integer now_image_len = 0;  // 当前检测区域的直线数量

  double image_sim = 1.0;  // 计算前后两帧的相似度
  Integer car_speed = 0;  // 当前车载设备速度
  Integer image_sim_number = 10;  // 前后两帧相似度持续小于7的次数
  Integer last_car_state = 0;  // 记录车辆上一时刻状态
  boolean is_angle_ok = false;   // 陀螺仪角度是否放置正确
  Integer first_angle = 0;  // 第一次得到陀螺仪数据
  /** Classifies a frame from the preview stream. */
  private void classifyFrame() {
    String textToShow = "";
    // 获取每一帧的数据
    if (classifier == null || getActivity() == null || cameraDevice == null) {
      showToast("Uninitialized Classifier or invalid context.");
      return;
    }
    // 获取摄像头数据
    Bitmap bitmap =
            textureView.getBitmap(ImageClassifier.DIM_IMG_SIZE_X, ImageClassifier.DIM_IMG_SIZE_Y);
    Bitmap bitmap_analysis =
            textureView.getBitmap(ImageClassifier.Analysis_IMG_SIZE_X, ImageClassifier.Analysis_IMG_SIZE_Y);

    // 得到当前陀螺仪角度数据  为了方便测试 陀螺仪数据需要自定义
    int tmp_angle = (int) angle_activity.currentAngle;

    if (!is_angle_ok){
      if( (tmp_angle < 20) ){
        first_angle = Math.min(first_angle + 1, 10);
      } else if(((90 - tmp_angle) < 20)){
        first_angle = Math.max(first_angle - 1, -10);
      }
    }

    if (first_angle == -10) {
      tmp_angle = 90 - tmp_angle;
      is_angle_ok = true;
    } else if(first_angle != 10){
      textToShow = "设备没有正确放置！";
      showToast(textToShow);
    } else {
      is_angle_ok = true;
    }
    Log.d("================", "currentAngle========>" + tmp_angle + " : " + first_angle);
    // 当前车载设备的速度

    if (is_angle_ok) {

      if (timeF <= 0) {
        timeF = 3;
        Utils.bitmapToMat(bitmap_analysis, tmp_now_image);
        tmp_cut_image = carBehaviorAnalysisByOpenCv.deal_flage(tmp_now_image);   // 对图片进行预处理
        now_image_len = carBehaviorAnalysisByOpenCv.contour_extraction(tmp_cut_image); // 获取图片轮廓

        // 初始化基础参数
        if (last_image.cols() == 0) {
          last_image = tmp_cut_image;
          tmp_last_image = tmp_cut_image;
        }

        // 计算前后两帧的相似度
        image_sim = carBehaviorAnalysisByOpenCv.split_blok_box_sim(last_image, tmp_cut_image);
        if (image_sim > 7){
          image_sim_number =Math.max(image_sim_number - 1,0);
        } else {
          image_sim_number = Math.min(image_sim_number + 1, 10);
        }


        //  缓存图片 用于上传服务器 只缓存50帧
        int tmp_much_catch_image_len = much_catch_image.size();
        if (tmp_much_catch_image_len > 50) {
          much_catch_image.remove(0);
          much_catch_image.add(tmp_now_image);
        } else {
          much_catch_image.add(tmp_now_image);
        }

        Log.d(TAG, "" + last_car_state +" "+ image_sim_number +" "+ now_image_len +" "+car_speed +" "+ tmp_angle);

        // 结合 陀螺仪 模型检测结果 进行车辆行为分析
        // 返回 车辆行为结果索引
        tmp_car_state = carBehaviorAnalysisByOpenCv.carBehaviorAnalysis(last_car_state, image_sim_number,now_image_len,car_speed, tmp_angle);

        last_car_state = tmp_car_state;  // 记录当前时刻车辆状态

        // 相识度对比底片替换 使得last_image与flag相差一定帧数
        if (timeF_switch_bg <= 0) {
          timeF_switch_bg = 20;
          last_image = tmp_last_image;
          tmp_last_image = tmp_cut_image;
        } else {
          timeF_switch_bg = timeF_switch_bg - 1;
        }

        number = number + 1;

      }else{
        timeF = timeF - 1;
      }

      // ================================ 开始进行货物类别检测   ================
      if (tmp_car_state !=0) {
        textToShow = classifier.classifyFrame(bitmap);
      }

      textToShow = carBehaviorAnalysisByOpenCv.result_text.get(tmp_car_state) +" \n"+
              "当前轮廓: " + now_image_len + " \n " +
              "当前角度：" + tmp_angle + " \n " + textToShow;

      showToast(textToShow);
      bitmap.recycle();
      bitmap_analysis.recycle();
    }

  }

    /** Compares two {@code Size}s based on their areas. */
  private static class CompareSizesByArea implements Comparator<Size> {

    @Override
    public int compare(Size lhs, Size rhs) {
      // We cast here to ensure the multiplications won't overflow
      return Long.signum(
          (long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
    }
  }

  /** Shows an error message dialog. */
  public static class ErrorDialog extends DialogFragment {

    private static final String ARG_MESSAGE = "message";

    public static ErrorDialog newInstance(String message) {
      ErrorDialog dialog = new ErrorDialog();
      Bundle args = new Bundle();
      args.putString(ARG_MESSAGE, message);
      dialog.setArguments(args);
      return dialog;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      final Activity activity = getActivity();
      return new AlertDialog.Builder(activity)
          .setMessage(getArguments().getString(ARG_MESSAGE))
          .setPositiveButton(
              android.R.string.ok,
              new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                  activity.finish();
                }
              })
          .create();
    }
  }
}
