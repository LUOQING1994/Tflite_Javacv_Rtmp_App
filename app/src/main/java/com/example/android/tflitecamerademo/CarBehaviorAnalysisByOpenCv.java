package com.example.android.tflitecamerademo;


import java.util.Properties;

/**
 *  本程序使用opencv、车辆陀螺仪、车辆速度来对车辆行为进行分析
 * **/
public class CarBehaviorAnalysisByOpenCv {

    // 为当前Activity取一个名字 方便调试
    private String TAG = "CarBehaviorAnalysisByOpenCv";


    /**
     * @param image_sim_time    前后两帧的相似度持续时间
     * @param now_image_hull    当前检测区域凸包数量
     * @param speed_time    车载速度大于阈值的持续时间
     * @param now_angle    陀螺仪角度
     * @return    车辆行为
     */
    Integer carBehaviorAnalysisByHull(long image_sim_time, Integer now_image_hull, long speed_time, Integer now_angle, Properties props) {

        int hull_number_through = Integer.parseInt(props.getProperty("hull_number_through"));
        int hull_angle_through = Integer.parseInt(props.getProperty("hull_angle_through"));
        int speed_time_through = Integer.parseInt(props.getProperty("speed_time_through"));

        int tmp_car_state;
        if ((now_angle > hull_angle_through)){ // 角度大于15 则有可能出现倾倒行为 去除凸包 防止夜间倾倒
            if ((speed_time < speed_time_through) && image_sim_time < 30) { // 相似度持续时间不大于30时表示有可能出现运输单大于59时就一定视为运输
                tmp_car_state = 1;    // 视为倾倒
            } else {
                tmp_car_state = 0;    // 视为运输
            }
        } else {
            if (speed_time < speed_time_through
                    && now_image_hull > hull_number_through && image_sim_time < 30){ // 相似度持续时间不大于30时表示有可能出现运输单大于59时就一定视为运输
                tmp_car_state = -1;   // 视为装载
            } else {  // 其他情况 视为运输
                tmp_car_state = 0;
            }
        }

        return tmp_car_state;
    }

}

