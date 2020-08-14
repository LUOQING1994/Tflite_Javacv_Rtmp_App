package com.example.android.tflitecamerademo;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.utils.DistanceUtil;

import java.io.IOException;
import java.util.Properties;

/**
 * @author zdl
 * date 2020/7/24 16:32
 * email zdl328465042@163.com
 * description
 */
class BaseActivity extends Activity {

    double currentAngle = 0.0;
    double currentSpeed = 0.0;

    private SensorManager sensorManager;
    private Sensor magneticSensor;
    private Sensor accelerometerSensor;
    private float[] r = new float[9];
    private float[] gravity = new float[3];
    private float[] geomagnetic = new float[3];
    private float[] values = new float[3];

    private LatLng lastLatLng;
    Properties props = new Properties();
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setupView();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
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

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(sensorEventListener);
    }

    private void setupView() throws IOException {
        props.load(getApplicationContext().getAssets().open("config.properties"));
        initAngle();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        initLocationOption();
    }

    private void initAngle(){
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
        if (horAngle > 90.0) {
            horAngle = horAngle - 90.0;
        }
//        currentAngle = Math.max(verAngle, horAngle);
        currentAngle = degreeY;
//        Log.d("================", "currentAngle========>" + currentAngle);
    }

    /**
     * 初始化定位参数配置
     */

    private void initLocationOption() {
        SDKInitializer.initialize(getApplicationContext());
//定位服务的客户端。宿主程序在客户端声明此类，并调用，目前只支持在主线程中启动
        LocationClient locationClient = new LocationClient(getApplicationContext());
//声明LocationClient类实例并配置定位参数
        LocationClientOption locationOption = new LocationClientOption();
        MyLocationListener myLocationListener = new MyLocationListener();
//注册监听函数
        locationClient.registerLocationListener(myLocationListener);
//可选，默认高精度，设置定位模式，高精度，低功耗，仅设备
        locationOption.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);
//可选，默认gcj02，设置返回的定位结果坐标系，如果配合百度地图使用，建议设置为bd09ll;
        locationOption.setCoorType("bd09ll");
//可选，默认0，即仅定位一次，设置发起连续定位请求的间隔需要大于等于1000ms才是有效的
        locationOption.setScanSpan(1000);
//可选，设置是否需要地址信息，默认不需要
        locationOption.setIsNeedAddress(false);
//可选，设置是否需要地址描述
        locationOption.setIsNeedLocationDescribe(false);
//可选，设置是否需要设备方向结果
        locationOption.setNeedDeviceDirect(false);
//可选，默认false，设置是否当gps有效时按照1S1次频率输出GPS结果
        locationOption.setLocationNotify(true);
//可选，默认true，定位SDK内部是一个SERVICE，并放到了独立进程，设置是否在stop的时候杀死这个进程，默认不杀死
        locationOption.setIgnoreKillProcess(true);
//可选，默认false，设置是否需要位置语义化结果，可以在BDLocation.getLocationDescribe里得到，结果类似于“在北京天安门附近”
        locationOption.setIsNeedLocationDescribe(false);
//可选，默认false，设置是否需要POI结果，可以在BDLocation.getPoiList里得到
        locationOption.setIsNeedLocationPoiList(false);
//可选，默认false，设置是否收集CRASH信息，默认收集
        locationOption.SetIgnoreCacheException(false);
//可选，默认false，设置是否开启Gps定位
        locationOption.setOpenGps(true);
//可选，默认false，设置定位时是否需要海拔信息，默认不需要，除基础定位版本都可用
        locationOption.setIsNeedAltitude(false);
//设置打开自动回调位置模式，该开关打开后，期间只要定位SDK检测到位置变化就会主动回调给开发者，该模式下开发者无需再关心定位间隔是多少，定位SDK本身发现位置变化就会及时回调给开发者
        locationOption.setOpenAutoNotifyMode();
//设置打开自动回调位置模式，该开关打开后，期间只要定位SDK检测到位置变化就会主动回调给开发者
        locationOption.setOpenAutoNotifyMode(3000, 1, LocationClientOption.LOC_SENSITIVITY_HIGHT);
//需将配置好的LocationClientOption对象，通过setLocOption方法传递给LocationClient对象使用
        locationClient.setLocOption(locationOption);
//开始定位
        locationClient.start();
    }

    /**
     * 实现定位回调
     *  当百度发现gps改变时 1s发送一次gps数据 当gps不变时 会改变发送频率
     */
    private class MyLocationListener extends BDAbstractLocationListener {
        @Override
        public void onReceiveLocation(BDLocation location) {
            //此处的BDLocation为定位结果信息类，通过它的各种get方法可获取定位相关的全部结果
            //以下只列举部分获取经纬度相关（常用）的结果信息
            //更多结果信息获取说明，请参照类参考中BDLocation类中的说明

            //获取纬度信息
            double latitude = location.getLatitude();
            //获取经度信息
            double longitude = location.getLongitude();
            //获取定位精度，默认值为0.0f
            float radius = location.getRadius();
            //获取经纬度坐标类型，以LocationClientOption中设置过的坐标类型为准
            String coorType = location.getCoorType();
            //获取定位类型、定位错误返回码，具体信息可参照类参考中BDLocation类中的说明
            int errorCode = location.getLocType();

            Log.d("================", "====latitude>" + latitude + "\tlongitude====>" + longitude);

            if (null == lastLatLng) {
                lastLatLng = new LatLng(latitude, longitude);
            } else {
                LatLng ll = new LatLng(latitude, longitude);
                currentSpeed = DistanceUtil.getDistance(lastLatLng, ll);
                lastLatLng = ll;
            }

            Log.d("================", "currentSpeed========>" + currentSpeed);
        }
    }
}
