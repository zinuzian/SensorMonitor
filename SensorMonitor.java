package com.example.sensormonitor_java;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.icu.util.Output;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.RequiresApi;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public class SensorMonitor implements SensorEventListener {
    private int mCountAccel = 0;
    private int mCountGyro = 0;
    private int mCountGravity = 0;
    private long mOffsetAccel = 0L;
    private long mOffsetGyro = 0L;
    private long mOffsetGravity = 0L;
    private float mFreqAccel = 0.0f;
    private float mFreqGyro = 0.0f;
    private float mFreqGravity = 0.0f;
    private final HandlerThread mWorker;
    private long prevTsAccel = 0L;
    private long prevTsGravity = 0L;
    private long prevTsGyro = 0L;
    public static List<String> list_accel = null;
    public static List<String> list_gravity = null;
    public static List<String> list_gyro = null;
    public static List<float[]> arr_linear = null;
    public static List<float[]> arr_gravity = null;
    public static List<float[]> arr_gyro= null;


    private final Handler mMainHandler, mWorkerHandler;
    private final WeakReference<MainActivity> mMainActivity;

    public SensorMonitor(WeakReference<MainActivity> activity) {
        mWorker = new HandlerThread("WorkerThread");
        mWorker.start();
        mWorkerHandler = new Handler(mWorker.getLooper());

        mMainActivity = activity;
        mMainHandler = new Handler(Looper.getMainLooper());

        arr_linear = new ArrayList<>();
        arr_gravity = new ArrayList<>();
        arr_gyro = new ArrayList<>();

    }

    @Override
    protected void finalize() throws Throwable {
        mWorker.quitSafely();
        super.finalize();
    }

    public Handler getWorkerHandler(){
        return mWorkerHandler;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            long tsAccel = event.timestamp; // nanosecond
            float[] values = event.values.clone();
            if (mCountAccel++ == 0) {
                mOffsetAccel = tsAccel;
            } else if (mCountAccel == 100) {
                mCountAccel = 0;
                mFreqAccel = (1.0f / ((tsAccel - mOffsetAccel) / 1e9f / 100.0f));
                Log.v(MainActivity.TAG(), "Processed 100 Linear Accel samples"+tsAccel);
            }

            // instead of creating a new Runnable everytime and posting it, you may do "sendMessage()" with a Message object reused from a global message pull
            // when the sending/posting target is the main thread, there is an even more convenient way: runOnUiThread(), which is also applicable here
            mMainHandler.post(new Runnable() {
                @RequiresApi(api = Build.VERSION_CODES.N)
                @Override
                public void run() {
                    MainActivity activity = mMainActivity.get();
                    if (activity != null) {
//                        mMainActivity.get().updateAccelUI(tsAccel / 1000L, values, mFreqAccel);
                        arr_linear.add(values);
                        if(arr_linear.size() >= mMainActivity.get().classifier.n_samples &&
                                arr_gyro.size() >= mMainActivity.get().classifier.n_samples &&
                                arr_gravity.size() >= mMainActivity.get().classifier.n_samples ){
                            new PredictionThread().run(arr_linear, arr_gravity, arr_gyro);
                            arr_linear = arr_linear.subList(mMainActivity.get().classifier.n_samples - mMainActivity.get().classifier.n_overlap, mMainActivity.get().classifier.n_samples);
                            arr_gyro = arr_gyro.subList(mMainActivity.get().classifier.n_samples - mMainActivity.get().classifier.n_overlap, mMainActivity.get().classifier.n_samples);
                            arr_gravity = arr_gravity.subList(mMainActivity.get().classifier.n_samples - mMainActivity.get().classifier.n_overlap, mMainActivity.get().classifier.n_samples);
                        }
                        Log.v(MainActivity.TAG(), "Sensor info has been displayed");
//                        String str_data = String.format("%d,%d,%.9e,%.9e,%.9e\n", activity.activity_class, tsAccel / 1000L, values[0], values[1], values[2]);
                        if(list_accel != null){
//                            list_accel.add(str_data);
                            Log.v(MainActivity.TAG(), "Sensor info has been appended");
                        }else{
                            Log.v(MainActivity.TAG(), "list is null object");
                        }
                    } else {
                        Log.w(MainActivity.TAG(), "WeakReference to MainActivity has been lost");
                    }
                }
            });
        }
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            long tsGyro = event.timestamp; // nanosecond
            if(tsGyro != 0) {
                float[] values = event.values.clone();
                if (mCountGyro++ == 0) {
                    mOffsetGyro = tsGyro;
                } else if (mCountGyro == 100) {
                    mCountGyro = 0;
                    mFreqGyro = (1.0f / ((tsGyro - mOffsetGyro) / 1e9f / 100.0f));
                    Log.v(MainActivity.TAG(), "Processed 100 Gyro samples" + tsGyro);
                }

                // instead of creating a new Runnable everytime and posting it, you may do "sendMessage()" with a Message object reused from a global message pull
                // when the sending/posting target is the main thread, there is an even more convenient way: runOnUiThread(), which is also applicable here
                mMainHandler.post(new Runnable() {
                    @RequiresApi(api = Build.VERSION_CODES.N)
                    @Override
                    public void run() {
                        MainActivity activity = mMainActivity.get();
                        if (activity != null) {
//                            mMainActivity.get().updateGyroUI(tsGyro / 1000L, values, mFreqGyro);
//                        mMainActivity.get().append_gyro(tsGyro / 1000000L, values);
                            arr_gyro.add(values);
                            if(arr_linear.size() >= mMainActivity.get().classifier.n_samples &&
                                    arr_gyro.size() >= mMainActivity.get().classifier.n_samples &&
                                    arr_gravity.size() >= mMainActivity.get().classifier.n_samples ){
                                new PredictionThread().run(arr_linear, arr_gravity, arr_gyro);
                                arr_linear = arr_linear.subList(mMainActivity.get().classifier.n_samples - mMainActivity.get().classifier.n_overlap, mMainActivity.get().classifier.n_samples);
                                arr_gyro = arr_gyro.subList(mMainActivity.get().classifier.n_samples - mMainActivity.get().classifier.n_overlap, mMainActivity.get().classifier.n_samples);
                                arr_gravity = arr_gravity.subList(mMainActivity.get().classifier.n_samples - mMainActivity.get().classifier.n_overlap, mMainActivity.get().classifier.n_samples);
                            }
                            Log.v(MainActivity.TAG(), "Sensor info has been displayed");
//                            String str_data = String.format("%d,%d,%.9e,%.9e,%.9e\n", activity.activity_class, tsGyro / 1000L, values[0], values[1], values[2]);
                            if(list_gyro != null){
//                                list_gyro.add(str_data);
                                Log.v(MainActivity.TAG(), "Sensor info has been appended");
                            }else{
                                Log.v(MainActivity.TAG(), "list is null object");
                            }
                        } else {
                            Log.w(MainActivity.TAG(), "WeakReference to MainActivity has been lost");
                        }
                    }
                });
            }
        }
        if (event.sensor.getType() == Sensor.TYPE_GRAVITY) {
            long tsGravity = event.timestamp; // nanosecond
            if(tsGravity != 0) {
                float[] values = event.values.clone();
                if (mCountGravity++ == 0) {
                    mOffsetGravity = tsGravity;
                } else if (mCountGravity == 100) {
                    mCountGravity = 0;
                    mFreqGravity = (1.0f / ((tsGravity - mOffsetGravity) / 1e9f / 100.0f));
                    Log.v(MainActivity.TAG(), "Processed 100 Gravity samples" + tsGravity);
                }

                // instead of creating a new Runnable everytime and posting it, you may do "sendMessage()" with a Message object reused from a global message pull
                // when the sending/posting target is the main thread, there is an even more convenient way: runOnUiThread(), which is also applicable here
                mMainHandler.post(new Runnable() {
                    @RequiresApi(api = Build.VERSION_CODES.N)
                    @Override
                    public void run() {
                        MainActivity activity = mMainActivity.get();
                        if (activity != null) {
//                            mMainActivity.get().updateGravityUI(tsGravity / 1000L, values, mFreqGravity);
//                        mMainActivity.get().append_gravity(tsGravity / 1000000L, values);
                            arr_gravity.add(values);
                            if(arr_linear.size() >= mMainActivity.get().classifier.n_samples &&
                                    arr_gyro.size() >= mMainActivity.get().classifier.n_samples &&
                                    arr_gravity.size() >= mMainActivity.get().classifier.n_samples ){
                                new PredictionThread().run(arr_linear, arr_gravity, arr_gyro);
                                arr_linear = arr_linear.subList(mMainActivity.get().classifier.n_samples - mMainActivity.get().classifier.n_overlap, mMainActivity.get().classifier.n_samples);
                                arr_gyro = arr_gyro.subList(mMainActivity.get().classifier.n_samples - mMainActivity.get().classifier.n_overlap, mMainActivity.get().classifier.n_samples);
                                arr_gravity = arr_gravity.subList(mMainActivity.get().classifier.n_samples - mMainActivity.get().classifier.n_overlap, mMainActivity.get().classifier.n_samples);
                            }
                            Log.v(MainActivity.TAG(), "Sensor info has been displayed");
//                            String str_data = String.format("%d,%d,%.9e,%.9e,%.9e\n", activity.activity_class, tsGravity / 1000L, values[0], values[1], values[2]);
                            if(list_gravity != null){
//                                list_gravity.add(str_data);
                                Log.v(MainActivity.TAG(), "Sensor info has been appended");
                            }else{
                                Log.v(MainActivity.TAG(), "list is null object");
                            }
                        } else {
                            Log.w(MainActivity.TAG(), "WeakReference to MainActivity has been lost");
                        }
                    }
                });
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private class PredictionThread extends Thread {

        @RequiresApi(api = Build.VERSION_CODES.N)
        private void run(List<float[]> arr_accel, List<float[]> arr_gravity, List<float[]> arr_gyro)  {
            // 센서 모니터링 하면서 버퍼 채우고 predict
            int answer = mMainActivity.get().classifier.predict(arr_accel, arr_gravity, arr_gyro);
            Log.d(MainActivity.TAG(), "Current Activity: "+ String.valueOf(answer));
            try{
                URL url = new URL("http://192.168.0.4:50020/post_activity");
                HttpURLConnection con = (HttpURLConnection)url.openConnection();
                con.setRequestMethod("POST");
//                con.setRequestProperty("Accept", "application/json");
                con.setDoOutput(true);
                con.setDoInput(true);
                Map<String,String> arguments = new HashMap<>();
                arguments.put("activity", String.valueOf(answer));
                StringJoiner sj = new StringJoiner("&");
                for(Map.Entry<String,String> entry : arguments.entrySet())
                    sj.add(URLEncoder.encode(entry.getKey(), "UTF-8") + "="
                            + URLEncoder.encode(entry.getValue(), "UTF-8"));
                byte[] out = sj.toString().getBytes(StandardCharsets.UTF_8);
                int length = out.length;

                con.setFixedLengthStreamingMode(length);
                con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                con.connect();
                try(OutputStream os = con.getOutputStream()) {
                    os.write(out);
                }

//                OutputStream outputStream = con.getOutputStream();
//                PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, String.valueOf(2)), true);
//                writer.flush();
//                writer.close();
//                JSONObject jsonObject = new JSONObject();
//                jsonObject.put("activity", String.valueOf(answer));
//                String jsonInputString = "{\"activity\": \""+ String.valueOf(answer)+"\"}";
//                Log.d(MainActivity.TAG(), "Sending : "+ jsonInputString);
//                try(OutputStream os = con.getOutputStream()) {
//                    byte[] input = jsonInputString.getBytes("utf-8");
//                    os.write(input, 0, input.length);
//                }
//                try(BufferedReader br = new BufferedReader(
//                        new InputStreamReader(con.getInputStream(), "utf-8"))) {
//                    StringBuilder response = new StringBuilder();
//                    String responseLine = null;
//                    while ((responseLine = br.readLine()) != null) {
//                        response.append(responseLine.trim());
//                    }
//                    System.out.println(response.toString());
//                }
//                DataOutputStream wr = new DataOutputStream(con.getOutputStream());
//                wr.writeBytes(jsonObject.toString());
//                wr.flush();
//                wr.close();

            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (ProtocolException e) {
                e.printStackTrace();
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }
//    public void append_accel(long timestamp /* millisecond */, float[] values){
//
//    }
//
//    public void append_gravity(long timestamp /* millisecond */, float[] values){
//        String str_data = String.format("%d,%d,%.9e,%.9e,%.9e\n", activity_class, timestamp, values[0], values[1], values[2]);
//        list_gravity.add(str_data);
//    }
//
//    public void append_gyro(long timestamp /* millisecond */, float[] values){
//        String str_data = String.format("%d,%d,%.9e,%.9e,%.9e\n", activity_class, timestamp, values[0], values[1], values[2]);
//        list_gyro.add(str_data);
//    }
}
