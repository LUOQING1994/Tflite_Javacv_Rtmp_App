package com.example.android.tflitecamerademo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.app.Fragment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v13.app.FragmentCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.daniulive.smartpublisher.SmartPublisherJniV2;
import com.eventhandle.NTSmartEventCallbackV2;
import com.eventhandle.NTSmartEventID;
import com.voiceengine.NTAudioRecordV2;
import com.voiceengine.NTAudioRecordV2Callback;

import org.bytedeco.javacv.AndroidFrameConverter;
import org.bytedeco.javacv.Frame;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.List;

import static org.bytedeco.javacpp.avutil.AV_PIX_FMT_RGBA;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link PushFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class PushFragment extends Fragment {

    private static final String TAG = "PushFragment";

    private View root;
    private Context ctx;
    private CameraActivity activity;
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private TextView textView;
    private ImageView imageView;
    private MainCarBehaviorAnalysis mainCarBehaviorAnalysis;
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    private SmartPublisherJniV2 libPublisher = null;

    NTAudioRecordV2 audioRecord_ = null;
    NTAudioRecordV2Callback audioRecordCallback_ = null;

    private long publisherHandle = 0;    //推送handle

    /* 推送类型选择
     * 0: 音视频
     * 1: 纯音频
     * 2: 纯视频
     * */
    private int pushType = 2;

    /* 水印类型选择
     * 0: 图片水印
     * 1: 全部水印
     * 2: 文字水印
     * 3: 不加水印
     * */
    private int watemarkType = 3;

    /* 推流分辨率选择
     * 0: 640*480
     * 1: 320*240
     * 2: 720*480
     * 3: 1280*720
     * */
    //分辨率为演示方便设置4个档位，可与系统枚举到的分辨率比较后再设置，以防系统不支持

    /* video软编码profile设置
     * 1: baseline profile
     * 2: main profile
     * 3: high profile
     * */
    private int sw_video_encoder_profile = 1;    //default with baseline profile

    /* 推送类型选择
     * 0: 视频软编码(H.264)
     * 1: 视频硬编码(H.264)
     * 2: 视频硬编码(H.265)
     * */
    private int videoEncodeType = 0;

    private Camera mCamera = null;
    private Camera.AutoFocusCallback myAutoFocusCallback = null;    //自动对焦

    private boolean mPreviewRunning = false; //priview状态
    private boolean isPushingRtmp = false;    //RTMP推送状态
    private boolean isRecording = false;    //录像状态
    private boolean isPauseRecording = true;    //录像暂停、恢复录像
    private boolean isRTSPServiceRunning = false;    //RTSP服务状态
    private boolean isRTSPPublisherRunning = false; //RTSP流发布状态
    private boolean isPushingRtsp = false;     //RTSP推送状态

    final private String logoPath = "/sdcard/daniulivelogo.png";
    private boolean isWritelogoFileSuccess = false;

//    final private String publishURL = "rtmp://192.168.43.1:53/live/test";
    final private String publishURL = "rtmp://127.0.0.1:1935/live/test";

    private static final int FRONT = 1;        //前置摄像头标记
    private static final int BACK = 2;        //后置摄像头标记
    private int currentCameraType = BACK;    //当前打开的摄像头标记
    private static final int PORTRAIT = 1;    //竖屏
    private static final int LANDSCAPE = 2;    //横屏 home键在右边的情况
    private static final int LANDSCAPE_LEFT_HOME_KEY = 3; // 横屏 home键在左边的情况
    private int currentOrigentation = LANDSCAPE;
    private int curCameraIndex = -1;

    private int videoWidth = 1280;
    private int videoHeight = 720;

    private int frameCount = 0;

    private boolean is_noise_suppression = true;
    private boolean is_agc = false;
    private boolean is_speex = false;
    private boolean is_mute = false;

    private float in_audio_volume_ = 1.0f;

    private boolean is_mirror = false;
    private int sw_video_encoder_speed = 3;
    private boolean is_sw_vbr_mode = true;

    private String imageSavePath;

    private String encrypt_key = "";
    private String encrypt_iv = "";
    private ImageClassifier classifier;  // 模型检测


    private boolean checkedPermissions = false;
    private static final int PERMISSIONS_REQUEST_CODE = 1;
    static {
        System.loadLibrary("SmartPublisher");
    }

    public PushFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment PushFragment.
     */
    public static PushFragment newInstance() {
        return new PushFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainCarBehaviorAnalysis = new MainCarBehaviorAnalysis();
        matNumberUtils = new MatNumberUtils();
        activity = (CameraActivity) getActivity();
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }
    /** Load the model and labels. */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        try {
            classifier = new ImageClassifier(getActivity());
        } catch (IOException e) {
            Log.e(TAG, "Failed to initialize an image classifier.");
        }
        startBackgroundThread();
    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        root = inflater.inflate(R.layout.fragment_push, container, false);
        ctx = getContext();
        surfaceView = root.findViewById(R.id.surfaceView);
        textView = root.findViewById(R.id.text);
        imageView = root.findViewById(R.id.imageView);
        return root;
    }

    private void hasP(){
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(surfaceCallback);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        //自动聚焦变量回调
        myAutoFocusCallback = new Camera.AutoFocusCallback() {
            public void onAutoFocus(boolean success, Camera camera) {
                if (success)//success表示对焦成功
                {
                    Log.i(TAG, "onAutoFocus succeed...");
                } else {
                    Log.i(TAG, "onAutoFocus failed...");
                }
            }
        };

        libPublisher = new SmartPublisherJniV2();
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

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (!checkedPermissions && !allPermissionsGranted()) {
            FragmentCompat.requestPermissions(PushFragment.this, getRequiredPermissions(), PERMISSIONS_REQUEST_CODE);
        } else {
            checkedPermissions = true;
            hasP();
        }
    }
    private SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder surfaceHolder) {
            Log.i(TAG, "surfaceCreated..");
            try {

                int CammeraIndex = findBackCamera();
                Log.i(TAG, "BackCamera: " + CammeraIndex);

                if (CammeraIndex == -1) {
                    CammeraIndex = findFrontCamera();
                    currentCameraType = FRONT;
                    if (CammeraIndex == -1) {
                        Log.i(TAG, "NO camera!!");
                        return;
                    }
                } else {
                    currentCameraType = BACK;
                }
                if (mCamera == null) {
                    openCamera(currentCameraType);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
            Log.i(TAG, "surfaceChanged..");
            initCamera(surfaceHolder);
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
            Log.i(TAG, "Surface Destroyed");
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE){
            Log.i("==========", "请求权限回调成功");
            hasP();
        }
    }

    //Check if it has back camera
    private int findBackCamera() {
        int cameraCount = 0;
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        cameraCount = Camera.getNumberOfCameras();

        for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
            Camera.getCameraInfo(camIdx, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                return camIdx;
            }
        }
        return -1;
    }

    //Check if it has front camera
    private int findFrontCamera() {
        int cameraCount = 0;
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        cameraCount = Camera.getNumberOfCameras();

        for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
            Camera.getCameraInfo(camIdx, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                return camIdx;
            }
        }
        return -1;
    }

    @SuppressLint("NewApi")
    private void openCamera(int type) {
        int frontIndex = -1;
        int backIndex = -1;
        int cameraCount = Camera.getNumberOfCameras();
        Log.i(TAG, "cameraCount: " + cameraCount);

        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int cameraIndex = 0; cameraIndex < cameraCount; cameraIndex++) {
            Camera.getCameraInfo(cameraIndex, info);

            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                frontIndex = cameraIndex;
            } else if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                backIndex = cameraIndex;
            }
        }

        currentCameraType = type;
        if (type == FRONT && frontIndex != -1) {
            curCameraIndex = frontIndex;
            mCamera =  Camera.open(frontIndex);
        } else if (type == BACK && backIndex != -1) {
            curCameraIndex = backIndex;
            mCamera =  Camera.open(backIndex);
        }
        // view创建完毕后 直接执行推流操作
        try {
            Thread.sleep(1000);
            startPush();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /*it will call when surfaceChanged*/
    private void initCamera(SurfaceHolder holder) {
        Log.i(TAG, "initCamera..");

        if (mPreviewRunning)
            mCamera.stopPreview();

        Camera.Parameters parameters;
        try {
            parameters = mCamera.getParameters();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return;
        }

        /*
        List<Size> pictureSizes = parameters.getSupportedPictureSizes();
        int length = pictureSizes.size();
        for (int i = 0; i < length; i++) {
            Log.e("SupportedPictureSizes","SupportedPictureSizes : " + pictureSizes.get(i).width + "x" + pictureSizes.get(i).height);
        }

        List<Size> previewSizes = parameters.getSupportedPreviewSizes();
        length = previewSizes.size();
        for (int i = 0; i < length; i++) {
            Log.e("SupportedPreviewSizes","SupportedPreviewSizes : " + previewSizes.get(i).width + "x" + previewSizes.get(i).height);
        }
        */

        parameters.setPreviewSize(videoWidth, videoHeight);
        parameters.setPictureFormat(PixelFormat.JPEG);
        parameters.setPreviewFormat(PixelFormat.YCbCr_420_SP);

        SetCameraFPS(parameters);

        setCameraDisplayOrientation(activity, curCameraIndex, mCamera);

        mCamera.setParameters(parameters);

        int bufferSize = (((videoWidth | 0xf) + 1) * videoHeight * ImageFormat.getBitsPerPixel(parameters.getPreviewFormat())) / 8;

        mCamera.addCallbackBuffer(new byte[bufferSize]);

        mCamera.setPreviewCallbackWithBuffer(previewCallback);
        try {
            mCamera.setPreviewDisplay(holder);
        } catch (Exception ex) {
            // TODO Auto-generated catch block
            if (null != mCamera) {
                mCamera.release();
                mCamera = null;
            }
            ex.printStackTrace();
        }
        mCamera.startPreview();
        mCamera.autoFocus(myAutoFocusCallback);
        mPreviewRunning = true;
    }
    byte[] camera_data;
    MatNumberUtils matNumberUtils;
    /**
     *  开启推流服务
     *  1，terminal中输入：adb shell   -> su ->  srs -c /data/srs/srs.conf   ->   netstat -nltp
     */
    private Camera.PreviewCallback previewCallback = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            frameCount++;
            if (frameCount % 3000 == 0) {
                Log.i("OnPre", "gc+");
                Runtime.getRuntime().gc();
                Log.i("OnPre", "gc-");
            }
            if (data == null) {
                Camera.Parameters params = camera.getParameters();
                Camera.Size size = params.getPreviewSize();
                int bufferSize = (((size.width | 0x1f) + 1) * size.height * ImageFormat.getBitsPerPixel(params.getPreviewFormat())) / 8;
                camera.addCallbackBuffer(new byte[bufferSize]);
            } else {
//                Log.i("数据转换", "isPushingRtmp:" + isRTSPPublisherRunning + " isPushingRtmp:" + isPushingRtmp+ " isRecording:" + isRecording+ " isPushingRtsp:" + isPushingRtsp);
                if (isRTSPPublisherRunning || isPushingRtmp || isRecording || isPushingRtsp) {
                    libPublisher.SmartPublisherOnCaptureVideoData(publisherHandle, data, data.length, currentCameraType, currentOrigentation);
                    currentSpeed = (int)activity.currentSpeed;
                    currentAngle = (int)activity.currentAngle;
                }
                camera_data = data;
                camera.addCallbackBuffer(data);
            }
        }
    };




    /**
     * byte[]转bitmap
     * @return
     */
    public Bitmap BytesToBimap(byte[] data) {
        YuvImage yuvimage = new YuvImage(data, ImageFormat.NV21, videoWidth,videoHeight, null); //20、20分别是图的宽度与高度
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        yuvimage.compressToJpeg(new Rect(0, 0,videoWidth, videoHeight), 80, baos);//80--JPG图片的质量[0-100],100最高
        byte[] jdata = baos.toByteArray();
        try {
            baos.flush();
            baos.close();
            yuvimage = null;
            System.gc();
        } catch (Exception e){

        }
        return BitmapFactory.decodeByteArray(jdata, 0, jdata.length);
    }
    //Byte转Bitmap
    public Bitmap ByteArray2Bitmap(byte[] data) {
        Mat mat = new Mat(videoHeight,videoWidth, CvType.CV_8UC1);
        mat.put(0,0,data);
        Bitmap tmp_model_bitmap = Bitmap.createBitmap(mat.width(), mat.height(),
                Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mat,tmp_model_bitmap);
        mat.release();
        return tmp_model_bitmap;
    }
    public Bitmap rawByteArray2RGBABitmap2(byte[] data, int width, int height) {
        int frameSize = width * height;
        int[] rgba = new int[frameSize];
        for (int i = 0; i < height; i++)
            for (int j = 0; j < width; j++) {
                int y = (0xff & ((int) data[i * width + j]));
                int u = (0xff & ((int) data[frameSize + (i >> 1) * width + (j & ~1) + 0]));
                int v = (0xff & ((int) data[frameSize + (i >> 1) * width + (j & ~1) + 1]));
                y = y < 16 ? 16 : y;
                int r = Math.round(1.164f * (y - 16) + 1.596f * (v - 128));
                int g = Math.round(1.164f * (y - 16) - 0.813f * (v - 128) - 0.391f * (u - 128));
                int b = Math.round(1.164f * (y - 16) + 2.018f * (u - 128));
                r = r < 0 ? 0 : (r > 255 ? 255 : r);
                g = g < 0 ? 0 : (g > 255 ? 255 : g);
                b = b < 0 ? 0 : (b > 255 ? 255 : b);
                rgba[i * width + j] = 0xff000000 + (b << 16) + (g << 8) + r;
            }
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bmp.setPixels(rgba, 0 , width, 0, 0, width, height);
        return bmp;
    }

    private HandlerThread backgroundThread;
    private static final String HANDLE_THREAD_NAME = "CameraBackground";
    private Handler backgroundHandler;
    private final Object lock = new Object();
    private boolean runClassifier = false;
    private int currentSpeed;
    private int currentAngle;
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
    private void classifyFrame() {
        if (classifier == null || getActivity() == null || camera_data == null) {
            showToast("等待推流开始。。。。。。",null);
            return;
        }
        runClassifier = false;
        Bitmap frame_data = BytesToBimap(camera_data);
//        Bitmap frame_data = rawByteArray2RGBABitmap2(camera_data, videoWidth,videoHeight);
        matNumberUtils = mainCarBehaviorAnalysis.carBehaviorAnalysis(frame_data,activity,classifier, currentSpeed, currentAngle);
//        model_frame_data = Bitmap.createBitmap(matNumberUtils.getIamge().cols(), matNumberUtils.getIamge().rows(),
//                Bitmap.Config.ARGB_4444);
//        Utils.matToBitmap(matNumberUtils.getIamge(),model_frame_data);
//         绘制结果
//        Bitmap unDelMap = drawImageText(matNumberUtils.getToShow(),model_frame_data);
//        model_frame_data.recycle();
        showToast(matNumberUtils.getToShow(),frame_data);
//        showToast("开始了",frame_data);
        runClassifier = true;
//        frame_data.recycle();
    }
    /**
     * 使用canvas结果绘制
     */
    public Bitmap drawImageText(String showText,Bitmap unDelMap){
        Canvas canvas = new Canvas(unDelMap);
        // new antialised Paint
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        // text color - #3D3D3D
        paint.setColor(Color.rgb(255, 0, 0));
        // text size in pixels
        paint.setTextSize((int) (80));
        String[] splitArray = showText.split("\n");

        for (int i = 0; i < splitArray.length;i ++) {

            int x = 100;
            int y = 100 + i * 80;
            canvas.drawText(splitArray[i], x , y , paint);
        }

        return unDelMap;
    }
    /**
     * Shows a {@link } on the UI thread for the classification results.
     *
     * @param text The message to show
     */
    private void showToast(final String text, Bitmap show_frame_data) {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            if (text != null){
                                textView.setText(text);
                            }
                            if (show_frame_data != null){
                                imageView.setImageBitmap(show_frame_data);
                            }
                        }
                    });
        }
    }

    /**
     * 拉取网络流信息
     */
    private FFmpegFrameGrabber grabber;
    private AndroidFrameConverter converter;
    private Frame frame;
    private boolean isRestart = true;

    public void readRtmpVedio(){
        // 根据id 获取UI界面中的ImageView对象 并把操作结果展示到该对象中
        imageView = root.findViewById(R.id.imageView);
//        String vedioUrl = "rtmp://192.168.42.174:1935/live/test";
//        String vedioUrl = "rtmp://192.168.43.1/live/test";
        String vedioUrl = "rtmp://192.168.101.183:1935/stream/pupils_trace";

        try {
            grabber = new FFmpegFrameGrabber(vedioUrl);
            grabber.setImageWidth(videoWidth);
            grabber.setImageHeight(videoHeight);
            //为了加快转bitmap这句一定要写
            grabber.setPixelFormat(AV_PIX_FMT_RGBA);
            grabber.start(String.valueOf(50*1024));
            isRestart = true;
            converter = new AndroidFrameConverter();
        } catch (IOException e) {
            e.printStackTrace();
            textView.setText("服务器中没有该文件!");
        }
        startBackgroundThread();
        while (isRestart) {
            try {
                frame = grabber.grabImage();
                if (frame == null){
                    continue;
                }
                currentSpeed = (int)activity.currentSpeed;
                currentAngle = (int)activity.currentAngle;
//                frame_data = converter.convert(frame);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }



    private void SetCameraFPS(Camera.Parameters parameters) {
        if (parameters == null)
            return;

        int[] findRange = null;

        int defFPS = 20 * 1000;

        List<int[]> fpsList = parameters.getSupportedPreviewFpsRange();
        if (fpsList != null && fpsList.size() > 0) {
            for (int i = 0; i < fpsList.size(); ++i) {
                int[] range = fpsList.get(i);
                if (range != null
                        && Camera.Parameters.PREVIEW_FPS_MIN_INDEX < range.length
                        && Camera.Parameters.PREVIEW_FPS_MAX_INDEX < range.length) {
                    Log.i(TAG, "Camera index:" + i + " support min fps:" + range[Camera.Parameters.PREVIEW_FPS_MIN_INDEX]);

                    Log.i(TAG, "Camera index:" + i + " support max fps:" + range[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]);

                    if (findRange == null) {
                        if (defFPS <= range[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]) {
                            findRange = range;

                            Log.i(TAG, "Camera found appropriate fps, min fps:" + range[Camera.Parameters.PREVIEW_FPS_MIN_INDEX]
                                    + " ,max fps:" + range[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]);
                        }
                    }
                }
            }
        }

        if (findRange != null) {
            parameters.setPreviewFpsRange(findRange[Camera.Parameters.PREVIEW_FPS_MIN_INDEX], findRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]);
        }
    }

    private void setCameraDisplayOrientation(Activity activity, int cameraId, android.hardware.Camera camera) {
        android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }
        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;
        } else {
            // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }

        Log.i(TAG, "curDegree: " + result);

        camera.setDisplayOrientation(result);
    }

    private void startPush(){

        Log.i(TAG, "onClick start push rtmp..");

        if (libPublisher == null)
            return;

        if (!isRecording && !isRTSPPublisherRunning && !isPushingRtsp) {
            InitAndSetConfig();
        }


        Log.i(TAG, publishURL);

        if (libPublisher.SmartPublisherSetURL(publisherHandle, publishURL) != 0) {
            Log.e(TAG, "Failed to set publish stream URL..");
        }

        if(encrypt_key != null && !encrypt_key.isEmpty()) {
            Log.i(TAG, "encrypt_key:" + encrypt_key);

            int is_encrypt_video = 1;
            int is_encrypt_audio = 1;

            if (pushType == 1)
            {
                is_encrypt_video = 0;
            }
            else if (pushType == 2)
            {
                is_encrypt_audio = 0;
            }

            libPublisher.SetRtmpEncryptionOption(publisherHandle, publishURL, is_encrypt_video, is_encrypt_audio);

            //加密算法可自行设置
            int encryption_algorithm = 1;
            libPublisher.SetRtmpEncryptionAlgorithm(publisherHandle, publishURL, encryption_algorithm);

            int key_len = 16;

            if (encrypt_key.length() > 16 && encrypt_key.length() <= 24) {
                key_len = 24;
            } else if (encrypt_key.length() > 24) {
                key_len = 32;
            }

            byte[] key = new byte[key_len];

            for (int i = 0; i < key_len; i++) {
                key[i] = 0;
            }

            try {
                byte[] key_utf8 = encrypt_key.getBytes("UTF-8");

                int copy_len = key_utf8.length < key_len ? key_utf8.length : key_len;

                for (int i = 0; i < copy_len; ++i) {
                    key[i] = key_utf8[i];
                }

            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            int ret = libPublisher.SetRtmpEncryptionKey(publisherHandle, publishURL, key, key.length);

            if(ret != 0)
            {
                Log.e(TAG, "Call SmartPublisherSetRtmpEncryptionKey failed, errorID: " + ret);
            }
        }

        if(encrypt_iv != null && !encrypt_iv.isEmpty()) {
            int iv_len = 16;

            byte[] iv = new byte[iv_len];

            for (int i = 0; i < iv_len; i++) {
                iv[i] = 0;
            }

            try {
                byte[] iv_utf8 = encrypt_iv.getBytes("UTF-8");

                int copy_len = iv_utf8.length < iv_len ? iv_utf8.length : iv_len;

                for (int i = 0; i < copy_len; ++i) {
                    iv[i] = iv_utf8[i];
                }

            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            int ret = libPublisher.SetRtmpEncryptionIV(publisherHandle, publishURL, iv, iv.length);

            if(ret != 0)
            {
                Log.e(TAG, "Call SetRtmpEncryptionIV failed, errorID: " + ret);
            }
        }

        int startRet = libPublisher.SmartPublisherStartPublisher(publisherHandle);
        if (startRet != 0) {
            isPushingRtmp = false;

            Log.e(TAG, "Failed to start push stream..");
            return;
        }

        if (!isRecording && !isRTSPPublisherRunning && !isPushingRtsp) {
            if (pushType == 0 || pushType == 1) {
                CheckInitAudioRecorder();    //enable pure video publisher..
            }
        }
        isPushingRtmp = true;
    }

    private void InitAndSetConfig() {
        Log.i(TAG, "videoWidth: " + videoWidth + " videoHeight: " + videoHeight
                + " pushType:" + pushType);

        int audio_opt = 1;
        int video_opt = 1;

        if (pushType == 1) {
            video_opt = 0;
        } else if (pushType == 2) {
            audio_opt = 0;
        }

        publisherHandle = libPublisher.SmartPublisherOpen(ctx, audio_opt, video_opt,
                videoWidth, videoHeight);

        if (publisherHandle == 0) {
            Log.e(TAG, "sdk open failed!");
            return;
        }

        Log.i(TAG, "publisherHandle=" + publisherHandle);

        if(videoEncodeType == 1)
        {
            int h264HWKbps = setHardwareEncoderKbps(true, videoWidth, videoHeight);

            Log.i(TAG, "h264HWKbps: " + h264HWKbps);

            int isSupportH264HWEncoder = libPublisher
                    .SetSmartPublisherVideoHWEncoder(publisherHandle, h264HWKbps);

            if (isSupportH264HWEncoder == 0) {
                Log.i(TAG, "Great, it supports h.264 hardware encoder!");
            }
        }
        else if (videoEncodeType == 2)
        {
            int hevcHWKbps = setHardwareEncoderKbps(false, videoWidth, videoHeight);

            Log.i(TAG, "hevcHWKbps: " + hevcHWKbps);

            int isSupportHevcHWEncoder = libPublisher
                    .SetSmartPublisherVideoHevcHWEncoder(publisherHandle, hevcHWKbps);

            if (isSupportHevcHWEncoder == 0) {
                Log.i(TAG, "Great, it supports hevc hardware encoder!");
            }
        }

        if(is_sw_vbr_mode)	//H.264 software encoder
        {
            int is_enable_vbr = 1;
            int video_quality = CalVideoQuality(videoWidth, videoHeight, true);
            int vbr_max_bitrate = CalVbrMaxKBitRate(videoWidth, videoHeight);

            libPublisher.SmartPublisherSetSwVBRMode(publisherHandle, is_enable_vbr, video_quality, vbr_max_bitrate);
        }

        libPublisher.SetSmartPublisherEventCallbackV2(publisherHandle, new EventHandeV2());

        // 如果想和时间显示在同一行，请去掉'\n'
        String watermarkText = "大牛直播(daniulive)\n\n";

        String path = logoPath;

        if (watemarkType == 0) {
            if (isWritelogoFileSuccess)
                libPublisher.SmartPublisherSetPictureWatermark(publisherHandle, path,
                        SmartPublisherJniV2.WATERMARK.WATERMARK_POSITION_TOPRIGHT, 160,
                        160, 10, 10);

        } else if (watemarkType == 1) {
            if (isWritelogoFileSuccess)
                libPublisher.SmartPublisherSetPictureWatermark(publisherHandle, path,
                        SmartPublisherJniV2.WATERMARK.WATERMARK_POSITION_TOPRIGHT, 160,
                        160, 10, 10);

            libPublisher.SmartPublisherSetTextWatermark(publisherHandle, watermarkText, 1,
                    SmartPublisherJniV2.WATERMARK.WATERMARK_FONTSIZE_BIG,
                    SmartPublisherJniV2.WATERMARK.WATERMARK_POSITION_BOTTOMRIGHT, 10, 10);

            // libPublisher.SmartPublisherSetTextWatermarkFontFileName("/system/fonts/DroidSansFallback.ttf");

            // libPublisher.SmartPublisherSetTextWatermarkFontFileName("/sdcard/DroidSansFallback.ttf");
        } else if (watemarkType == 2) {
            libPublisher.SmartPublisherSetTextWatermark(publisherHandle, watermarkText, 1,
                    SmartPublisherJniV2.WATERMARK.WATERMARK_FONTSIZE_BIG,
                    SmartPublisherJniV2.WATERMARK.WATERMARK_POSITION_BOTTOMRIGHT, 10, 10);

            // libPublisher.SmartPublisherSetTextWatermarkFontFileName("/system/fonts/DroidSansFallback.ttf");
        } else {
            Log.i(TAG, "no watermark settings..");
        }
        // end

        if (!is_speex) {
            // set AAC encoder
            libPublisher.SmartPublisherSetAudioCodecType(publisherHandle, 1);

            // set aac bit-rate
            //libPublisher.SmartPublisherSetAudioBitRate(publisherHandle, 128);

        } else {
            // set Speex encoder
            libPublisher.SmartPublisherSetAudioCodecType(publisherHandle, 2);
            libPublisher.SmartPublisherSetSpeexEncoderQuality(publisherHandle, 8);
        }

        libPublisher.SmartPublisherSetNoiseSuppression(publisherHandle, is_noise_suppression ? 1
                : 0);

        libPublisher.SmartPublisherSetAGC(publisherHandle, is_agc ? 1 : 0);

        libPublisher.SmartPublisherSetInputAudioVolume(publisherHandle, 0 , in_audio_volume_);

        // libPublisher.SmartPublisherSetClippingMode(publisherHandle, 0);

        libPublisher.SmartPublisherSetSWVideoEncoderProfile(publisherHandle, sw_video_encoder_profile);

        libPublisher.SmartPublisherSetSWVideoEncoderSpeed(publisherHandle, sw_video_encoder_speed);

        // libPublisher.SetRtmpPublishingType(publisherHandle, 0);

        // libPublisher.SmartPublisherSetGopInterval(publisherHandle, 40);

        // libPublisher.SmartPublisherSetFPS(publisherHandle, 15);

        // libPublisher.SmartPublisherSetSWVideoBitRate(publisherHandle, 600, 1200);

        libPublisher.SmartPublisherSaveImageFlag(publisherHandle, 1);

        if (libPublisher.SmartPublisherSetPostUserDataQueueMaxSize(publisherHandle, 3, 0) != 0) {
            Log.e(TAG, "Failed to SetPostUserDataQueueMaxSize..");
        }
    }

    private void CheckInitAudioRecorder() {
        if (audioRecord_ == null) {
            //audioRecord_ = new NTAudioRecord(this, 1);

            audioRecord_ = new NTAudioRecordV2(ctx);
        }

        if (audioRecord_ != null) {
            Log.i(TAG, "CheckInitAudioRecorder call audioRecord_.start()+++...");

            audioRecordCallback_ = new NTAudioRecordV2CallbackImpl();

            // audioRecord_.IsMicSource(true);      //如采集音频声音过小，可以打开此选项

            // audioRecord_.IsRemoteSubmixSource(true);

            audioRecord_.AddCallback(audioRecordCallback_);

            //audioRecord_.Start(44100,  1);

            audioRecord_.Start();

            Log.i(TAG, "CheckInitAudioRecorder call audioRecord_.start()---...");


            //Log.i(TAG, "onCreate, call executeAudioRecordMethod..");
            // auido_ret: 0 ok, other failed
            //int auido_ret= audioRecord_.executeAudioRecordMethod();
            //Log.i(TAG, "onCreate, call executeAudioRecordMethod.. auido_ret=" + auido_ret);
        }
    }

    //设置H.264/H.265硬编码码率(按照25帧计算)
    private int setHardwareEncoderKbps(boolean isH264, int width, int height)
    {
        int kbit_rate = 2000;
        int area = width * height;

        if (area <= (320 * 300)) {
            kbit_rate = isH264?350:280;
        } else if (area <= (370 * 320)) {
            kbit_rate = isH264?470:400;
        } else if (area <= (640 * 360)) {
            kbit_rate = isH264?850:650;
        } else if (area <= (640 * 480)) {
            kbit_rate = isH264?1000:800;
        } else if (area <= (800 * 600)) {
            kbit_rate = isH264?1050:950;
        } else if (area <= (900 * 700)) {
            kbit_rate = isH264?1450:1100;
        } else if (area <= (1280 * 720)) {
            kbit_rate = isH264?2000:1500;
        } else if (area <= (1366 * 768)) {
            kbit_rate = isH264?2200:1900;
        } else if (area <= (1600 * 900)) {
            kbit_rate = isH264?2700:2300;
        } else if (area <= (1600 * 1050)) {
            kbit_rate =isH264?3000:2500;
        } else if (area <= (1920 * 1080)) {
            kbit_rate = isH264?4500:2800;
        } else {
            kbit_rate = isH264?4000:3000;
        }
        return kbit_rate;
    }

    private int CalVideoQuality(int w, int h, boolean is_h264)
    {
        int area = w*h;

        int quality = is_h264 ? 23 : 28;

        if ( area <= (320 * 240) )
        {
            quality = is_h264? 23 : 27;
        }
        else if ( area <= (640 * 360) )
        {
            quality = is_h264? 25 : 28;
        }
        else if ( area <= (640 * 480) )
        {
            quality = is_h264? 26 : 28;
        }
        else if ( area <= (960 * 600) )
        {
            quality = is_h264? 26 : 28;
        }
        else if ( area <= (1280 * 720) )
        {
            quality = is_h264? 27 : 29;
        }
        else if ( area <= (1600 * 900) )
        {
            quality = is_h264 ? 28 : 30;
        }
        else if ( area <= (1920 * 1080) )
        {
            quality = is_h264 ? 29 : 31;
        }
        else
        {
            quality = is_h264 ? 30 : 32;
        }

        return quality;
    }

    private int CalVbrMaxKBitRate(int w, int h)
    {
        int max_kbit_rate = 2000;

        int area = w*h;

        if (area <= (320 * 300))
        {
            max_kbit_rate = 320;
        }
        else if (area <= (360 * 320))
        {
            max_kbit_rate = 400;
        }
        else if (area <= (640 * 360))
        {
            max_kbit_rate = 600;
        }
        else if (area <= (640 * 480))
        {
            max_kbit_rate = 700;
        }
        else if (area <= (800 * 600))
        {
            max_kbit_rate = 800;
        }
        else if (area <= (900 * 700))
        {
            max_kbit_rate = 1000;
        }
        else if (area <= (1280 * 720))
        {
            max_kbit_rate = 1400;
        }
        else if (area <= (1366 * 768))
        {
            max_kbit_rate = 1700;
        }
        else if (area <= (1600 * 900))
        {
            max_kbit_rate = 2400;
        }
        else if (area <= (1600 * 1050))
        {
            max_kbit_rate = 2600;
        }
        else if (area <= (1920 * 1080))
        {
            max_kbit_rate = 2900;
        }
        else
        {
            max_kbit_rate = 3500;
        }

        return max_kbit_rate;
    }
    class EventHandeV2 implements NTSmartEventCallbackV2 {
        @Override
        public void onNTSmartEventCallbackV2(long handle, int id, long param1, long param2, String param3, String param4, Object param5) {

            Log.i(TAG, "EventHandeV2: handle=" + handle + " id:" + id);

            String publisher_event = "";

            switch (id) {
                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_STARTED:
                    publisher_event = "开始..";
                    break;
                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_CONNECTING:
                    publisher_event = "连接中..";
                    break;
                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_CONNECTION_FAILED:
                    publisher_event = "连接失败..";
                    break;
                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_CONNECTED:
                    publisher_event = "连接成功..";
                    break;
                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_DISCONNECTED:
                    publisher_event = "连接断开..";
                    break;
                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_STOP:
                    publisher_event = "关闭..";
                    break;
                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_RECORDER_START_NEW_FILE:
                    publisher_event = "开始一个新的录像文件 : " + param3;
                    break;
                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_ONE_RECORDER_FILE_FINISHED:
                    publisher_event = "已生成一个录像文件 : " + param3;
                    break;

                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_SEND_DELAY:
                    publisher_event = "发送时延: " + param1 + " 帧数:" + param2;
                    break;

                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_CAPTURE_IMAGE:
                    publisher_event = "快照: " + param1 + " 路径：" + param3;

                    if (param1 == 0) {
                        publisher_event = publisher_event + "截取快照成功..";
                    } else {
                        publisher_event = publisher_event + "截取快照失败..";
                    }
                    break;
                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUBLISHER_RTSP_URL:
                    publisher_event = "RTSP服务URL: " + param3;
                    break;
                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUSH_RTSP_SERVER_RESPONSE_STATUS_CODE:
                    publisher_event ="RTSP status code received, codeID: " + param1 + ", RTSP URL: " + param3;
                    break;
                case NTSmartEventID.EVENT_DANIULIVE_ERC_PUSH_RTSP_SERVER_NOT_SUPPORT:
                    publisher_event ="服务器不支持RTSP推送, 推送的RTSP URL: " + param3;
                    break;
            }

            String str = "当前回调状态：" + publisher_event;

            Log.i(TAG, str);
        }
    }

    class NTAudioRecordV2CallbackImpl implements NTAudioRecordV2Callback {
        @Override
        public void onNTAudioRecordV2Frame(ByteBuffer data, int size, int sampleRate, int channel, int per_channel_sample_number) {

//    		 Log.i(TAG, "onNTAudioRecordV2Frame size=" + size + " sampleRate=" + sampleRate + " channel=" + channel
//    				 + " per_channel_sample_number=" + per_channel_sample_number);


            if (publisherHandle != 0) {
                libPublisher.SmartPublisherOnPCMData(publisherHandle, data, size, sampleRate, channel, per_channel_sample_number);

                //libPublisher.SmartPublisherOnPCMDataV2(publisherHandle, data, 0, size, sampleRate, channel, per_channel_sample_number);

               /* data.rewind();
               java.nio.ByteOrder old_order = data.order();
               data.order(java.nio.ByteOrder.nativeOrder());
               java.nio.ShortBuffer short_buffer = data.asShortBuffer();
               data.order(old_order);

               short[] short_array =  new short[short_buffer.remaining()];
               short_buffer.get(short_array);

               libPublisher.SmartPublisherOnPCMShortArray(publisherHandle, short_array, 0, short_array.length, sampleRate, channel, per_channel_sample_number);
               */

            }
        }
    }
}