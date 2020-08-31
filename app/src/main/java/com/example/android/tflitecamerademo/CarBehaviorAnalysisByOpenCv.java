package com.example.android.tflitecamerademo;


import java.util.Properties;

/**
 *  本程序使用opencv、车辆陀螺仪、车辆速度来对车辆行为进行分析
 * **/
public class CarBehaviorAnalysisByOpenCv {

    // 为当前Activity取一个名字 方便调试
    private String TAG = "CarBehaviorAnalysisByOpenCv";


    /**
     * @param image_sim_number    前后两帧的相似度
     * @param now_image_hull    当前检测区域凸包数量
     * @param speed_time    车载速度大于阈值的持续时间
     * @param now_angle    陀螺仪角度
     * @return    车辆行为
     */
    Integer carBehaviorAnalysisByHull(Integer image_sim_number, Integer now_image_hull, long speed_time, Integer now_angle, Properties props) {

        int hull_number_through = Integer.parseInt(props.getProperty("hull_number_through"));
        int hull_angle_through = Integer.parseInt(props.getProperty("hull_angle_through"));
        int image_sim_number_through = Integer.parseInt(props.getProperty("image_sim_number"));
        int speed_time_through = Integer.parseInt(props.getProperty("speed_time_through"));

        int tmp_car_state;
        if ((now_angle > hull_angle_through)){ // 角度大于15 则有可能出现倾倒行为 去除凸包 防止夜间倾倒
            if ((speed_time < speed_time_through) && (image_sim_number != image_sim_number_through)) { // 速度大于阈值的持续时间 且相似度没有连续过高
                tmp_car_state = 1;    // 视为倾倒
            } else {
                tmp_car_state = 0;    // 视为运输
            }
        } else if ((now_angle < hull_angle_through - 8) && (speed_time < speed_time_through)){ // 角度小于10 且速度大于阈值的持续时间小于初始值 则有可能出现装载
            if ((now_image_hull > hull_number_through) && (image_sim_number != image_sim_number_through)){ // 当前凸包要大于10 且相似度没有连续过高
                tmp_car_state = -1;   // 视为装载
            } else {
                tmp_car_state = 0;    // 视为运输
            }
        } else {  // 其他情况 视为运输
            tmp_car_state = 0;
        }

        return tmp_car_state;
    }

}

