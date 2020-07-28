package com.example.android.tflitecamerademo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;

/**
 * @author zdl
 * date 2020/7/24 16:32
 * email zdl328465042@163.com
 * description
 */
class BaseActivity extends Activity {

    double currentAngle = 0.0;

    private SensorManager sensorManager;
    private Sensor magneticSensor;
    private Sensor accelerometerSensor;

    private float[] r = new float[9];
    private float[] gravity = new float[3];
    private float[] geomagnetic = new float[3];
    private float[] values = new float[3];

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupView();
    }

    @SuppressLint("MissingPermission")
    private void setupView() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(
                sensorEventListener,
                magneticSensor,
                SensorManager.SENSOR_DELAY_GAME
        );
        sensorManager.registerListener(
                sensorEventListener,
                accelerometerSensor,
                SensorManager.SENSOR_DELAY_GAME
        );
    }

    private SensorEventListener sensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (null == event) return;
            switch (event.sensor.getType()) {
                case Sensor.TYPE_MAGNETIC_FIELD:
                    for (int i = 0; i < event.values.length; i++) {
                        geomagnetic[i] = event.values[i];
                    }
                    break;
                case Sensor.TYPE_ACCELEROMETER:
                    for (int i = 0; i < event.values.length; i++) {
                        gravity[i] = event.values[i];
                    }
                    getOrientation();
                    break;
                default:
                    break;
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };

    private void getOrientation() {
        SensorManager.getRotationMatrix(r, null, gravity, geomagnetic);
        SensorManager.getOrientation(r, values);
        double degreeX = Math.toDegrees(values[1]);
        double degreeY = Math.toDegrees(values[2]);
        double degreeZ = Math.toDegrees(values[0]);

//        Log.d("================", "degreeX========>" + degreeX);
//        Log.d("================", "degreeY========>" + degreeY);
//        Log.d("================", "degreeZ========>($degreeZ)")

        //手机竖直方向角度[-90,90]
        double verAngle = Math.abs(degreeX);
        //手机横向角度[-180,180]
        double horAngle = Math.abs(degreeY);
//        Log.d("================", "手机横向角度========>  " + degreeY);
        if (horAngle > 90.0){
            horAngle = horAngle - 90.0;
        }
        currentAngle = Math.max(verAngle, horAngle);
    }
}
