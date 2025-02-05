package com.example.android.tflitecamerademo;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.alibaba.fastjson.JSON;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;

class MainCarBehaviorAnalysis {

    private CarBehaviorAnalysisByOpenCv carBehaviorAnalysisByOpenCv = new CarBehaviorAnalysisByOpenCv();
    private OpenCVTools openCVTools = new OpenCVTools();
    private MatNumberUtils matNumberUtils = new MatNumberUtils();
    private Mat last_image = new Mat();  // 上一时刻的图片
    private Mat tmp_last_image = new Mat(); // 记录当前时刻图片的中间状态

    // start ======================  OpenCV为主的算法需要的参数 =====================
    private int timeF_switch_bg = 10;  // 当提取三张图片后 再替换对比底图

    // 数据转换的临时变量
    private Integer tmp_car_state = 0; // 以霍夫直线为主 所得到的车辆行为识别结果
    private Integer tmp_car_load = 0;  // 记录车辆转载变运输或者运输变装载的中间态
    private Integer image_sim_number = 10;  // 前后两帧相似度持续大于image_sim_through的上限
    private Integer last_car_state = 0;  // 记录车辆上一时刻状态
    private Integer tmp_last_car_state = 0;  // 记录车辆上一时刻中间状态
    private Integer now_image_hull = 0;  // 当前检测区域的凸包数量

    private int last_car_category = 6; // 默认上一时刻车载类别为篷布
    private int tmp_car_category = 6; // 记录车载类别的中间状态
    private int tmp_state_change_number = 0; // 记录运输变装载 装载变运输的次数
    private Properties props = null;
    private ImageClassifier classifier;
    private int current_speed = 0;  // 当前速度
    private int current_angle = 0;  // 当前角度
    MatNumberUtils carBehaviorAnalysis(Bitmap bitmap_image, CameraActivity activitys,
                                       ImageClassifier classifiers, int tmp_speed, int tmp_angle) {
        if (activitys == null || props == null || classifier == null){
            props = activitys.props;
            classifier = classifiers;
            // 开启检测未上传成功的图片线程
            startUpImageThread();
        }
        current_speed = tmp_speed;
        current_angle = tmp_angle;
        Mat tmp_now_image = new Mat();
        Utils.bitmapToMat(bitmap_image, tmp_now_image);

        // OpenCv算法检测
        matNumberUtils = mainCarBehaviorAnalysis(tmp_now_image, tmp_angle);
        tmp_car_state = matNumberUtils.getNumber();
        // 分类模型检测
        String class_result = CarModelAnalysis(tmp_now_image);
        // 由于全局使用了同一个matNumberUtils对象 所以 即使没有得到检测结果 我们得到的便是上一时刻的车辆状态

        int model_result_index = classifier.CAR_CATEGORY;
        double model_result_prob = classifier.CAR_CATEGORY_PROBABILITY;
//        Log.i("opencv结果", "==========================");
//        Log.i("opencv结果", "tmp_car_state:" + tmp_car_state + " last_car_state： " + last_car_state + " tmp_last_car_state: " + tmp_last_car_state + " image_sim_number: " + image_sim_number );
//        Log.i("opencv结果", "tmp_car_state:" + tmp_car_state + " tmp_car_load: " + tmp_car_load);
//        Log.i("模型结果", model_result_index + " ： " + model_result_prob );
        // 启用模型的检测结果是为了防止在夜晚opencv算法识别错误的情况
        if (tmp_car_state == 0 && model_result_index != 5 && model_result_index != 6
                && model_result_prob > 0.85 && speedMidTime < 10 && hullMidTime > 10
                && image_sim_number < Integer.parseInt(props.getProperty("image_sim_number"))) {
            // 进入该判断语句后 说明当前启用了模型的检测结果
            // 所以 需要避免使用speedMidTime、hullMidTime、simMidTime 判断何时转换状态为运输
            simMidTime = 0;
            hullMidTime = 0;
            speedMidTime = 0;
            if ( tmp_angle < 15){
                // 说明模型已经识别到了货物 此时 强制设置货车状态为装载
                tmp_car_state = -1;
            } else {
                // 说明模型已经识别到了货物 此时 强制设置货车状态为倾倒
                tmp_car_state = 1;
            }
        }else if (model_result_index == 6 &&  model_result_prob > 0.9
                && now_image_hull < 10 ){
            //  当模型识别结果为幕布且概率较高时 强制设置为运输（预防在倾倒前 打开幕布时 避免识别成装载）
            tmp_car_state = 0;
            tmp_car_load = tmp_car_state;
            tmp_state_change_number = 0;
        }
        else if (model_result_index == 5  && model_result_prob > 0.9
                && tmp_angle < 15){
            //  当模型识别结果为空且概率较高时 强制设置为运输（预防在倾倒后 由于挡板晃动 导致识别成装载）
            if (tmp_last_car_state == 1){
                if (hullMidTime > 5){
                    tmp_car_state = 0;
                } else {
                    tmp_car_state = 1;
                }
            }
            tmp_car_load = tmp_car_state;
        }
        // 判断是否出现 装载变运输 运输变装载的情况 当前一个模型类别为篷布时 说明此刻是倾倒前的打开篷布操作 不可视为装载
        if ((tmp_car_load + tmp_car_state) == -1 && last_car_category != 6){
            tmp_state_change_number = Math.min(tmp_state_change_number + 1, 4);
        }
        if (tmp_car_state != 1) {
            if (tmp_state_change_number == 4){
                tmp_car_state = -1;   // 运输转装载 装载转运输次数达到4次 强制设置为装载
                tmp_car_load = tmp_car_state;
            } else if (tmp_state_change_number == 0) {
                tmp_car_load = tmp_car_state;
                tmp_car_state = 0;
            } else {
                tmp_car_load = tmp_car_state;
                tmp_car_state = 0;   // 当tmp_state_change_number小于4 且检测状态为-1时 强制设置为运输
            }
        } else {
            // 倾倒出现时 统计状态变化的值需要重置tmp_state_change_number
            tmp_state_change_number = 0;
            tmp_car_load = tmp_car_state;
        }
//        Log.i("第一次结果", "tmp_car_state : " + tmp_car_state + " tmp_state_change_number： " + tmp_state_change_number  + " now_image_hull: " + now_image_hull);
//        Log.i("第二次结果", simMidTime + " ： " + hullMidTime + " : " + speedMidTime);

        //    ==========  利用speedMidTime、hullMidTime、simMidTime 判断何时转换状态为运输     ===========================
        if ((simMidTime > Integer.parseInt(props.getProperty("sim_time_through"))
                || hullMidTime > Integer.parseInt(props.getProperty("hull_time_through"))
                || speedMidTime > Integer.parseInt(props.getProperty("speed_time_through")))
                && tmp_angle < 15){
            tmp_car_state = 0;
            tmp_car_load = tmp_car_state;
            tmp_state_change_number = 0;
        }
//        Log.i("第三次结果", tmp_car_state + " ： " + last_car_state + " : " + tmp_last_car_state);
        //   ===========  更新last_car_state的状态     ======================
        if( tmp_car_state != tmp_last_car_state){
            last_car_state = tmp_last_car_state;
            tmp_last_car_state = tmp_car_state;
        }
//        Log.i("第四次结果", "tmp_car_state :" + tmp_car_state + " last_car_state： " + last_car_state + " tmp_last_car_state: " + tmp_last_car_state + " tmp_state_change_number: " + tmp_state_change_number);

        // 记录车辆当前状态为运输态的持续时间
        countTransportTime();
        // 记录车辆当前状态为装载态的持续时间
        countLoadTime();
        // 根据当前车辆状态 上传图片数据
        imageOptionFrame(bitmap_image);

        String textToShow = "检测结果: " + openCVTools.result_text.get(tmp_car_state) + " \n " +
                "凸包数量: " + now_image_hull + " \n " +
                "相似度 : " + image_sim_number + " \n " +
                "当前角度：" + tmp_angle + " \n " +
                "当前速度：" + tmp_speed + " \n " + class_result;
        matNumberUtils.setToShow(textToShow);
        matNumberUtils.setIamge(tmp_now_image);
        tmp_now_image.release();
        return matNumberUtils;
    }

    private MatNumberUtils mainCarBehaviorAnalysis(Mat tmp_image, int tmp_angle) {
        // 基础的图像处理
        Mat tmp_cut_image = openCVTools.deal_flag(tmp_image);
        // 初始化基础参数
        if (last_image.cols() == 0) {
            // 默认为当前货物类别
            last_car_category = classifier.CAR_CATEGORY;
            tmp_car_category = classifier.CAR_CATEGORY;
            last_image = tmp_cut_image;
            tmp_last_image = tmp_cut_image;
        }
        // 计算前后两帧的相似度
        Integer image_sim = openCVTools.split_blok_box_sim(last_image, tmp_cut_image);
        Integer image_sim_through = Integer.parseInt(props.getProperty("image_sim_through"));
        if (image_sim > image_sim_through) {
            image_sim_number = Math.min(image_sim_number + 1, Integer.parseInt(props.getProperty("image_sim_number")));
        } else {
            image_sim_number = Math.max(image_sim_number - 1, 0);
        }
        // 凸包检测
        matNumberUtils = openCVTools.other_contours_Hull(tmp_cut_image, tmp_image);
        // 获取凸包数
        now_image_hull = matNumberUtils.getNumber();
        // 计算相似度为10的持续时间
        countSimTime();
        // 计算速度大于阈值的持续时间
        countSpeedTime();
        // 计算凸包大于阈值的持续时间
        countHullTime();
        // 结合 陀螺仪 凸包检测 进行车辆行为分析
        tmp_car_state = carBehaviorAnalysisByOpenCv.carBehaviorAnalysisByHull(simMidTime,now_image_hull,speedMidTime, tmp_angle, props);
        matNumberUtils.setNumber(tmp_car_state);

        // 相识度对比底片替换 使得last_image与flag相差一定帧数
        if (timeF_switch_bg <= 0) {
            timeF_switch_bg = Integer.parseInt(props.getProperty("timeF_switch_bg"));
            last_image = tmp_last_image;
            tmp_last_image = tmp_cut_image;
        } else {
            timeF_switch_bg = timeF_switch_bg - 1;
        }
        return matNumberUtils;
    }

    /**
     * 模型类别检测
     */
    private String CarModelAnalysis(Mat image) {
        Mat tmp_model_image = new Mat();
        // 模型检测
        Imgproc.resize(image, tmp_model_image, new Size(ImageClassifier.DIM_IMG_SIZE_X, ImageClassifier.DIM_IMG_SIZE_Y));
        Bitmap tmp_model_bitmap = Bitmap.createBitmap(tmp_model_image.cols(), tmp_model_image.rows(),
                Bitmap.Config.ARGB_4444);
        Utils.matToBitmap(tmp_model_image, tmp_model_bitmap);
        String model_result = classifier.classifyFrame(tmp_model_bitmap);
        // 记录前后时刻 模型记录的类别
        if (tmp_car_category != classifier.CAR_CATEGORY) {
            last_car_category = tmp_car_category;
            tmp_car_category = classifier.CAR_CATEGORY;
        }
        tmp_model_bitmap.recycle();
        tmp_model_image.release();
        return model_result;
    }


    /**
     *  用于处理图片上传功能
     */
    private boolean is_upOneFlag = false; // 是否可以上传倾倒或者装载的图片
    private int unCloseNumber = 0; // 记录未覆盖的图片数量
    private String upImagePath = ""; // 图片上传的地址

    @SuppressLint("SdCardPath")
    private void imageOptionFrame(Bitmap frame_data){
//        Log.i("结果", "stateMidTime ： " + stateMidTime + " unCloseMidTime: " + unCloseMidTime);
        //  当运输状态持续了2分钟后 才进行上传倾倒或者装载的图片
        if ( stateMidTime < Integer.parseInt(props.getProperty("state_time_through")) ){
            if ( image_sim_number < 1 ){
                // 状态为运输 且持续时间大于10秒 才开始收集信息
                if( tmp_car_state == -1 && loadMidTime > 10){
                    upImagePath = "/sdcard/android.example.com.tflitecamerademo/data/up_load/";
                    filesOption(upImagePath, Integer.parseInt(props.getProperty("save_image_max_number")),frame_data);
                    is_upOneFlag = true;
                } else if ( tmp_car_state == 1){
                    upImagePath = "/sdcard/android.example.com.tflitecamerademo/data/up_dump/";
                    filesOption(upImagePath,  Integer.parseInt(props.getProperty("save_image_max_number")), frame_data);
                    is_upOneFlag = true;
                }
                // 关闭upImageRunable线程
                upImageFalge = false;
            }
            unCloseNumber = 0;
        } else {
            if( is_upOneFlag ){
                Log.i("上传图片", "开始上传图片。。。。。。。。。。。");
                upImageToService(upImagePath);
                // 开始启用upImageRunable线程
                upImageFalge = true;
                is_upOneFlag = false;
            }
            // 开始对幕布是否关闭进行判定
            countUnCloseTime(); // 统计幕布未遮蔽的时间 若大于设定的时间阈值 则开始上传图片
            //  未关闭时间较长、角度小于10度、速度持续时间大于10秒（为了测试暂时取消速度的条件）
            if (unCloseMidTime > Integer.parseInt(props.getProperty("unClose_max_time")))
            {
                //  发现未遮蔽 立即上传
                unCloseNumber = Math.min(unCloseNumber + 1, 10);
                if(unCloseNumber < Integer.parseInt(props.getProperty("save_unClose_max_number"))){
                    Log.i("幕布未关闭", "开始收集照片。。。。。。。。。。。");
                    upImagePath = "/sdcard/android.example.com.tflitecamerademo/data/un_close/";
                    filesOption(upImagePath,  Integer.parseInt(props.getProperty("save_unClose_max_number")), frame_data);
                    Log.i("幕布未关闭", "开始进行图片上传操作。。。。。。。。。。。");
                    upImageToService(upImagePath);
                    // 开始启用upImageRunable线程
                    upImageFalge = true;
                }
            }
        }
    }


    //  模拟各种突发情况进程测试
    //  1,存储过程中突然断电
    //  2,删除文件时 突然断电等








    /**
     *  图片上传操作
     */
    // 利用hashMap存储txt中的数据 照片名称为key，prop对象为value
    private HashMap<String,Properties> txtMap = new HashMap<>();
    private void upImageToService(String imagePath){
        // 不让upImageRunable线程进行上传操作
        upImageFalge = false;
        File dir1 = new File(imagePath);
        if (!dir1.exists()) {
            Log.i("图片上传操作", "没有对应的文件");
            return;
        }
        for (File file : dir1.listFiles()){
            if (file.isFile() && file.getName().split("\\.")[1].equals("txt")){
                try {
                    InputStreamReader inputReader = new InputStreamReader(new FileInputStream(file),"UTF-8");
                    BufferedReader bf = new BufferedReader(inputReader);
                    // 按行读取字符串
                    String str;
                    while ((str = bf.readLine()) != null) {
                        Properties tmpTextProp = JSON.parseObject(str,Properties.class);
                        String[] tmp_array = tmpTextProp.getProperty("current_image_url").split("/");
                        String tmp_key = tmp_array[tmp_array.length - 1];
                        txtMap.put(tmp_key, tmpTextProp);
                    }
                    bf.close();
                    inputReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        int tmp_interval = dir1.listFiles().length / Integer.parseInt(props.getProperty("up_image_max_number"));
        tmp_interval = tmp_interval == 0 ? 1 : tmp_interval;
        int tmp_up_number = 0;   // 记录已经上传的图片数量
        for (int i = 0;i < dir1.listFiles().length
                && tmp_up_number < Integer.parseInt(props.getProperty("up_image_max_number"));){
            File tmp_file = dir1.listFiles()[i];
            // 通过文件名称 得到产生图片的时间戳 用于命名上传失败后的存储文件
            String[] strArray = tmp_file.getName().split("\\.");
            // 跳过fail文件夹、txt文件、hashMap中没有对应kay的图片
            if ( tmp_file.isDirectory() || strArray[1].equals("txt") || txtMap.get(tmp_file.getName()) == null){
                i++;
                continue;
            }
            // 通过文件名称 得到产生图片的时间戳 用于命名上传失败后的存储文件
            long tmp_time = Long.parseLong(strArray[0]);
            Date date = new Date(tmp_time);
            @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH");
            Log.i("图片上传操作", "移动数据到另外的文件夹 并使用线程开始轮询上传操作");
            // 直接把图片转移到fail文件夹中 避开因连接服务器超时而带来的主线程卡顿
            filesRemoveOtherDir(tmp_file,imagePath + "/fail/" + sdf.format(date), txtMap);
            tmp_up_number++;
            i = tmp_interval + i;
        }
        Log.i("图片上传操作", "开始删除本地数据。。。。。 ");
        File[] files = dir1.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()){
                    // 删除
                    file.delete();
                }
            }
        }
        txtMap.clear(); // 清理hashMap中的数据
        Log.i("图片上传操作", "删除本地数据。。。。。 ");
    }

    /**
     *file  图片文件
     *requesurl  服务器后台
     */
    public  String uploadFile(File file, HashMap<String, Properties> txtMap){
        String result = "false";
        try {
            URL url = new URL(props.getProperty("up_service_url"));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");//请求方式 默认get请求
            conn.setConnectTimeout(5000);
            conn.setDoInput(true);//允许输入流
            conn.setDoOutput(true);//允许输出流
            conn.setUseCaches(false);//不允许使用缓存
            // 设置编码格式
            conn.setRequestProperty("Charset", "UTF-8");
            conn.setRequestProperty("fileName", file.getName());
            // 传输txt中对应的图片基本信息
            conn.setRequestProperty("fileBaseInfo", String.valueOf(txtMap.get(file.getName())));
            if(file != null){
                Log.i("开始上传", "==== 获得数据 ====");
                DataOutputStream dos = new DataOutputStream(conn.getOutputStream());//getoutputStream会隐式的调用connect()
                InputStream is = new FileInputStream(file);
                byte[] bytes = new byte[2048];
                int len = 0;
                while((len = is.read(bytes)) != -1){
                    dos.write(bytes,0,len);//将图片转为二进制输出
                }
                is.close();
                dos.close();

                int res = conn.getResponseCode();//获取响应码
                if(res == 200){				//200表示响应后台成功！
                    InputStream input = conn.getInputStream();//获取流
                    Log.i("开始上传", "返回数据");
                    int ss;
                    byte[] buffer = new byte[1024];
                    StringBuilder builder = new StringBuilder();
                    while((ss = input.read(buffer))!= -1){
                        builder.append(new String(buffer,0,ss,"UTF-8"));//获取后台传递过来的数据
                    }
                    result = builder.toString();
                }
            }
            conn.disconnect();
        } catch (IOException e) {
            e.printStackTrace();
            result = "false";
        }
        return result;
    }

    /**
     * 创建一个线程 用于轮询未上传成功的照片数据
     */
    private static final String HANDLE_THREAD_NAME = "upImageThread";
    private Handler upImageHandler;
    private final Object lock = new Object();
    private boolean upImageFalge = false;

    private void startUpImageThread() {
        /** Starts a background thread and its {@link Handler}. */
        HandlerThread upImageThread = new HandlerThread(HANDLE_THREAD_NAME);
        upImageThread.start();
        upImageHandler = new Handler(upImageThread.getLooper());
        synchronized (lock) {
            upImageFalge = true;
        }
        upImageHandler.post(upImageRunable);
    }
    private Runnable upImageRunable =
            new Runnable() {
                @Override
                public void run() {
                    synchronized (lock) {
                        if (upImageFalge) {
                            cheackUpImage();
                        }
                    }
                    upImageHandler.post(upImageRunable);
                }
            };

    /**
     * 用于轮询查看未上传成功的照片
     */
    @SuppressLint("SdCardPath")
    private void cheackUpImage(){
        upImageFalge = false;
        // 开启线程 检测是否有未上传的图片 并进行轮询

        // 1,分别检查三个文件夹中是否有数据 有的话尝试上传
        upImageToService("/sdcard/android.example.com.tflitecamerademo/data/un_close");
        upImageToService("/sdcard/android.example.com.tflitecamerademo/data/up_dump");
        upImageToService("/sdcard/android.example.com.tflitecamerademo/data/up_load");

        // 2,检查fail文件夹中是否有数据 有的话尝试上传
        int tmp_close = upAndDelDirImage("/sdcard/android.example.com.tflitecamerademo/data/un_close/fail");
        int tmp_dump = upAndDelDirImage("/sdcard/android.example.com.tflitecamerademo/data/up_dump/fail");
        int tmp_load = upAndDelDirImage("/sdcard/android.example.com.tflitecamerademo/data/up_load/fail");
        Log.i("图片上传操作", " 检查文件夹是否有没上传的原始数据。。。。。。 ");

        if (tmp_close == 0 && tmp_dump == 0 && tmp_load == 0) {
            Log.i("图片上传操作", " 停止使用线程进行上传操作。。。。。 ");
            upImageFalge = false;
        } else {
            Log.i("图片上传操作", " 启用线程进行上传图片。。。。。 ");
            upImageFalge = true;
            try {
                Thread.currentThread().sleep(5000);
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    }
    /**
     *  上传并删除fail中对应文件夹中的数据
     */
    public int upAndDelDirImage( String file_path){
        File tmp_check_file = new File(file_path);
        // 利用hashMap存储txt中的数据 照片名称为key，prop对象为value
        HashMap<String,Properties> failTxtMap = new HashMap<>();
        if (tmp_check_file.exists()){
            File[] files = tmp_check_file.listFiles();
            for (File file : files) {
                File[] sub_files = file.listFiles();
                // 找到txt文件 制作hashMap
                File tmp_txt_file = null;
                for ( File sub_file : sub_files){
                    if (sub_file.isFile() && sub_file.getName().split("\\.")[1].equals("txt")){
                        try {
                            tmp_txt_file = sub_file;
                            InputStreamReader inputReader = new InputStreamReader(new FileInputStream(sub_file), StandardCharsets.UTF_8);
                            BufferedReader bf = new BufferedReader(inputReader);
                            // 按行读取字符串
                            String str;
                            while ((str = bf.readLine()) != null) {
                                Properties tmpTextProp = JSON.parseObject(str,Properties.class);
                                String[] tmp_array = tmpTextProp.getProperty("current_image_url").split("/");
                                String tmp_key = tmp_array[tmp_array.length - 1];
                                failTxtMap.put(tmp_key, tmpTextProp);
                            }
                            bf.close();
                            inputReader.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                for ( File sub_file : sub_files){
                    if (sub_file.isFile() && !sub_file.getName().split("\\.")[1].equals("txt")){
                        String re_result = uploadFile(sub_file, failTxtMap);
                        Log.i("图片上传操作", "线程结果 " + re_result);
                        if (re_result.equals("ok")){
                            // 上传成功则删除对应照片
                            sub_file.delete();
                            failTxtMap.remove(sub_file.getName());
                        }
                    }
                }
                // 当只剩下一个文件时 只有可能是txt文件
                if (sub_files.length == 1){
                    tmp_txt_file.delete();
                }
                // 当前文件夹中还有图片 将不会执行删除
                file.delete();
            }
        }
        return tmp_check_file.listFiles() == null ? 0 : tmp_check_file.listFiles().length;
    }


    /**
     *  计算运输的持续时间
     */
    private long stateStartTime = 0;  // 车辆状态为0的时间
    private long stateEndTime = 0;  // 车辆状态为0的时间
    private long stateMidTime = 0;  // 车辆状态为0的时间
    private boolean state_start_time_flag = true;
    private boolean state_end_time_flag = false;
    private void countTransportTime(){
        if (tmp_car_state == 0 && state_start_time_flag) {
            stateStartTime = System.currentTimeMillis();
            state_start_time_flag = false;
            state_end_time_flag = true;
        } else if (tmp_car_state != 0 && state_end_time_flag){
            stateEndTime = System.currentTimeMillis();
            state_start_time_flag = true;
            state_end_time_flag = false;
        } else if (tmp_car_state != 0){
            stateStartTime = System.currentTimeMillis();
            stateEndTime =  System.currentTimeMillis();
        }

        if (state_end_time_flag) {    // 运输状态一直没变化
            stateEndTime =  System.currentTimeMillis();
        }

        // 当时差超过2分钟时 强制使得stateMidTime保持在2
        if (stateMidTime > 1 && state_end_time_flag){
            stateMidTime = 2; // 使用分钟
        } else {
            long diff = stateEndTime - stateStartTime;
            long day = diff / (24 * 60 * 60 * 1000);
            long hour = (diff / (60 * 60 * 1000) - day * 24);
            stateMidTime = ((diff / (60 * 1000)) - day * 24 * 60 - hour * 60);
        }
        // 未遮蔽状态 只能在运输时 计算
        if (stateMidTime < 2) {
            unCloseMidTime = 0;
        }
    }
    /**
     *  计算装载持续时间
     */
    private long loadStartTime = 0;  // 车辆状态为0的时间
    private long loadEndTime = 0;  // 车辆状态为0的时间
    private long loadMidTime = 0;  // 车辆状态为0的时间
    private boolean load_start_time_flag = true;
    private boolean load_end_time_flag = false;
    private void countLoadTime(){
        if (tmp_car_state == -1 && load_start_time_flag) {
            loadStartTime = System.currentTimeMillis();
            load_start_time_flag = false;
            load_end_time_flag = true;
        } else if (tmp_car_state != -1 && load_end_time_flag){
            loadEndTime = System.currentTimeMillis();
            load_start_time_flag = true;
            load_end_time_flag = false;
        } else if (tmp_car_state != -1){
            loadStartTime = System.currentTimeMillis();
            loadEndTime =  System.currentTimeMillis();
        }

        if (load_end_time_flag) {    // 运输状态一直没变化
            loadEndTime =  System.currentTimeMillis();
        }

        // 当时差超过1分钟时 强制使得loadMidTime保持在60
        if (loadMidTime > 58 && load_end_time_flag){
            loadMidTime = 60; // 暂时使用秒
        } else {
            long diff = loadEndTime - loadStartTime;
            long day = diff / (24 * 60 * 60 * 1000);
            long hour = (diff / (60 * 60 * 1000) - day * 24);
            long min = ((diff / (60 * 1000)) - day * 24 * 60 - hour * 60);
            loadMidTime = (diff/1000-day*24*60*60-hour*60*60-min*60);
        }
    }
    /**
     *  计算幕布未关闭的持续时间
     */
    private long unCloseStartTime = 0;  // 车辆未遮蔽的时间
    private long unCloseEndTime = 0;  // 车辆未遮蔽的时间
    private long unCloseMidTime = 0;  // 车辆未遮蔽的时间
    private boolean unClose_start_time_flag = true;
    private boolean unClose_end_time_flag = false;
    private void countUnCloseTime(){
        int tmp_unClose_flag;
        if ( classifier.CAR_CATEGORY != 5 && classifier.CAR_CATEGORY != 6
                && current_angle < 10
            //  TODO  && current_speed > 10
                 ) {
            tmp_unClose_flag = 1;
        } else {
            tmp_unClose_flag = 0;
        }
        if ( tmp_unClose_flag == 1 && unClose_start_time_flag) {
            unCloseStartTime = System.currentTimeMillis();
            unClose_start_time_flag = false;
            unClose_end_time_flag = true;
        } else if ( tmp_unClose_flag == 0 && unClose_end_time_flag){
            unCloseEndTime = System.currentTimeMillis();
            unClose_start_time_flag = true;
            unClose_end_time_flag = false;
        } else if (tmp_unClose_flag == 0){
            unCloseStartTime = System.currentTimeMillis();
            unCloseEndTime =  System.currentTimeMillis();
        }

        if (unClose_end_time_flag) {    // 运输状态一直没变化
            unCloseEndTime =  System.currentTimeMillis();
        }

        // 当时差超过1分钟时 强制使得unCloseMidTime保持在60
        if (unCloseMidTime > 58 && unClose_end_time_flag){
            unCloseMidTime = 60; // 暂时使用秒
        } else {
            long diff = unCloseEndTime - unCloseStartTime;
            long day = diff / (24 * 60 * 60 * 1000);
            long hour = (diff / (60 * 60 * 1000) - day * 24);
            long min = ((diff / (60 * 1000)) - day * 24 * 60 - hour * 60);
            unCloseMidTime = (diff/1000-day*24*60*60-hour*60*60-min*60);
        }
        Log.i("统计未遮蔽时间", "tmp_unClose_flag : " + tmp_unClose_flag + ": " + classifier.CAR_CATEGORY  + " : " + classifier.CAR_CATEGORY + ": " + classifier.CAR_CATEGORY_PROBABILITY);
    }
    /**
     *  判断文件夹中文件数 并存储文件
     */
    private void filesOption(String path, int maxFileNumber, Bitmap frame_data){
        Log.d("photoPath -->> ", "存储开始======================   ");
        // 不再上图片上传线程工作
        upImageFalge = false;
        File dir1 = new File(path);
        if (!dir1.exists()) {
            dir1.mkdirs();
        }
        //  当存储的图片数达到阈值时 不再存储
        if(dir1.listFiles().length >= maxFileNumber){
            return;
        }
        try {
            String fileName = System.currentTimeMillis() + ".jpg";
            FileOutputStream fos = new FileOutputStream(path + fileName);
            //  保存当前图片的路径
            props.setProperty("current_image_url",path + fileName);
            frame_data.compress(Bitmap.CompressFormat.JPEG, 80, fos);
            fos.flush();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        // 获取需要上传服务器的数据
        getBaseInfoDta();
        // 保存props中的数据到本地txt文件中
        try {
            Date date = new Date(System.currentTimeMillis());
            @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH");
            File file = new File(dir1 + "/"+sdf.format(date) + ".txt");
            if(!file.exists()) {
                file.createNewFile(); // 创建新文件,有同名的文件的话直接覆盖
            }
            String propsString = JSON.toJSONString(props);
            if (!propsString.equals("null")){
                FileOutputStream fos = new FileOutputStream(file,true);
                OutputStreamWriter osw = new OutputStreamWriter(fos);
                BufferedWriter bw = new BufferedWriter(osw);
                bw.write(propsString);
                bw.newLine();
                bw.flush();
                bw.close();
                osw.close();
                fos.close();
            }
        } catch (IOException e1) {
            e1.printStackTrace();
        }
    }

    /**
     *  移动文件到另一个文件夹中
     */
    private void filesRemoveOtherDir(File origin_file, String new_path, HashMap<String, Properties> txtMap){
        Log.d("photoPath -->> ", "开始移动文件======================   ");
        File dir1 = new File(new_path);
        if (!dir1.exists()) {
            dir1.mkdirs();
        }
        if (origin_file != null) {
            try {
                File copy_file = new File(new_path + "/" + origin_file.getName());
                // 移动图片
                FileChannel inputChannel = new FileInputStream(origin_file).getChannel();
                FileChannel outputChannel = new FileOutputStream(copy_file).getChannel();
                outputChannel.transferFrom(inputChannel, 0, inputChannel.size());

                // 移动文件
                Date date = new Date(Long.parseLong(origin_file.getName().split("\\.")[0]));
                @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH");
                File file = new File(new_path + "/"+ sdf.format(date) + ".txt");
                if(!file.exists()) {
                    file.createNewFile(); // 创建新文件,有同名的文件的话直接覆盖
                }
                FileOutputStream fos = new FileOutputStream(file,true);
                OutputStreamWriter osw = new OutputStreamWriter(fos);
                BufferedWriter bw = new BufferedWriter(osw);
                String propsString = JSON.toJSONString(txtMap.get(origin_file.getName()));
                txtMap.remove(origin_file.getName());
                bw.write(propsString);
                bw.newLine();
                bw.flush();
                bw.close();
                osw.close();
                fos.close();
                inputChannel.close();
                outputChannel.close();
            } catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    /**
     * 获取需要上传服务器的基本信息数据
     */
    private void getBaseInfoDta(){
        props.setProperty("current_state",openCVTools.result_text.get(tmp_car_state));
        props.setProperty("current_sim", String.valueOf(image_sim_number));
        props.setProperty("current_sim_time", String.valueOf(simMidTime));
        props.setProperty("current_hull", String.valueOf(now_image_hull));
        props.setProperty("current_hull_time", String.valueOf(hullMidTime));
        props.setProperty("current_model_result", String.valueOf(classifier.CAR_CATEGORY));
        props.setProperty("current_model_probably", String.valueOf(classifier.CAR_CATEGORY_PROBABILITY));
        props.setProperty("current_model_time", String.valueOf(classifier.CAR_CATEGORY_TIME));
        props.setProperty("last_state", String.valueOf(last_car_state));
        props.setProperty("last_model_result", String.valueOf(last_car_category));
        props.setProperty("current_Id", "川12312");
        props.setProperty("current_time", String.valueOf(System.currentTimeMillis()));

    }
    /**
     *  计算相似度持续时间
     */
    private long startTime = 0;
    private long endTime = 0;
    private long simMidTime = 0;
    private boolean count_start_time_flag = true;
    private boolean count_end_time_flag = true;
    private void countSimTime(){
        // 计算相似度的持续时间间隔
        if (image_sim_number == Integer.parseInt(props.getProperty("image_sim_number")) && count_start_time_flag){
            startTime =  System.currentTimeMillis();
            count_start_time_flag = false;
            count_end_time_flag = true;
        } else if (image_sim_number != Integer.parseInt(props.getProperty("image_sim_number")) && count_end_time_flag){
            // 记录画面一旦变化 记录变化时间
            endTime =  System.currentTimeMillis();
            count_end_time_flag = false;
            count_start_time_flag = true;
        } else if (image_sim_number != Integer.parseInt(props.getProperty("image_sim_number") ) && !count_end_time_flag){
            startTime = System.currentTimeMillis();
            endTime =  System.currentTimeMillis();
        }

        if (count_end_time_flag) {    // 相似度高 但一直没有出现画面变化
            endTime =  System.currentTimeMillis();
        }
        // 当时差超过1分钟时 强制使得simMidTime保持在60
        if (simMidTime > 58 && count_end_time_flag){
            simMidTime = 60; // 暂时使用秒
        } else {
            long diff = endTime - startTime;
            long day = diff / (24 * 60 * 60 * 1000);
            long hour = (diff / (60 * 60 * 1000) - day * 24);
            long min = ((diff / (60 * 1000)) - day * 24 * 60 - hour * 60);
            simMidTime = (diff/1000-day*24*60*60-hour*60*60-min*60);
        }
    }
    /**
     *  计算速度的持续时间
     */
    private long speedStartTime = 0;  // 速度不为0的时间
    private long speedEndTime = 0;  // 速度为0的时间
    private long speedMidTime = 0;  // 速度为0的持续时间
    private boolean speed_start_time_flag = true;
    private boolean speed_end_time_flag = true;
    private void countSpeedTime(){
        if (current_speed > Integer.parseInt(props.getProperty("hull_speed_thought")) && speed_start_time_flag) {
            speedStartTime = System.currentTimeMillis();
            speed_start_time_flag = false;
            speed_end_time_flag = true;
        } else if (current_speed < Integer.parseInt(props.getProperty("hull_speed_thought")) && speed_end_time_flag){
            speedEndTime = System.currentTimeMillis();
            speed_start_time_flag = true;
            speed_end_time_flag = false;
        } else if (current_speed < Integer.parseInt(props.getProperty("hull_speed_thought"))){
            speedStartTime = System.currentTimeMillis();
            speedEndTime =  System.currentTimeMillis();
        }
        if (speed_end_time_flag) {    // 凸包一直没变化
            speedEndTime =  System.currentTimeMillis();
        }
        // 当时差超过1分钟时 强制使得midspeedMidTime保持在60
        if (speedMidTime > 58 && speed_end_time_flag){
            speedMidTime = 60; // 暂时使用秒
        } else {
            long diff = speedEndTime - speedStartTime;
            long day = diff / (24 * 60 * 60 * 1000);
            long hour = (diff / (60 * 60 * 1000) - day * 24);
            long min = ((diff / (60 * 1000)) - day * 24 * 60 - hour * 60);
            speedMidTime = (diff/1000-day*24*60*60-hour*60*60-min*60);
        }
    }
    /**
     *  计算凸包的持续时间
     */
    private long hullStartTime = 0;  // 凸包不为0的时间
    private long hullEndTime = 0;  // 凸包为0的时间
    private long hullMidTime = 0;  // 凸包为0的持续时间
    private boolean hull_start_time_flag = true;
    private boolean hull_end_time_flag = true;
    private void countHullTime(){
        // 计算凸包小于10的持续时间间隔
        if (now_image_hull < 10 && hull_start_time_flag) {
            hullStartTime = System.currentTimeMillis();
            hull_start_time_flag = false;
            hull_end_time_flag = true;
        } else if (now_image_hull > 10 && hull_end_time_flag){
            hullEndTime = System.currentTimeMillis();
            hull_start_time_flag = true;
            hull_end_time_flag = false;
        } else if (now_image_hull > 10){
            hullStartTime = System.currentTimeMillis();
            hullEndTime =  System.currentTimeMillis();
        }

        if (hull_end_time_flag) {    // 凸包一直没变化
            hullEndTime =  System.currentTimeMillis();
        }

        // 当时差超过1分钟时 强制使得hullMidTime保持在60
        if (hullMidTime > 58 && hull_end_time_flag){
            hullMidTime = 60; // 暂时使用秒
        } else {
            long diff = hullEndTime - hullStartTime;
            long day = diff / (24 * 60 * 60 * 1000);
            long hour = (diff / (60 * 60 * 1000) - day * 24);
            long min = ((diff / (60 * 1000)) - day * 24 * 60 - hour * 60);
            hullMidTime = (diff/1000-day*24*60*60-hour*60*60-min*60);
        }
    }
}
