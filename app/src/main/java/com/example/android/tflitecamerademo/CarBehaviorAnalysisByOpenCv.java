package com.example.android.tflitecamerademo;


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
    Integer carBehaviorAnalysis(Integer image_sim_number,Integer now_image_len, Integer speed, Integer now_angle) {
        int tmp_car_state;
        if ((now_image_len > 100) && (now_angle > 20)){ // 线条数大于100 且角度大于20 则有可能出现倾倒行为
            if ((speed < 6) && (image_sim_number != 0)) { // 速度小于6 且相似度没有连续过高
                tmp_car_state = 1;    // 视为倾倒
            } else {
                tmp_car_state = 0;    // 视为运输
            }
        } else if ((now_angle < 10) && (speed < 6)){ // 角度小于10 且速度小于6 则有可能出现装载
            if ((now_image_len > 100) && (image_sim_number != 0)){ // 当前线条要大于100 且相似度没有连续过高
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
    Integer carBehaviorAnalysisByHull(Integer image_sim_number,Integer now_image_hull, Integer speed, Integer now_angle) {
        int tmp_car_state;
        if ((now_image_hull > 10) && (now_angle > 20)){ // 凸包数大于10 且角度大于20 则有可能出现倾倒行为
            if ((speed < 6) && (image_sim_number != 0)) { // 速度小于6 且相似度没有连续过高
                tmp_car_state = 1;    // 视为倾倒
            } else {
                tmp_car_state = 0;    // 视为运输
            }
        } else if ((now_angle < 10) && (speed < 6)){ // 角度小于10 且速度小于6 则有可能出现装载
            if ((now_image_hull > 10) && (image_sim_number != 0)){ // 当前线条要大于10 且相似度没有连续过高
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

