package com.example.android.tflitecamerademo;

import org.opencv.core.Mat;

public class MatNumberUtils {
    private int number;
    private Mat iamge;
    private String toShow;

    public String getToShow() {
        return toShow;
    }

    public void setToShow(String toShow) {
        this.toShow = toShow;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public void setIamge(Mat iamge) {
        this.iamge = iamge;
    }

    public int getNumber() {
        return number;
    }

    public Mat getIamge() {
        return iamge;
    }
}
