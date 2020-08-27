package com.example.android.tflitecamerademo;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.Properties;

public class MainCarBehaviorAnalysis {

    private CarBehaviorAnalysisByOpenCv carBehaviorAnalysisByOpenCv = new CarBehaviorAnalysisByOpenCv();
    private OpenCVTools openCVTools = new OpenCVTools();
    private MatNumberUtils matNumberUtils = new MatNumberUtils();
    Mat last_image = new Mat();  // 上一时刻的图片
    Mat tmp_last_image = new Mat(); // 记录当前时刻图片的中间状态

    // start ======================  OpenCV为主的算法需要的参数 =====================
    int timeF_switch_bg = 10;  // 当提取三张图片后 再替换对比底图

    // 数据转换的临时变量
    Integer tmp_car_state = 0; // 以霍夫直线为主 所得到的车辆行为识别结果

    Integer image_sim_number = 10;  // 前后两帧相似度持续大于image_sim_through的上限
    Integer last_car_state = 0;  // 记录车辆上一时刻状态
    Integer tmp_last_car_state = 0;  // 记录车辆上一时刻中间状态
    Integer now_image_hull = 0;  // 当前检测区域的凸包数量

    String model_result = ""; // 记录模型货物分类结果
    int last_car_category = 6; // 默认上一时刻车载类别为篷布
    int tmp_car_category = 6; // 记录车载类别的中间状态
    String textToShow = "";
    int tmp_speed = 0;
    int tmp_angle = 0;
    private int tmp_state_change_number = 0; // 记录运输变装载 装载变运输的次数
    private int tmp_dump_change_number = 0; // 记录运输变倾倒 倾倒变运输的次数
    private long diff_startTime = 0;
    private long diff_endTime = 0;
    private long midTime = 0;
    private boolean count_start_time_flag = true;
    private boolean count_end_time_flag = true;
    private long hullStartTime = 0;  // 凸包不为0的时间
    private long hullEndTime = 0;  // 凸包为0的时间
    private long midHullMidTime = 0;  // 凸包为0的持续时间
    boolean hull_start_time_flag = true;
    boolean hull_end_time_flag = true;
    Properties props = null;
    ImageClassifier classifier;
    CameraActivity activity;
    public MatNumberUtils carBehaviorAnalysis(Bitmap bitmap_image, CameraActivity activitys, ImageClassifier classifiers) {
        activity = activitys;
        props = activity.props;
        classifier = classifiers;
        Mat tmp_now_image = new Mat();
        Utils.bitmapToMat(bitmap_image, tmp_now_image);
        // ========================= 这里接入车载设备速度  ====== 暂时设置为0
        tmp_speed = (int)activity.currentSpeed;
        tmp_angle = (int)activity.currentAngle;
        // OpenCv算法检测
        matNumberUtils = mainCarBehaviorAnalysis(tmp_now_image, tmp_speed, tmp_angle);
        // 分类模型检测
        CarModelAnalysis(tmp_now_image);
        // todo     上传数据
        // 由于全局使用了同一个matNumberUtils对象 所以 即使没有得到检测结果 我们得到的便是上一时刻的车辆状态
        tmp_car_state = matNumberUtils.getNumber();
        Integer model_result_index = classifier.CAR_CATEGORY;
        double model_result_prob = classifier.CAR_CATEGORY_PROBABILITY;
        if (tmp_car_state == 0 && model_result_index != 5 && model_result_index != 6
                && model_result_prob > 0.85 && tmp_speed == 0 && image_sim_number != Integer.parseInt(props.getProperty("image_sim_number"))
                && tmp_angle < 8 && now_image_hull > 5) { // 加入凸包是为了防止模型识别出错
            // 说明模型已经识别到了货物 此时 强制设置货车状态为装载
            tmp_car_state = -1;
        } else if (last_car_state == 1 && tmp_car_state == -1) { // 上一时刻为倾倒 当前时刻为装载 强制设置成倾倒
            tmp_car_state = 1;
        }

        // 计算相似度的持续时间间隔
        if (image_sim_number == Integer.parseInt(props.getProperty("image_sim_number")) && count_start_time_flag) {
            diff_startTime = System.currentTimeMillis();
            count_start_time_flag = false;
            count_end_time_flag = true;
        } else if (image_sim_number != Integer.parseInt(props.getProperty("image_sim_number")) && count_end_time_flag) {
            // 记录画面一旦变化 记录变化时间
            diff_endTime = System.currentTimeMillis();
            count_end_time_flag = false;
            count_start_time_flag = true;
        }

        if (count_end_time_flag) {    // 相似度高 但一直没有出现画面变化
            diff_endTime = System.currentTimeMillis();
        }
        // 当时差超过1分钟时 强制使得midTime保持在60
        if (midTime > 58 && count_end_time_flag) {
            midTime = 60; // 暂时使用秒
            tmp_car_state = 0; // 当相似度持续时间大于阈值时 强制设置为运输
            tmp_state_change_number = 0;
            tmp_dump_change_number = 0;
        } else {
            long diff = diff_endTime - diff_startTime;
            long day = diff / (24 * 60 * 60 * 1000);
            long hour = (diff / (60 * 60 * 1000) - day * 24);
            long min = ((diff / (60 * 1000)) - day * 24 * 60 - hour * 60);
            long sec = (diff / 1000 - day * 24 * 60 * 60 - hour * 60 * 60 - min * 60);
            midTime = sec;
        }
        // 判断是否出现 装载变运输 运输变装载的情况
        if (tmp_last_car_state != tmp_car_state) {
            if ((tmp_last_car_state + tmp_car_state) == -1) {
                tmp_state_change_number = Math.min(tmp_state_change_number + 1, 3);
            }
            if ((tmp_last_car_state + tmp_car_state) == 1) {
                tmp_dump_change_number = Math.min(tmp_dump_change_number + 1, 2);
            }
        }
        if (tmp_dump_change_number == 0) {
            if (tmp_state_change_number == 3) {
                // 当车辆相似度较长时间没有改变 或者 速度较大时 设置为运输态
                if (midTime > 50 && count_end_time_flag || tmp_speed > Integer.parseInt(props.getProperty("hull_speed_thought"))) {
                    tmp_car_state = 0;
                    last_car_state = tmp_car_state;
                    tmp_state_change_number = 0;
                } else {
                    tmp_car_state = -1;   // 运输转装载 装载转运输次数达到3次 强制设置为装载
                    last_car_state = tmp_last_car_state;
                    tmp_last_car_state = tmp_car_state;
                }
            } else {
                tmp_last_car_state = tmp_car_state;
                tmp_car_state = last_car_state;
            }
        } else {
            if (tmp_dump_change_number <= 2 && tmp_angle > Integer.parseInt(props.getProperty("hull_angle_through"))
                    && image_sim_number != Integer.parseInt(props.getProperty("image_sim_number"))) {
                // 当倾倒变运输 或 运输变倾倒出现次数小于2时 设置为倾倒
                tmp_car_state = 1;
                last_car_state = tmp_last_car_state;
                tmp_last_car_state = tmp_car_state;
            } else {
                tmp_last_car_state = tmp_car_state;
                last_car_state = tmp_car_state;
            }
        }
        // 计算凸包小于5的持续时间间隔
        if (now_image_hull < 10 && hull_start_time_flag) {
            hullStartTime = System.currentTimeMillis();
            hull_start_time_flag = false;
            hull_end_time_flag = true;
        } else if (now_image_hull > 5 && hull_end_time_flag){
            hullEndTime = System.currentTimeMillis();
            hull_start_time_flag = true;
            hull_end_time_flag = false;
        }
        if (hull_end_time_flag) {    // 凸包一直没变化
            hullEndTime =  System.currentTimeMillis();
        }

        // 当时差超过1分钟时 强制使得midHullMidTime保持在60
        if (midHullMidTime > 58 && hull_end_time_flag){
            midHullMidTime = 60; // 暂时使用秒
        } else {
            long diff = hullEndTime - hullStartTime;
            long day = diff / (24 * 60 * 60 * 1000);
            long hour = (diff / (60 * 60 * 1000) - day * 24);
            long min = ((diff / (60 * 1000)) - day * 24 * 60 - hour * 60);
            long sec = (diff/1000-day*24*60*60-hour*60*60-min*60);
            midHullMidTime = sec;
        }
        // 当凸包为0的状态持续60秒 则强制设置为运输
        if (midHullMidTime > 59 && tmp_angle <Integer.parseInt(props.getProperty("hull_angle_through"))
                && image_sim_number == Integer.parseInt(props.getProperty("image_sim_number")) ){
            tmp_car_state = 0;
            last_car_state = tmp_car_state;
        }
//        Log.i("时间", midTime + " :" + midHullMidTime + " : " + tmp_state_change_number);
//        Log.i("tmp_dump_change_number", tmp_dump_change_number + " : " + tmp_car_state);


        textToShow = "检测结果: " + openCVTools.result_text.get(tmp_car_state) + " \n " +
                "凸包数量: " + now_image_hull + " \n " +
                "相似度 : " + image_sim_number + " \n " +
                "当前角度：" + tmp_angle + " \n " +
                "当前速度：" + tmp_speed + " \n " + model_result;
        matNumberUtils.setToShow(textToShow);
        getBaseInfoDta();  // 获取需要上传服务器的数据
        return matNumberUtils;
    }

    public MatNumberUtils mainCarBehaviorAnalysis(Mat tmp_image, int tmp_speed, int tmp_angle) {
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

        // 结合 陀螺仪 凸包检测 进行车辆行为分析
        tmp_car_state = carBehaviorAnalysisByOpenCv.carBehaviorAnalysisByHull(image_sim_number, now_image_hull, tmp_speed, tmp_angle, props);
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
    public void CarModelAnalysis(Mat image) {
        Mat tmp_model_image = new Mat();
        // 模型检测
        Imgproc.resize(image, tmp_model_image, new Size(ImageClassifier.DIM_IMG_SIZE_X, ImageClassifier.DIM_IMG_SIZE_Y));
        Bitmap tmp_model_bitmap = Bitmap.createBitmap(tmp_model_image.cols(), tmp_model_image.rows(),
                Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(tmp_model_image, tmp_model_bitmap);
        model_result = classifier.classifyFrame(tmp_model_bitmap);

        // 记录前后时刻 模型记录的类别
        if (tmp_car_category != classifier.CAR_CATEGORY) {
            last_car_category = tmp_car_category;
            tmp_car_category = classifier.CAR_CATEGORY;
        }
        tmp_model_bitmap.recycle();
        tmp_model_image.release();
    }

    /**
     * 获取需要上传服务器的基本信息数据
     */
    public void getBaseInfoDta(){
        props.setProperty("current_state",openCVTools.result_text.get(tmp_car_state));
        props.setProperty("current_sim", String.valueOf(image_sim_number));
        props.setProperty("current_sim_time", String.valueOf(midTime));
        props.setProperty("current_hull", String.valueOf(now_image_hull));
        props.setProperty("current_hull_time", String.valueOf(midHullMidTime));
        props.setProperty("current_model_result", String.valueOf(classifier.CAR_CATEGORY));
        props.setProperty("current_model_probably", String.valueOf(classifier.CAR_CATEGORY_PROBABILITY));
        props.setProperty("current_model_time", String.valueOf(classifier.CAR_CATEGORY_TIME));
        props.setProperty("last_state", String.valueOf(last_car_state));
        props.setProperty("last_model_result", String.valueOf(last_car_category));
        props.setProperty("current_Id", "川12312");

    }
}
