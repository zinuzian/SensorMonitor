package com.example.sensormonitor_java;

import static libsvm.svm.svm_predict_values;

import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;
import libsvm.svm_parameter;
import libsvm.svm_problem;

public class ActivityClassifier {
    public int n_samples;
    public int n_overlap;
    public int n_features;
    public int n_class = 7;
    public String svm_kernel;
    public double gamma;
    public double c;
    static final String svm_type_table[] = {"c_svc","nu_svc","one_class","epsilon_svr","nu_svr"};

//    private ArrayList<Double> accel_x_mean=null;
//    private ArrayList<Double> accel_y_mean=null;
//    private ArrayList<Double> accel_z_mean=null;
//    private ArrayList<Double> gravity_x_mean=null;
//    private ArrayList<Double> gravity_y_mean=null;
//    private ArrayList<Double> gravity_z_mean=null;
//    private ArrayList<Double> gyro_x_mean=null;
//    private ArrayList<Double> gyro_y_mean=null;
//    private ArrayList<Double> gyro_z_mean=null;

    private ArrayList<double[]> activity_features=null;
    private ArrayList<Integer> activity_class=null;

    private ArrayList<double[]> sensor_buffer;

    private boolean light_mode = true;
    private svm_model model;
    private int[] n_activity;

    // Feature Extractors
    private static double mean(double[] freq){
        double s = 0.0;
        int n = freq.length;
        for(int i=0;i<n;i++){
            s += freq[i] / n;
        }
        return s;
    }

    private static double informationEntropy(double[] freq, int content_len){
        double entropy = 0.0;
        int n = freq.length;
        for(int i=0;i<n;i++) {
            double p = 1.0 * freq[i] / content_len;
            entropy -= p * Math.log(p) / Math.log(2);
        }
        return entropy;
    }

    private static double totalEnergy_freqSpec(double[] freq){
        double s = 0.0;
        int n = freq.length;
        for(int i=0;i<n;i++){
            s += (freq[i] * freq[i]) / n;
        }
        return s;
    }

    private static double correlation(double[] freq1, double[] freq2){
        double s = 0.0;
        int n = freq1.length;
        double m1 = mean(freq1);
        double m2 = mean(freq1);
        double s1 = 0;
        double s2 = 0;

        for(int i=0;i<n;i++){

        }

        return s;
    }

    public svm_node[] buildPoint(double[] feature) {
        // build single data point
        svm_node[] point = new svm_node[this.n_features];
        for(int i=0;i<feature.length;i++) {
            point[i] = new svm_node();
            point[i].index = i + 1;
            point[i].value = feature[i];
        }

        return point;
    }


    public svm_model fit() {

        svm_node[][] features = new svm_node[this.activity_features.size()][this.n_features];
        double[] classes = new double[this.activity_features.size()];

        for(int i=0;i<this.activity_features.size();i++){
            for(int j=0;j<this.n_features;j++){
                features[i][j] = new svm_node();
                features[i][j].index = j+1;
                features[i][j].value = this.activity_features.get(i)[j];
            }
            classes[i] = this.activity_class.get(i);
        }
        // Build Parameters
        svm_parameter param = new svm_parameter();
        param.svm_type    = svm_parameter.NU_SVC;
        if(this.svm_kernel == "rbf") param.kernel_type = svm_parameter.RBF;
        else param.kernel_type = svm_parameter.LINEAR;
        param.gamma       = this.gamma;
        param.nu          = this.c;
        param.cache_size  = 100;

        // Build Problem
        svm_problem problem = new svm_problem();
        problem.x = features;
        problem.l = features.length;
        problem.y = classes;

        // Build Model
        svm_model trained_model = svm.svm_train(problem, param);
        Log.v(MainActivity.TAG(), "nr class = "+String.valueOf(trained_model.nr_class));

        double[] target = new double[problem.l];
        svm.svm_cross_validation(problem, param, 10, target );
        int[][] confMat = new int[7][7];
        for(int i=0;i<7;i++){for(int j=0;j<7;j++){confMat[i][j] = 0;}}
        for(int i=0;i<problem.l;i++){
            confMat[(int)(target[i])][(int)(classes[i])]++;
        }
        for(int i=0;i<7;i++){
            for(int j=0;j<7;j++){
                Log.d(MainActivity.TAG(), String.valueOf(confMat[i][j]));
            }
        }
        return trained_model;
    }



    public double transform(svm_model model, svm_node[] nodes) {
        double[] scores = new double[2];
        double result = svm_predict_values(model, nodes, scores);

        return scores[0];
    }

    public ActivityClassifier(int n_samples, int n_overlap, double gamma, double c, boolean light_mode){
        this.n_samples = n_samples;
        this.n_overlap = n_overlap;
        this.svm_kernel = "RBF";
        this.gamma = gamma;
        this.c = c;
        this.light_mode = light_mode;
        if(light_mode){
            this.n_features = 9;
        }else{
            this.n_features = 27+9;
        }
        this.n_activity = new int[7];
        for(int i=0;i<7;i++){
            n_activity[i] = 0;
        }
    }
    public double svm_predict(svm_node[] x)
    {
        int nr_class = this.model.nr_class;
        double[] dec_values;
        if(this.model.param.svm_type == svm_parameter.ONE_CLASS ||
                this.model.param.svm_type == svm_parameter.EPSILON_SVR ||
                this.model.param.svm_type == svm_parameter.NU_SVR)
            dec_values = new double[1];
        else
            dec_values = new double[nr_class*(nr_class-1)/2];
        double pred_result = svm_predict_values(this.model, x, dec_values);
        return pred_result;
    }


    public void parse_and_sample(String[] filenames){
        File[] files = new File[filenames.length];
        BufferedReader[] br = new BufferedReader[files.length];
        long[] prev_ts=new long[files.length];
        double[][] arr_x = new double[files.length][this.n_samples];
        double[][] arr_y = new double[files.length][this.n_samples];
        double[][] arr_z = new double[files.length][this.n_samples];


//        if(accel_x_sampled == null) accel_x_sampled = new ArrayList<double[]>();
//        if(accel_y_sampled == null) accel_y_sampled = new ArrayList<double[]>();
//        if(accel_z_sampled == null) accel_z_sampled = new ArrayList<double[]>();
//        if(gravity_x_sampled == null) gravity_x_sampled = new ArrayList<double[]>();
//        if(gravity_y_sampled == null) gravity_y_sampled = new ArrayList<double[]>();
//        if(gravity_z_sampled == null) gravity_z_sampled = new ArrayList<double[]>();
//        if(gyro_x_sampled == null) gyro_x_sampled = new ArrayList<double[]>();
//        if(gyro_y_sampled == null) gyro_y_sampled = new ArrayList<double[]>();
//        if(gyro_z_sampled == null) gyro_z_sampled = new ArrayList<double[]>();
        if(activity_features == null) activity_features = new ArrayList<double[]>();
        if(activity_class == null) activity_class = new ArrayList<Integer>();


        boolean newSeq;
        boolean line_loaded;
        long start_ts;
        int nth_sample = 0;
        int n_seq = 0;
        String[][] lines = new String[files.length][];
        for(int i=0;i< files.length;i++) files[i] = new File(filenames[i]);
        try{
            String[] line = new String[files.length];
            for(int i=0;i< files.length;i++) {
                br[i] = new BufferedReader(new FileReader(files[i]));
            }
            line_loaded = true;
            newSeq = true;
            while((line[0]=br[0].readLine())!=null && (line[1]=br[1].readLine())!=null && (line[2]=br[2].readLine())!=null){
                for(int i=0;i< files.length;i++){
                    lines[i] = line[i].split(",");
                    if(Long.valueOf(lines[i][1].trim()) - prev_ts[i] > 10100L){
                        newSeq = true;
                    }
                    prev_ts[i] = Long.valueOf(lines[i][1].trim());
                }
                if(newSeq){
                    start_ts = Math.max(Math.max(Long.valueOf(lines[0][1].trim()), Long.valueOf(lines[1][1].trim())), Long.valueOf(lines[2][1].trim()));
                    Log.d(MainActivity.TAG(), "New Sequence found at "+String.valueOf(start_ts));
                    for(int i=0;i< files.length;i++){
                        if(Math.abs(prev_ts[i] - start_ts) < 5500L) continue;
                        while((line[i]=br[i].readLine()) != null){
                            prev_ts[i] = Long.valueOf(line[i].split(",")[1].trim());
                            if(Math.abs(prev_ts[i] - start_ts) < 5500L) break;
                        }
                        lines[i] = line[i].split(",");
                    }
                    nth_sample = 0;
                    n_seq++;
                    newSeq = false;
                }
                for(int i=0;i< files.length;i++) prev_ts[i] = Long.valueOf(lines[i][1].trim());
                if(nth_sample == 0){
                    Log.v(MainActivity.TAG(), "New Sequence("+String.valueOf(n_seq)+") found from activity "+String.valueOf(lines[0][0].trim()));
                }
                for(int i=0;i< files.length;i++){
                    arr_x[i][nth_sample] = Double.valueOf(lines[i][2].trim());
                    arr_y[i][nth_sample] = Double.valueOf(lines[i][3].trim());
                    arr_z[i][nth_sample] = Double.valueOf(lines[i][4].trim());
                }
                nth_sample++;
//                Log.v(MainActivity.TAG(), "New Sequence ("+String.valueOf(nth_sample)+") th sample");
                if(nth_sample == this.n_samples){
                    // flush
//                    accel_x_sampled.add(arr_x[0]);
//                    accel_y_sampled.add(arr_y[0]);
//                    accel_z_sampled.add(arr_z[0]);
//                    gravity_x_sampled.add(arr_x[1]);
//                    gravity_y_sampled.add(arr_y[1]);
//                    gravity_z_sampled.add(arr_z[1]);
//                    gyro_x_sampled.add(arr_x[2]);
//                    gyro_y_sampled.add(arr_y[2]);
//                    gyro_z_sampled.add(arr_z[2]);
                    double[] feature = new double[this.n_features];
                    int activity_class_num = Integer.valueOf(lines[0][0].trim());
                    feature[0] = mean(arr_x[0]);
                    feature[1] = mean(arr_y[0]);
                    feature[2] = mean(arr_z[0]);
                    feature[3] = mean(arr_x[1]);
                    feature[4] = mean(arr_y[1]);
                    feature[5] = mean(arr_z[1]);
                    feature[6] = mean(arr_x[2]);
                    feature[7] = mean(arr_y[2]);
                    feature[8] = mean(arr_z[2]);

                    activity_features.add(feature);
                    activity_class.add(activity_class_num);

                    n_activity[activity_class_num]++;

                    // copy overlapped section
                    for(int i=0;i< files.length;i++){
                        for(int j=0;j<this.n_overlap;j++){
                            arr_x[i][j] = arr_x[i][arr_x[i].length-this.n_overlap+j];
                            arr_y[i][j] = arr_y[i][arr_y[i].length-this.n_overlap+j];
                            arr_z[i][j] = arr_z[i][arr_z[i].length-this.n_overlap+j];
                        }
                    }
                    nth_sample = this.n_overlap;
                }
            }
            for(int i=0;i<7;i++){
                Log.v(MainActivity.TAG(), "# of Activity "+String.valueOf(i)+" : "+String.valueOf(n_activity[i]));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void load(){
//        _type = {'c-svc':0, 'nu-svc':1}
//        _kernel={'linear':0, 'poly':1, 'rbf':2, 'sigmoid':3, 'precomp':4}
        File modelFile = new File(MainActivity.getDirExternRoot(), String.format("cv53.73_ns%d_no%d_nf9_s1_t2_n0.5.bin",n_samples,n_overlap));
        try{
            model = svm.svm_load_model(modelFile.getPath());
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public int predict(List<float[]> arr_linear, List<float[]> arr_gravity, List<float[]> arr_gyro){
        long startTime = System.currentTimeMillis();
        double[] feature = new double[this.n_features];

        double[] temp_arr = new double[arr_linear.size()];
        for(int i=0;i<arr_linear.size();i++) temp_arr[i] = (double)arr_linear.get(i)[0];
        feature[0] = mean(temp_arr);

        temp_arr = new double[arr_linear.size()];
        for(int i=0;i<arr_linear.size();i++) temp_arr[i] = (double)arr_linear.get(i)[1];
        feature[1] = mean(temp_arr);

        temp_arr = new double[arr_linear.size()];
        for(int i=0;i<arr_linear.size();i++) temp_arr[i] = (double)arr_linear.get(i)[2];
        feature[2] = mean(temp_arr);

        temp_arr = new double[arr_gravity.size()];
        for(int i=0;i<arr_gravity.size();i++) temp_arr[i] = (double)arr_gravity.get(i)[0];
        feature[3] = mean(temp_arr);

        temp_arr = new double[arr_gravity.size()];
        for(int i=0;i<arr_gravity.size();i++) temp_arr[i] = (double)arr_gravity.get(i)[1];
        feature[4] = mean(temp_arr);

        temp_arr = new double[arr_gravity.size()];
        for(int i=0;i<arr_gravity.size();i++) temp_arr[i] = (double)arr_gravity.get(i)[2];
        feature[5] = mean(temp_arr);

        temp_arr = new double[arr_gyro.size()];
        for(int i=0;i<arr_gyro.size();i++) temp_arr[i] = (double)arr_gyro.get(i)[0];
        feature[6] = mean(temp_arr);

        temp_arr = new double[arr_gyro.size()];
        for(int i=0;i<arr_gyro.size();i++) temp_arr[i] = (double)arr_gyro.get(i)[1];
        feature[7] = mean(temp_arr);

        temp_arr = new double[arr_gyro.size()];
        for(int i=0;i<arr_gyro.size();i++) temp_arr[i] = (double)arr_gyro.get(i)[2];
        feature[8] = mean(temp_arr);

        svm_node[] prob = buildPoint(feature);
        int answer = (int)(svm.svm_predict(this.model, prob));
        long endTime = System.currentTimeMillis();
        Log.v(MainActivity.TAG(), "Time(ms) : "+String.valueOf(endTime-startTime));
        return answer;`
    }

    public svm_model getModel(){
        return this.model;
    }


}