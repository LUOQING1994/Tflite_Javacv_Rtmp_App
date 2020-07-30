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
     * @param now_image_len    当前检测区域直线数
     * @param speed    车载速度
     * @param now_angle    陀螺仪角度
     * @return    车辆行为
     */
    Integer carBehaviorAnalysis(Integer image_sim_number, Integer now_image_len, Integer speed, Integer now_angle, Properties props) {
        int line_number_through = Integer.parseInt(props.getProperty("line_number_through"));
        int line_angle_through = Integer.parseInt(props.getProperty("line_angle_through"));
        int line_speed_thought = Integer.parseInt(props.getProperty("line_speed_thought"));
        int image_sim_number_through = Integer.parseInt(props.getProperty("image_sim_number"));
        int tmp_car_state;
        if ((now_image_len > line_number_through) && (now_angle > line_angle_through)){ // 线条数大于100 且角度大于20 则有可能出现倾倒行为
            if ((speed < line_speed_thought) && (image_sim_number != image_sim_number_through)) { // 速度小于6 且相似度没有连续过高
                tmp_car_state = 1;    // 视为倾倒
            } else {
                tmp_car_state = 0;    // 视为运输
            }
        } else if ((now_angle < line_angle_through) && (speed < line_speed_thought)){ // 角度小于10 且速度小于6 则有可能出现装载
            if ((now_image_len > line_number_through) && (image_sim_number != image_sim_number_through)){ // 当前线条要大于100 且相似度没有连续过高
                tmp_car_state = -1;   // 视为装载
            } else {
                tmp_car_state = 0;    // 视为运输
            }
        } else {  // 其他情况 视为运输
            tmp_car_state = 0;
        }

         return tmp_car_state;
    }

    /**
     * @param image_sim_number    前后两帧的相似度
     * @param now_image_hull    当前检测区域凸包数量
     * @param speed    车载速度
     * @param now_angle    陀螺仪角度
     * @return    车辆行为
     */
    Integer carBehaviorAnalysisByHull(Integer image_sim_number,Integer now_image_hull, Integer speed, Integer now_angle, Properties props) {

        int hull_number_through = Integer.parseInt(props.getProperty("hull_number_through"));
        int hull_angle_through = Integer.parseInt(props.getProperty("hull_angle_through"));
        int hull_speed_thought = Integer.parseInt(props.getProperty("hull_speed_thought"));
        int image_sim_number_through = Integer.parseInt(props.getProperty("image_sim_number"));

        int tmp_car_state;
        if ((now_image_hull > hull_number_through) && (now_angle > hull_angle_through)){ // 凸包数大于20 且角度大于20 则有可能出现倾倒行为
            if ((speed < hull_speed_thought) && (image_sim_number != image_sim_number_through)) { // 速度小于6 且相似度没有连续过高
                tmp_car_state = 1;    // 视为倾倒
            } else {
                tmp_car_state = 0;    // 视为运输
            }
        } else if ((now_angle < hull_angle_through) && (speed < hull_speed_thought)){ // 角度小于20 且速度小于6 则有可能出现装载
            if ((now_image_hull > hull_number_through) && (image_sim_number != image_sim_number_through)){ // 当前线条要大于10 且相似度没有连续过高
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

