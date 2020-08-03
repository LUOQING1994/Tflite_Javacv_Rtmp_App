package com.example.android.tflitecamerademo;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

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

    private LocationManager locationManager;
    private double EARTH_RADIUS = 6378137.0;
    private Location lastLocation;

    Properties props = new Properties();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setupView();
        } catch (Exception e) {
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
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000,
                1F,
                locationListener
        );
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(sensorEventListener);
        locationManager.removeUpdates(locationListener);
    }

    private void setupView() throws IOException {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        props.load(getApplicationContext().getAssets().open("config.properties"));

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

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000,
                1F,
                locationListener
        );
        Location location =
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        locationUpdate(location);
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
        currentAngle = Math.max(verAngle, horAngle);
//        Log.d("================", "currentAngle========>" + currentAngle);
    }

    private LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            locationUpdate(location);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        @Override
        public void onProviderEnabled(String provider) {

        }

        @Override
        public void onProviderDisabled(String provider) {

        }
    };

    private void locationUpdate(Location location) {
        double s = 0.0;
        if (null != lastLocation) {
            s = gps2m(location.getLatitude(), location.getLongitude(), lastLocation.getLatitude(), lastLocation.getLongitude());
        }
        lastLocation = location;
        //获取最新位置信息
        currentSpeed = s;
        Log.d("当前设备速度", "currentSpeed ========>" + s);
//        tv.text = "您的当前位置:\n经度：${location?.longitude}\n纬度:${location?.latitude}\n速度：$s m/s"
    }

    private double gps2m(
            double lat_a,
            double lng_a,
            double lat_b,
            double lng_b
    ) {
        double radLat1 = lat_a * Math.PI / 180.0;
        double radLat2 = lat_b * Math.PI / 180.0;
        double a = radLat1 - radLat2;
        double b = (lng_a - lng_b) * Math.PI / 180.0;
        double s = 2 * Math.asin(Math.sqrt(Math.pow(Math.sin(a / 2), 2) +
                Math.cos(radLat1) * Math.cos(radLat2) * Math.pow(Math.sin(b / 2), 2)));
        s *= EARTH_RADIUS;
        s = Math.round((s * 10000)) / 10000.0;
        return s;
    }
}
