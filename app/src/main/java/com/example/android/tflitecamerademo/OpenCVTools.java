package com.example.android.tflitecamerademo;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import android.util.Log;
import android.widget.ImageView;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
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
        Rect rect = new Rect((int) (width * 0.24), (int) (height * 0.315), (int) (0.485 * width), (int) (0.55 * height));
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
        int height_threshold = 200;  // 边缘检测的上边界
        int line_number = 0;  // 统计满足条件的凸包个数
        Mat lines = new Mat();
        boolean isFirst = false; // 轮廓提取时 是否第一次满足线条数大于100
        // 设定line_number的值较小 防止动态参数调整过大
        Mat edges = new Mat();
        Mat morphology = new Mat();
        while (line_number < 10 || isFirst ) {   // 动态的调整参数 使其能够适应于夜晚
            Imgproc.Canny(flag,edges,10, height_threshold,3,true);

            // 膨胀 连接边缘
            Imgproc.dilate(edges, morphology, new Mat(), new Point(-1, -1), 3, 1, new Scalar(1));
            Imgproc.HoughLinesP(morphology, lines,1, Math.PI / 180.0, 40, 30, 40);
            line_number = lines.rows();

            if (line_number < 10 && height_threshold > 20) {
                height_threshold = height_threshold - 20;
            } else if (line_number > 10 && height_threshold >= 40 && !isFirst) {
                height_threshold =  height_threshold - 10;
                isFirst = true;
            } else {
                break;
            }
        }
        // 统计线条直线
        int number = 0;
        for (int i = 0; i < line_number; i++) {
            int[] oneLine = new int[4];
            lines.get(i,0,oneLine);
            // 去除过长和太短的线条
            double tmp_x = Math.pow((oneLine[0] - oneLine[2]), 2);
            double tmo_y = Math.pow((oneLine[1] - oneLine[3]), 2);
            int tmp_dis = (int) Math.sqrt(tmo_y + tmp_x);
            if (tmp_dis > 35 && tmp_dis < 200) {
                number = number + 1;
            }
            if (number >= 500) {
                break;
            }
//            Imgproc.line(flag, new Point(oneLine[0],oneLine[1]),new Point(oneLine[2],oneLine[3]),new Scalar(0,0,255),2,8,0 );
        }

        return number;
    }
    /**
     * 轮廓提取 + 凸包检测
     */
    public Integer contours_Hull(Mat image){

        // 高斯滤波，降噪  只对凸包检测进行处理
        Imgproc.GaussianBlur(image, image, new Size(9,9), 2, 2);
        int width = image.width();
        int height = image.height();
        double rect_area = width * height * 0.15;

        Mat binary = new Mat();
        int height_threshold = 200;  // 边缘检测的上边界
        int contourHull_number = 0;  // 统计满足条件的凸包个数
        while (contourHull_number < 10) {
            // Canny边缘检测
            Imgproc.Canny(image, binary, 10, height_threshold, 3, false);

            Mat morphology = new Mat();
            // 膨胀 连接边缘
            Imgproc.dilate(binary, morphology, new Mat(), new Point(-1,-1), 3, 1, new Scalar(1));

            // 轮廓发现
            List<MatOfPoint> contours = new ArrayList<>();
            Mat hierarchy = new Mat();
            Imgproc.findContours(morphology, contours, hierarchy, Imgproc.RETR_EXTERNAL,Imgproc.CHAIN_APPROX_SIMPLE, new Point(0,0));

            // 凸包提取
            MatOfInt hull = new MatOfInt();
            MatOfPoint2f approx = new MatOfPoint2f();
            approx.convertTo(approx, CvType.CV_32F);

            for (MatOfPoint contour: contours) {
                // 边框的凸包
                Imgproc.convexHull(contour, hull);
                // 用凸包计算出新的轮廓点
                Point[] contourPoints = contour.toArray();
                int[] indices = hull.toArray();
                List<Point> newPoints = new ArrayList<>();
                for (int index : indices) {
                    newPoints.add(contourPoints[index]);
                }
                MatOfPoint2f contourHull = new MatOfPoint2f();
                contourHull.fromList(newPoints);
                // 多边形拟合凸包边框(此时的拟合的精度较低)
                Imgproc.approxPolyDP(contourHull, approx, Imgproc.arcLength(contourHull, true)*0.02, true);
                // 筛选出面积大于某一阈值的凸多边形
                MatOfPoint approxf1 = new MatOfPoint();
                approx.convertTo(approxf1, CvType.CV_32S);
                double tmp_area = Math.abs(Imgproc.contourArea(approx));
                if ( approx.rows() > 3 && rect_area > tmp_area && tmp_area > 1000 &&
                        Imgproc.isContourConvex(approxf1)) {
                    // 绘制凸包
//                    for (int j = 0; j < approx.rows(); j++) {
//                        Imgproc.line(tmp_cut_flag, approx.toArray()[j], approx.toArray()[(j + 1) % approx.rows()], new Scalar(0,255,255), 2, 8, 0);
//                    }
                    contourHull_number = contourHull_number + 1;
                }
            }
            if(height_threshold > 10 && contourHull_number < 10){
                height_threshold = height_threshold - 15;   // 边缘检测的上限以15的梯度下降
                contourHull_number = 0; // 重置凸包数量
            } else {
                break;
            }

        }

        return contourHull_number;

    }
}
