package com.example.android.tflitecamerademo;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 *    本工具类主要是实现openCv一些图像处理操作
 */


public class OpenCVTools {

    public HashMap<Integer, String> result_text = new HashMap<>();  // 记录检测结果

    OpenCVTools(){
        result_text.put(-1, "装载");
        result_text.put(0, "运输");
        result_text.put(1, "倾倒");
        result_text.put(2, "检测失败");
    }

    /**
     *  图像基础处理
     * @param flag
     * @return
     */
    public Mat deal_flag(Mat flag){
        int width = flag.width();
        int height = flag.height();
        // 规定对比区域
        Rect rect = new Rect((int) (width * 0.2), (int) (height * 0.2), (int) (0.7 * width), (int) (0.7 * height));
        Mat cut_flag = new Mat(flag, rect);
        Mat blur_flag = new Mat();

        // 均值偏移 抹去细小纹理
        Imgproc.medianBlur(cut_flag, blur_flag, 9);
        // 统一进行颜色转换
        Mat gary_now_flag = new Mat();
        Imgproc.cvtColor(blur_flag, gary_now_flag, Imgproc.COLOR_BGR2GRAY);
        return gary_now_flag;
    }
    /**
     *  相识度计算
     *  计算单通道的直方图相似度
     */
    private double calculate(Mat image1, Mat image2) {
        List<Mat> images1 = new ArrayList<>();
        Mat hist1 = new Mat();
        Mat mask1 = Mat.ones(image1.size(), CvType.CV_8UC1);
        images1.add(image1);
        Imgproc.calcHist( images1, new MatOfInt(0), mask1, hist1, new MatOfInt(256), new MatOfFloat(0,255));
        float[] histdata1 = new float[256];
        hist1.get(0,0,histdata1);
        // 归一化
        Core.normalize(hist1,hist1,0,255,Core.NORM_MINMAX);

        List<Mat> images2 = new ArrayList<>();
        Mat hist2 = new Mat();
        Mat mask2 = Mat.ones(image2.size(), CvType.CV_8UC1);
        images2.add(image2);
        Imgproc.calcHist( images2, new MatOfInt(0), mask2, hist2, new MatOfInt(256), new MatOfFloat(0,255));
        float[] histdata2 = new float[256];
        hist2.get(0,0,histdata2);
        // 归一化
        Core.normalize(hist2,hist2,0,255,Core.NORM_MINMAX);

        // 计算直方图的重合度
        double degree = 0;
        int tmp_hist_len = hist1.rows();
        for(int i = 0; i < tmp_hist_len - 1; i++){
            if (histdata1[i]!= histdata2[i]){
                degree += (1 - Math.abs(histdata1[i] - histdata2[i]) / Math.max(histdata1[i], histdata2[i]));
            } else {
                degree += 1;
            }
        }

        degree = degree/tmp_hist_len;
        return degree;
    }
    /**
     *  待计算区域分割后 逐一计算相似度
     */
    public int split_blok_box_sim( Mat image1, Mat image2){
        int result = 0;
        int height = image1.height();
        int weight = image1.width();
        int height_inter = height / 3;
        int weight_inter = weight / 3;

        for ( int i = 0; i < 3; i++ ){
            for ( int j = 0; j < 3; j++) {
                Rect tmp_rect_1 = new Rect(i * weight_inter, j * height_inter, weight_inter, height_inter);
                Mat cut_flag1 = new Mat(image1, tmp_rect_1);
                Rect tmp_rect_2 = new Rect(i * weight_inter, j * height_inter, weight_inter, height_inter);
                Mat cut_flag2 = new Mat(image2, tmp_rect_2);
                double tmp_sim_degree = calculate(cut_flag1, cut_flag2);
                if (tmp_sim_degree > 0.8) {
                    result = result + 1;
                }
            }
        }
        return result;
    }
    /**
     *  轮廓提取算法
     *
     */
    public Integer contour_extraction( Mat flag){
        Mat edges = new Mat();
        Imgproc.Canny(flag,edges,10, 100,3,true);
        Mat lines = new Mat();
        Imgproc.HoughLinesP(edges, lines,1, Math.PI / 360.0, 40, 30, 40);
        // 释放内存
        edges.release();
        // 得到的lines是 n x 1 的Mat
        return lines.rows();
    }

}
