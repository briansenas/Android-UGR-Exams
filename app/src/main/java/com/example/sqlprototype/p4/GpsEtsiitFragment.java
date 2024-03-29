package com.example.sqlprototype.p4;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;


import com.example.sqlprototype.R;
import com.journeyapps.barcodescanner.CaptureActivity;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;


public class GpsEtsiitFragment extends Fragment implements DoubleSwipperCallbacks, SensorEventListener {
    View root;
    int destinyNode = 0;
    Instructions currentInstr;
    
    TextView txtInstructions, txtNextNode, txtAngle;
    ImageView imgInstructions, imgCompass;
    
    TextToSpeech textToSpeechEngine;
    DoubleSwipper doubleSwipper;
    static FindPattern findPattern = new FindPattern();
    Compass compass;

    boolean finalizado = false;

    ActivityResultLauncher<ScanOptions> barLauncher;

    SensorManager sensorManager;
    Sensor sensor, sensorgyroscope, magnetometerSensor;
    boolean launchingQR=false;
    private long lastQRCompleted;
    private long lastNextCompleted;
    private long QRdelay = 750;
    private long NextDelay = 750;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FragmentActivity parent = getActivity();

        sensorManager = (SensorManager) parent.getSystemService(Context.SENSOR_SERVICE);
        sensor = sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER).get(0);
        sensorgyroscope = sensorManager.getSensorList(Sensor.TYPE_GYROSCOPE).get(0);
        magnetometerSensor = sensorManager.getSensorList(Sensor.TYPE_MAGNETIC_FIELD).get(0);

        sensorManager=(SensorManager) parent.getSystemService(Context.SENSOR_SERVICE);
        //add listener for accelerometer
        sensorManager.registerListener(this,sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),SensorManager.SENSOR_DELAY_FASTEST);
        //add listener for gyroscope
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_FASTEST);
        //add listener for magnetometer
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_FASTEST);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        root = inflater.inflate(R.layout.fragment_gps_etsiit, container, false);

        txtInstructions = root.findViewById(R.id.txtInstructions);
        txtNextNode = root.findViewById(R.id.txtNextNode);
        txtAngle = root.findViewById(R.id.txtAngle);
        imgInstructions = root.findViewById(R.id.imgInstructions);
        imgCompass = root.findViewById(R.id.imgCompass);


        doubleSwipper = new DoubleSwipper(root, this);
        compass = new Compass(imgCompass, txtAngle);

        textToSpeechEngine = new TextToSpeech(root.getContext(), status -> {
            if (status != TextToSpeech.SUCCESS) {
                Log.e("TTS", "Inicio de la síntesis fallido");
            }
        });

        if (destinyNode == 0)
            throw new RuntimeException("Destiny node is 0");

        barLauncher = registerForActivityResult(new ScanContract(), result -> {
            String qr_result = result.getContents();
            if (qr_result == null) {
                // Launch QR again if this is the first time
                if (currentInstr == null) {
                    Toast.makeText(getContext(), "Es necesario escanear un QR para indicar la localización inicial", Toast.LENGTH_SHORT).show();
                    launchQr();
                }
                launchingQR = false;
                return;
            }

            int currentNode = Integer.parseInt(qr_result);
            doInstructions(currentNode);
            launchingQR = false;
        });

        launchQr();

        return root;
    }


    @Override
    public void onSensorChanged(SensorEvent event) {
        if(event.sensor.getType()==Sensor.TYPE_ACCELEROMETER) {
            compass.updateAccelerometer(event.values);
        }
        if(event.sensor.getType()==Sensor.TYPE_GYROSCOPE) {
            if (System.currentTimeMillis() - lastQRCompleted > QRdelay && findPattern.read_gyro_qr(event.values) ) {
                // Launch QR to get new current node and display new instructions
                launchingQR = true;
                launchQr();
                lastQRCompleted = System.currentTimeMillis();
            }
            if (!launchingQR && System.currentTimeMillis() - lastNextCompleted > NextDelay && findPattern.read_gyro(event.values) ) {
                // Display instructions from next node
                doInstructions(currentInstr.nextNode);
                lastNextCompleted = System.currentTimeMillis();
            }
        }
        if(event.sensor == magnetometerSensor) {
            compass.updateMagnetometer(event.values);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}


    private void doInstructions(int currentNode) {
        if (currentNode == destinyNode) {
            String respuesta_ = "";
            if(currentInstr!=null)
                respuesta_ = "¡Hemos llegado a " + currentInstr.nextNodeName + "! Hasta la próxima.";
            else
                respuesta_ = "¡Ya se encuentra en su destino!";
            textToSpeechEngine.speak(respuesta_, TextToSpeech.QUEUE_FLUSH, null, "tts1");
            txtInstructions.setText(respuesta_);
            txtNextNode.setText(respuesta_);
            imgInstructions.setImageResource(R.drawable.destino);
            compass.desactiveView();


        }else {
            // Get instructions from db
            DataBaseAccess db = DataBaseAccess.getInstance(root.getContext());
            currentInstr = db.getInstructions(currentNode, destinyNode);

            // Say instructions
            textToSpeechEngine.speak(currentInstr.instructions, TextToSpeech.QUEUE_FLUSH, null, "tts1");

            // Set text instructions (which are invisible by default)
            txtInstructions.setText(currentInstr.instructions);

            // Set image
            int img = InstructionsImgMap.getImg(currentNode, currentInstr.nextNode);
            imgInstructions.setImageResource(img);

            // Display where we are going to
            txtNextNode.setText("Siguiente punto: " + currentInstr.nextNodeName);

            // Set compass
            compass.setDirection(currentInstr.direction);
            // test go right, 90 from north
            //compass.setDirection("90");
        }
    }

    public void doubleSwipeUp(){
        txtInstructions.setVisibility(View.VISIBLE);
    }

    public void doubleSwipeDown(){
        txtInstructions.setVisibility(View.GONE);
    }

    private void launchQr() {
        ScanOptions options = new ScanOptions();
        options.setBeepEnabled(false);
        options.setOrientationLocked(false);
        options.setCaptureActivity(CaptureActivity.class);
        options.setPrompt("Escanea el código QR");
        barLauncher.launch(options);
    }

    public void setDestinyNode(int destinyNode) {
        this.destinyNode = destinyNode;
    }

    public boolean cancelTxtSpeech() {
        boolean res = false;
        if(textToSpeechEngine == null)
            return res;
        if (textToSpeechEngine.isSpeaking()) {
            textToSpeechEngine.stop();
            res = true;
        }
        return res;
    }

    public void unregisterListeners(){
        if(sensorManager!=null)
            sensorManager.unregisterListener(this);
    }
}
