package com.example.sensormonitor_java;

import androidx.appcompat.app.AppCompatActivity;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import libsvm.svm_model;

public class MainActivity extends AppCompatActivity {
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private Sensor mGyroscope;
    private Sensor mGravity;
    private SensorMonitor mSensorMonitor;
    private PowerManager.WakeLock mWakeLock;
    private static String dir_extern_root;
//    private RadioGroup actionGroup;
//    public int activity_class=0;

    private EditText edit_n_samples;
    private EditText edit_n_overlap;
    private EditText edit_hyperparam_gamma;
    private EditText edit_hyperparam_c;

    public String OUT_FILENAME_ACCELEROMETER = "linear.csv";
    public String OUT_FILENAME_GRAVITY = "gravity.csv";
    public String OUT_FILENAME_GYROSCOPE = "gyro.csv";

    private boolean exists_extern_root = true;
    private boolean exists_every_class = true;
    private boolean mIsMonitoring = false;
    private boolean isModelLoaded = false;

    public ActivityClassifier classifier;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mSensorMonitor = new SensorMonitor(new WeakReference<MainActivity>(this));

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
//        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mGravity = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);


        edit_n_samples = (EditText)findViewById(R.id.editTextNSamples);
        edit_n_overlap = (EditText)findViewById(R.id.editTextNOverlap);
        edit_hyperparam_gamma = (EditText)findViewById(R.id.editTextGamma);
        edit_hyperparam_c = (EditText)findViewById(R.id.editTextC);

        // Permission request
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this,Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
        String state = Environment.getExternalStorageState();
        if (state.equals(Environment.MEDIA_MOUNTED)) {
            Log.d(TAG(), "Available to read and write");
        }
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        // File Directory check, make files
        File extern_root_folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "20202148");
        dir_extern_root = extern_root_folder.getAbsolutePath();
        if(!extern_root_folder.exists()) {
            exists_extern_root = extern_root_folder.mkdirs();
            Log.d(TAG(), "ROOT_DIR is: " + dir_extern_root);
            if (exists_extern_root){
                Log.d(TAG(), "External storage access success");
                for(int i=0;i<7;i++){
                    File extern_root_folder_i = new File(dir_extern_root, String.valueOf(i));
                    if(!extern_root_folder_i.exists()){
                        exists_every_class = exists_every_class && extern_root_folder_i.mkdirs();
                    }
                }
                if(exists_every_class){
                    Log.d(TAG(), "Ready to write");
                }else{
                    Log.e(TAG(), "Class folder access failed");
                }
            }else{
                Log.e(TAG(), "External storage access failed");
            }
        }

//        actionGroup = (RadioGroup) findViewById(R.id.radiogroup);
//        Button buttonToggle = (Button)findViewById(R.id.buttonToggle);
//        buttonToggle.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                mIsMonitoring = !mIsMonitoring;
//                if (mIsMonitoring) {
//                    RadioButton actionButton = (RadioButton) findViewById(actionGroup.getCheckedRadioButtonId());
//                    activity_class = actionGroup.indexOfChild(actionButton);
//                    String str_Qtype = actionButton.getText().toString();
//                    Toast.makeText(getApplicationContext(), str_Qtype + "(" + String.valueOf(activity_class) + ")" +" 기록 중", Toast.LENGTH_SHORT).show();
//                    for (int i = 0; i < actionGroup.getChildCount(); i++) {
//                        actionGroup.getChildAt(i).setEnabled(false);
//                    }
//                    startSensing();
//                } else {
//                    stopSensing();
//                    Toast.makeText(getApplicationContext(), "기록 중지됨", Toast.LENGTH_SHORT).show();
//                    for (int i = 0; i < actionGroup.getChildCount(); i++) {
//                        actionGroup.getChildAt(i).setEnabled(true);
//                    }
//                }
//            }
//        });
//        Button btnTrain = (Button)findViewById(R.id.btnTrain);
//        btnTrain.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//
//                int n_samples = Integer.valueOf(edit_n_samples.getText().toString());
//                int n_overlap = Integer.valueOf(edit_n_overlap.getText().toString());
//                double hyperparam_gamma = Double.valueOf(edit_hyperparam_gamma.getText().toString());
//                double hyperparam_c = Double.valueOf(edit_hyperparam_c.getText().toString());
//
//                Toast.makeText(getApplicationContext(), "Preparing dataset...", Toast.LENGTH_SHORT).show();
//                ActivityClassifier classifier = new ActivityClassifier(n_samples, n_overlap, hyperparam_gamma, hyperparam_c, true);
//                for(int i=0;i<7;i++){
//                    File extern_root_folder_i = new File(dir_extern_root, String.valueOf(i));
//                    classifier.parse_and_sample(new String[]{extern_root_folder_i+"/"+OUT_FILENAME_ACCELEROMETER, extern_root_folder_i+"/"+OUT_FILENAME_GRAVITY, extern_root_folder_i+"/"+OUT_FILENAME_GYROSCOPE});
//                }
//
//                Toast.makeText(getApplicationContext(), "Training Started!", Toast.LENGTH_SHORT).show();
//                btnTrain.setEnabled(false);
//                classifier.fit();
//                btnTrain.setEnabled(true);
//
//            }
//        });
//        Button btnDelete = (Button)findViewById(R.id.btnDelete);
//        btnDelete.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//
//            }
//        });
        Button btnSet = (Button)findViewById(R.id.btnSet);
        btnSet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int n_samples = Integer.valueOf(edit_n_samples.getText().toString());
                int n_overlap = Integer.valueOf(edit_n_overlap.getText().toString());
                double hyperparam_gamma = Double.valueOf(edit_hyperparam_gamma.getText().toString());
                double hyperparam_c = Double.valueOf(edit_hyperparam_c.getText().toString());

                classifier = new ActivityClassifier(n_samples, n_overlap, hyperparam_gamma, hyperparam_c, true);
                classifier.load();
                if(classifier.getModel() != null){
                    Toast.makeText(getApplicationContext(), "Model Loaded!", Toast.LENGTH_SHORT).show();
                    isModelLoaded = true;
                    Button btnMonitor = (Button)findViewById(R.id.btnMonitor);
                    btnMonitor.setEnabled(true);
                }else{
                    Toast.makeText(getApplicationContext(), "Model Loading Failed!", Toast.LENGTH_SHORT).show();
                    Button btnMonitor = (Button)findViewById(R.id.btnMonitor);
                    btnMonitor.setEnabled(false);
                }

            }
        });
        Button btnMonitor = (Button)findViewById(R.id.btnMonitor);
        btnMonitor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mIsMonitoring = !mIsMonitoring;
                if (mIsMonitoring) {
                    Toast.makeText(getApplicationContext(), "모니터링 시작!", Toast.LENGTH_SHORT).show();
                    startSensing();
                } else {
                    stopSensing();
                    Toast.makeText(getApplicationContext(), "모니터링 중지!", Toast.LENGTH_SHORT).show();
                }

            }
        });
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"MyApp::MyWakelockTag");
//        mWakeLock.acquire();  // place this where you want to acquire a wakelock
//        mWakeLock.release();  // place this where you want to release a wakelock

    }

    @Override
    protected void onStart() {
        super.onStart();
//        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        Log.d(TAG(), "onStart");

    }

    @Override
    protected void onStop() {
        super.onStop();
//        mSensorManager.unregisterListener(mSensorMonitor, mAccelerometer);
        Log.d(TAG(), "onStop");
    }

    public void startSensing(){
        mSensorManager.registerListener(mSensorMonitor, mAccelerometer, 10000, mSensorMonitor.getWorkerHandler());
        mSensorManager.registerListener(mSensorMonitor, mGyroscope, 10000, mSensorMonitor.getWorkerHandler());
        mSensorManager.registerListener(mSensorMonitor, mGravity, 10000, mSensorMonitor.getWorkerHandler());
        SensorMonitor.list_accel = new ArrayList<String>();
        SensorMonitor.list_gravity = new ArrayList<String>();
        SensorMonitor.list_gyro = new ArrayList<String>();
        mWakeLock.acquire();
        Log.d(TAG(), "Starting sensor monitoring");
    }

    public void stopSensing(){
        mSensorManager.unregisterListener(mSensorMonitor, mAccelerometer);
        mSensorManager.unregisterListener(mSensorMonitor, mGyroscope);
        mSensorManager.unregisterListener(mSensorMonitor, mGravity);
//        if(SensorMonitor.list_accel.size() > 1200 && SensorMonitor.list_gravity.size() > 1200 && SensorMonitor.list_gyro.size() > 1200){
//            writeData(SensorMonitor.list_accel.subList(600, SensorMonitor.list_accel.size()-600), OUT_FILENAME_ACCELEROMETER);
//            writeData(SensorMonitor.list_gravity.subList(600, SensorMonitor.list_gravity.size()-600), OUT_FILENAME_GRAVITY);
//            writeData(SensorMonitor.list_gyro.subList(600, SensorMonitor.list_gyro.size()-600), OUT_FILENAME_GYROSCOPE);
//        }
        SensorMonitor.list_accel = null;
        SensorMonitor.list_gravity = null;
        SensorMonitor.list_gyro = null;
        mWakeLock.release();
        Log.d(TAG(), "Stopping sensor monitoring");
    }

//    public void updateAccelUI(long timestamp /* millisecond */, float[] values, float freq){
//        ((TextView)findViewById(R.id.textAccelX)).setText(String.format("%.3f", values[0]));
//        ((TextView)findViewById(R.id.textAccelY)).setText(String.format("%.3f", values[1]));
//        ((TextView)findViewById(R.id.textAccelZ)).setText(String.format("%.3f", values[2]));
////        ((TextView)findViewById(R.id.textTimestampAccel)).setText(String.format("%d", timestamp));
//        ((TextView)findViewById(R.id.textSampleRateAccel)).setText(String.format("%.2f", freq));
//    }
//
//    public void updateGyroUI(long timestamp /* millisecond */, float[] values, float freq){
//        ((TextView)findViewById(R.id.textGyroX)).setText(String.format("%.3f", values[0]));
//        ((TextView)findViewById(R.id.textGyroY)).setText(String.format("%.3f", values[1]));
//        ((TextView)findViewById(R.id.textGyroZ)).setText(String.format("%.3f", values[2]));
////        ((TextView)findViewById(R.id.textTimestampGyro)).setText(String.format("%d", timestamp));
//        ((TextView)findViewById(R.id.textSampleRateGyro)).setText(String.format("%.2f", freq));
//    }
//
//    public void updateGravityUI(long timestamp /* millisecond */, float[] values, float freq){
//        ((TextView)findViewById(R.id.textGravityX)).setText(String.format("%.3f", values[0]));
//        ((TextView)findViewById(R.id.textGravityY)).setText(String.format("%.3f", values[1]));
//        ((TextView)findViewById(R.id.textGravityZ)).setText(String.format("%.3f", values[2]));
////        ((TextView)findViewById(R.id.textTimestampGravity)).setText(String.format("%d", timestamp));
//        ((TextView)findViewById(R.id.textSampleRateGravity)).setText(String.format("%.2f", freq));
//    }
//
//
//    public void writeData(List<String> list, String filename){
//        try {
//            File extern_activity_folder = new File(dir_extern_root, String.valueOf(activity_class));
//            File out_file= new File(extern_activity_folder, filename);
//            BufferedWriter bw = new BufferedWriter(new FileWriter(out_file, true));
//            for(int i=0; i<list.size();i++){
//                bw.append(list.get(i));
//            }
//            bw.flush();
//            bw.close();
//            Log.d(TAG(), "Saved "+String.valueOf(list.size())+" lines");
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//
//    }

    static public String TAG(){
        return MainActivity.class.getName() + " " + Thread.currentThread().getName();
    }

    public boolean getIsMonitoring(){
        return this.mIsMonitoring;
    }
    public static String getDirExternRoot(){ return dir_extern_root; }

}