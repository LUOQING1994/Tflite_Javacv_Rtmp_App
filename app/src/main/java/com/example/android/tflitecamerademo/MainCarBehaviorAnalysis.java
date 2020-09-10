package com.example.android.tflitecamerademo;

import android.graphics.Bitmap;
import android.util.Log;

import com.alibaba.fastjson.JSON;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Properties;

public class MainCarBehaviorAnalysis {

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
    private String textToShow = "";
    private int tmp_state_change_number = 0; // 记录运输变装载 装载变运输的次数
    private Properties props = null;
    private ImageClassifier classifier;
    private CameraActivity activity;
    private long startTime = 0;
    private long endTime = 0;
    private long simMidTime = 0;
    private boolean count_start_time_flag = true;
    private boolean count_end_time_flag = true;
    private long hullStartTime = 0;  // 凸包不为0的时间
    private long hullEndTime = 0;  // 凸包为0的时间
    private long hullMidTime = 0;  // 凸包为0的持续时间
    private boolean hull_start_time_flag = true;
    private boolean hull_end_time_flag = true;
    private long speedStartTime = 0;  // 速度不为0的时间
    private long speedEndTime = 0;  // 速度为0的时间
    private long speedMidTime = 0;  // 速度为0的持续时间
    private boolean speed_start_time_flag = true;
    private boolean speed_end_time_flag = true;
    private int current_speed = 0;  // 当前速度
    private int current_angle = 0;  // 当前角度
    public MatNumberUtils carBehaviorAnalysis(Bitmap bitmap_image, CameraActivity activitys,
                                              ImageClassifier classifiers, int tmp_speed, int tmp_angle) {
        if (activitys == null || props == null || classifier == null){
            activity = activitys;
            props = activity.props;
            classifier = classifiers;
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

        Integer model_result_index = classifier.CAR_CATEGORY;
        double model_result_prob = classifier.CAR_CATEGORY_PROBABILITY;
//        Log.i("opencv结果", "==========================");
//        Log.i("opencv结果", "tmp_car_state:" + tmp_car_state + " last_car_state： " + last_car_state + " tmp_last_car_state: " + tmp_last_car_state + " image_sim_number: " + image_sim_number );
        Log.i("opencv结果", "tmp_car_state:" + tmp_car_state + " tmp_car_load: " + tmp_car_load);
        Log.i("模型结果", model_result_index + " ： " + model_result_prob );
        // 启用模型的检测结果是为了防止在夜晚opencv算法识别错误的情况
        if (tmp_car_state == 0 && model_result_index != 5 && model_result_index != 6
                && 1 > model_result_prob && model_result_prob > 0.85
                && speedMidTime < 10 && hullMidTime > 10
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
        }
        if (model_result_index == 6 &&  model_result_prob > 0.9
                && now_image_hull < 10 ){
            //  当模型识别结果为幕布且概率较高时 强制设置为运输（预防在倾倒前 打开幕布时 避免识别成装载）
            tmp_car_state = 0;
            tmp_car_load = tmp_car_state;
            tmp_state_change_number = 0;
        }
        else if (model_result_index == 5  && model_result_prob > 0.9
                && tmp_angle < 15 && now_image_hull < 25){
            //  当模型识别结果为空且概率较高时 强制设置为运输（预防在倾倒后 由于挡板晃动 导致识别成装载）
            tmp_car_state = 0;
            tmp_car_load = tmp_car_state;
        }
        else {
            // 判断是否出现 装载变运输 运输变装载的情况 当前一个模型类别为篷布时 说明此刻是倾倒前的打开篷布操作 不可视为装载
            if ((tmp_car_load + tmp_car_state) == -1 && last_car_category != 6){
                tmp_state_change_number = Math.min(tmp_state_change_number + 1, 4);
            }
            if (tmp_car_state != 1) {
                if (tmp_state_change_number == 4){
                    tmp_car_state = -1;   // 运输转装载 装载转运输次数达到4次 强制设置为装载
                    tmp_car_load = tmp_car_state;
                } else {
                    tmp_car_load = tmp_car_state;
                    tmp_car_state = tmp_last_car_state;   // 沿用之前的状态 ================
                }
            } else {
                // 倾倒出现时 统计状态变化的值需要重置tmp_state_change_number
                tmp_state_change_number = 0;
                tmp_car_load = tmp_car_state;
            }
        }
        Log.i("第一次结果", "tmp_car_state : " + tmp_car_state + " tmp_state_change_number： " + tmp_state_change_number  + " now_image_hull: " + now_image_hull);
        Log.i("第二次结果", simMidTime + " ： " + hullMidTime + " : " + speedMidTime);

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
        // 根据当前车辆状态 上传图片数据
        imageOptionFrame(bitmap_image);

        textToShow = "检测结果: " + openCVTools.result_text.get(tmp_car_state) + " \n " +
                "凸包数量: " + now_image_hull + " \n " +
                "相似度 : " + image_sim_number + " \n " +
                "当前角度：" + tmp_angle + " \n " +
                "当前速度：" + tmp_speed + " \n " + class_result;
        matNumberUtils.setToShow(textToShow);
        matNumberUtils.setIamge(tmp_now_image);
        tmp_now_image.release();
        return matNumberUtils;
    }

    public MatNumberUtils mainCarBehaviorAnalysis(Mat tmp_image, int tmp_angle) {
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
            tmp_cut_image.release();
        }
        return matNumberUtils;
    }

    /**
     * 模型类别检测
     */
    public String CarModelAnalysis(Mat image) {
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
    private long stateStartTime = 0;  // 车辆状态为0的时间
    private long stateEndTime = 0;  // 车辆状态为0的时间
    private long stateMidTime = 0;  // 车辆状态为0的时间
    private boolean state_start_time_flag = true;
    private boolean state_end_time_flag = false;
    private long unCloseStartTime = 0;  // 车辆未遮蔽的时间
    private long unCloseEndTime = 0;  // 车辆未遮蔽的时间
    private long unCloseMidTime = 0;  // 车辆未遮蔽的时间
    private boolean unClose_start_time_flag = true;
    private boolean unClose_end_time_flag = false;
    private boolean is_upOneFlag = false; // 是否可以上传倾倒或者装载的图片
    private boolean is_upUnCloseFlag = false; // 是否可以上传倾未覆盖的图片
    private int unCloseNumber = 0; // 记录未覆盖的图片数量

    public void imageOptionFrame(Bitmap frame_data){
        Log.i("结果", "stateMidTime ： " + stateMidTime + " unCloseMidTime: " + unCloseMidTime);
        if ( stateMidTime < Integer.parseInt(props.getProperty("state_time_through")) ){
            if ( image_sim_number != Integer.parseInt(props.getProperty("image_sim_number")) ){
                if( tmp_car_state == -1 ){
                    filesOption("/sdcard/android.example.com.tflitecamerademo/up_load/", Integer.parseInt(props.getProperty("up_image_max_number")),frame_data);
                    is_upOneFlag = true;
                } else if ( tmp_car_state == 1){
                    filesOption("/sdcard/android.example.com.tflitecamerademo/up_dump/",  Integer.parseInt(props.getProperty("up_image_max_number")), frame_data);
                    is_upOneFlag = true;
                }
            }

        } else {
            if( is_upOneFlag ){
                Log.i("上传图片", "开始上传图片。。。。。。。。。。。");
                // 当获得服务端上传成功信号时 删除本地图片和txt文件
                // TODO
                is_upOneFlag = false;
                is_upUnCloseFlag = false;
            }
            // 开始对幕布是否关闭进行判定
            countUnCloseTime(); // 统计幕布未遮蔽的时间 若大于设定的时间阈值 则开始上传图片
            //  未关闭时间较长、角度小于10度、速度持续时间大于10秒（为了测试暂时取消速度的条件）
            if (unCloseMidTime > Integer.parseInt(props.getProperty("unClose_max_time"))
                    && current_angle < 10 && !is_upUnCloseFlag
//                    && current_speed > 10
            )
            {
                unCloseNumber = Math.min(unCloseNumber++, Integer.parseInt(props.getProperty("up_unClose_max_number")));
                Log.i("保存的图片数", unCloseNumber + "  --------------------------------- " + unCloseNumber++);
                if(unCloseNumber >= Integer.parseInt(props.getProperty("up_unClose_max_number"))){
                    Log.i("幕布未关闭", "开始进行图片上传操作。。。。。。。。。。。");
                    // 上传完毕后 不再进入该循环
                    is_upUnCloseFlag = true;
                    unCloseNumber = 0;
                } else {
                    Log.i("幕布未关闭", "开始收集照片。。。。。。。。。。。");
                    filesOption("/sdcard/android.example.com.tflitecamerademo/un_close/",  Integer.parseInt(props.getProperty("up_unClose_max_number")), frame_data);
                }
            }
        }
    }
    /**
     *  计算运输的持续时间
     */
    public void countTransportTime(){
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

        // 当时差超过1分钟时 强制使得stateMidTime保持在60
        if (stateMidTime > 58 && state_end_time_flag){
            stateMidTime = 60; // 暂时使用秒
        } else {
            long diff = stateEndTime - stateStartTime;
            long day = diff / (24 * 60 * 60 * 1000);
            long hour = (diff / (60 * 60 * 1000) - day * 24);
            long min = ((diff / (60 * 1000)) - day * 24 * 60 - hour * 60);
            long sec = (diff/1000-day*24*60*60-hour*60*60-min*60);
            stateMidTime = sec;
        }
        // 未遮蔽状态 只能在运输时 计算
        if (stateMidTime == 0) {
            unCloseMidTime = 0;
        }
    }
    /**
     *  计算幕布未关闭的持续时间
     */
    public void countUnCloseTime(){
        int tmp_unClose_flag;
        if ( classifier.CAR_CATEGORY != 5 && classifier.CAR_CATEGORY != 6) {
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
            long sec = (diff/1000-day*24*60*60-hour*60*60-min*60);
            unCloseMidTime = sec;
        }
        Log.i("统计未遮蔽时间", "tmp_unClose_flag : " + tmp_unClose_flag + ": " + classifier.CAR_CATEGORY  + " : " + classifier.CAR_CATEGORY + ": " + classifier.CAR_CATEGORY_PROBABILITY);
    }
    /**
     *  判断文件夹中文件数 并存储文件
     */
    public void filesOption( String path, int maxFileNumber, Bitmap frame_data){
        Log.d("photoPath -->> ", "存储开始======================   ");
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
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        // 获取需要上传服务器的数据
        getBaseInfoDta();
        // 保存props中的数据到本地txt文件中
        try {
            File file = new File(dir1 + "/data.txt");
            if(!file.exists()) {
                file.createNewFile(); // 创建新文件,有同名的文件的话直接覆盖
            }
            FileOutputStream fos = new FileOutputStream(file,true);
            OutputStreamWriter osw = new OutputStreamWriter(fos);
            BufferedWriter bw = new BufferedWriter(osw);
            String propsString = JSON.toJSONString(props);
            bw.write(propsString);
            bw.newLine();
            bw.flush();
            bw.close();
            osw.close();
            fos.close();
        }catch (FileNotFoundException e1) {
            e1.printStackTrace();
        } catch (IOException e2) {
            e2.printStackTrace();
        }
    }

    /**
     * 获取需要上传服务器的基本信息数据
     */
    public void getBaseInfoDta(){
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

    }
    /**
     *  计算相似度持续时间
     */
    public void countSimTime(){
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
            long sec = (diff/1000-day*24*60*60-hour*60*60-min*60);
            simMidTime = sec;
        }
    }
    /**
     *  计算速度的持续时间
     */
    public void countSpeedTime(){
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
            long sec = (diff/1000-day*24*60*60-hour*60*60-min*60);
            speedMidTime = sec;
        }
    }
    /**
     *  计算凸包的持续时间
     */
    public void countHullTime(){
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
            long sec = (diff/1000-day*24*60*60-hour*60*60-min*60);
            hullMidTime = sec;
        }
    }
}
