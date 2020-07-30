package com.example.android.tflitecamerademo;

import android.util.Log;

import org.opencv.core.Mat;

import java.util.Properties;

public class CarBehaviorAnalysisByModel {

    // 为当前Activity取一个名字 方便调试
    private String TAG = "CarBehaviorAnalysisByModel";

    /**
     *
     * @param category    当前模型给出的类别
     * @param last_car_category    上一次模型的类别
     * @param last_car_state    上一次车辆状态
     * @param now_car_category_probability    当前模型的类别概率
     * @param now_image    当前画面
     * @param last_image   上一帧画面
     * @param image_sim_number   前后两帧画面的相似度
     * @param now_angle    当前陀螺仪角度
     * @param gps_speed    当前车载设备速度
     * @return    当前货车状态
     */
    Integer carBehaviorAnalysis(Integer category, Integer last_car_category, Integer last_car_state, Double now_car_category_probability
            , Mat now_image, Mat last_image, Integer image_sim_number, Integer now_angle, Integer gps_speed, Properties props) {

        int model_angle_through = Integer.parseInt(props.getProperty("model_angle_through"));
        int model_speed_thought = Integer.parseInt(props.getProperty("model_speed_thought"));
        int image_sim_number_through = Integer.parseInt(props.getProperty("image_sim_number"));

        int tmp_car_state;  // 默认延续上一时刻的车斗状态
        try {
            if (last_car_category == 6){ // 上一时刻为幕布
                if (category == 6){   // 当前时刻为幕布
                    tmp_car_state = 0;  // 视为运输
                }else if (category == 5){ //  当前时刻为空车
                    tmp_car_state = 0;  // 视为运输
                }else{ //当前时刻为有货
                    // 利用陀螺仪数据和相识度 区分倾倒、装载、运输
                    if (now_angle < model_angle_through){ // 倾斜角度小于20 视为平放
                        tmp_car_state = 0;
                    } else{ // 倾斜角度大于20 视为倾倒
                        tmp_car_state = 1;
                    }
                }
            } else if (last_car_category == 5){ // 上一时刻为空车
                if (category == 6){   // 当前时刻为幕布
                    tmp_car_state = 0;  // 视为运输
                }else if (category == 5){ //  当前时刻为空车
                    tmp_car_state = 0;  // 视为运输
                }else{ //当前时刻为有货
                    if (image_sim_number != image_sim_number_through) {
                        tmp_car_state = -1; // 视为装载
                    } else {
                        tmp_car_state = 0; // 视为运输
                    }
                }
            } else { // 上一时刻为有货
                if (category == 6){ // 当前时刻为幕布
                    tmp_car_state = 0;  // 视为运输
                }else if(category == 5){  // 当前时刻为空车
                    tmp_car_state = 0;  // 视为运输
                }else{  // 当前时刻为有货
                    // 利用陀螺仪数据和相识度 区分倾倒、装载、运输
                    if (now_angle < model_angle_through){ // 倾斜角度小于20 视为平放
                        if (last_car_state == 1){  // 上一时刻状态为倾倒
                            tmp_car_state = 0;  // 视为运输
                        }else {  // 上一时刻为运输或者装载
                            if (image_sim_number != image_sim_number_through) {
                                tmp_car_state = -1; // 视为装载
                            } else {
                                tmp_car_state = 0; // 视为运输
                            }
                                // tmp_car_state = last_car_state;
                        }
                    }else{ // 倾斜角度大于20 视为倾倒
                        tmp_car_state = 1;
                    }

                }

            }

            // 界面显示检测结果
            return tmp_car_state;
        } catch (Exception e) {
            Log.i(TAG, e.toString());
            // 发生错误时 自动返回0 （运输）
            return 0;
        }
    }

}

