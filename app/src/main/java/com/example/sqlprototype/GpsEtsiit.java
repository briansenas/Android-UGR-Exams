package com.example.sqlprototype;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GpsEtsiit extends Fragment implements SensorEventListener {

    View root;

    public boolean mode;
    public float p1StartY,p1StopY,p2StartY,p2StopY;
    public float DOUBLE_SWIPE_THRESHOLD = 0.5f;

    SensorManager sensorManager;
    Sensor sensor, sensorgyroscope;

    public static float THRESHOLD_Z = 10f;
    public static float THRESHOLD_Y = 10f;
    public float EPSILON_Z = 3f;
    public float EPSILON_Y = 3f;
    public boolean first_read = false;
    public float FCX, FCY, FCZ;
    public int SIGNY;
    public static boolean is_running = false;
    private static int count = 0;
    private ScheduledExecutorService executorService;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FragmentActivity parent = getActivity();

        sensorManager = (SensorManager) parent.getSystemService(Context.SENSOR_SERVICE);
        sensor = sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER).get(0);
        sensorgyroscope = sensorManager.getSensorList(Sensor.TYPE_GYROSCOPE).get(0);

        sensorManager=(SensorManager) parent.getSystemService(Context.SENSOR_SERVICE);
        //add listener for accelerometer
        sensorManager.registerListener(this,sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),SensorManager.SENSOR_DELAY_FASTEST);
        //add listener for gyroscope
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_FASTEST);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        root = inflater.inflate(R.layout.fragment_gps_etsiit, container, false);

        root.setOnTouchListener((v, event) -> {
            if(event.getPointerCount()>1) {
                switch (event.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_POINTER_DOWN:
                        // This happens when you touch the screen with two fingers
                        this.mode = true;
                        // event.getY(1) is for the second finger
                        p1StartY = event.getY(0);
                        p2StartY = event.getY(1);
                        break;

                    case MotionEvent.ACTION_POINTER_UP:
                        // This happens when you release the second finger
                        this.mode = true;
                        float p1Diff = p1StartY - p1StopY;
                        float p2Diff = p2StartY - p2StopY;

                        //this is to make sure that fingers go in same direction and
                        // swipe have certain length to consider it a swipe
                        if (Math.abs(p1Diff) > DOUBLE_SWIPE_THRESHOLD
                                && Math.abs(p2Diff) > DOUBLE_SWIPE_THRESHOLD &&
                                ((p1Diff > 0 && p2Diff > 0) || (p1Diff < 0 && p2Diff < 0))) {
                            if (p1StartY > p1StopY) {
                                // Swipe up
                                doubleSwipeUp();
                            } else {
                                //Swipe down
                                doubleSwipeDown();
                            }
                        }
                        this.mode = false;
                        break;

                    case MotionEvent.ACTION_MOVE:
                        if (this.mode == true) {
                            p1StopY = event.getY(0);
                            p2StopY = event.getY(1);
                        }
                        break;
                }
            }
            return true;

            });

        return root;
    }



    public void doubleSwipeUp(){
        Toast.makeText(getContext(),"SWIPE UP",Toast.LENGTH_SHORT).show();
    }

    public void doubleSwipeDown(){
        Toast.makeText(getContext(),"SWIPE DOWN",Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if(event.sensor.getType()==Sensor.TYPE_ACCELEROMETER)
        {

            if(!first_read) {
               FCX = event.values[0];
               FCY = event.values[1];
               FCZ = event.values[2];
               SIGNY = Integer.signum((int)FCY);
                first_read = true;
            }

            float y = event.values[1];
            float z = event.values[2];
            float diff_y = Math.abs(FCY - THRESHOLD_Y);
            float diff_z = Math.abs(FCZ - THRESHOLD_Z);
            boolean upd_y, upd_z;
            if(THRESHOLD_Y!=0){
                upd_y = Math.abs(y - diff_y) <= EPSILON_Y;
                upd_z = z + diff_z <= EPSILON_Z;
            }else{
               upd_y = Math.abs(y - FCY) <= EPSILON_Y;
               upd_z = Math.abs(z - FCZ) <= EPSILON_Z;
            }
            System.out.println(upd_y + " -- " + upd_z + " -- " + is_running + " -- " + count + " -- " + THRESHOLD_Y);
            if(upd_y && upd_z){
                if(is_running && count>=2){
                    is_running = false;
                    count += 1;
                    Toast.makeText(getContext(),"PATTERN READ",Toast.LENGTH_SHORT).show();
                }else{
                    THRESHOLD_Z -= 10f;
                    THRESHOLD_Y -= 10f;
                    THRESHOLD_Y = Math.abs(THRESHOLD_Y);
                    THRESHOLD_Y = Math.abs(THRESHOLD_Z);
                    count += 1;
                    if(!is_running) {
                        executorService = Executors.newSingleThreadScheduledExecutor();
                        executorService.schedule(GpsEtsiit::ResetPattern, 3, TimeUnit.SECONDS);
                    }
                    is_running = true;
                }
            }
        }
    }

    public static void ResetPattern(){
        is_running = false;
        THRESHOLD_Y = 10f;
        THRESHOLD_Z = 10f;
        count = 0;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
