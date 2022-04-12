/*
https://stackoverflow.com/questions/7005756/update-ui-from-another-thread-in-android
 */
package ml.nerdsofku.gminerriant;

import android.Manifest;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private MyService myService;
    private String TAG;

    private TextView serv;
    private CheckBox checkBox;
    private EditText ip;
    private Button button;
    private Intent intent;
    private Context context;

    private BroadcastReceiver broadcastReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        context = this;
        TAG = this.getClass().getSimpleName();
        initView();
        initService();
        initBroadCast();
        initRefresh();
        getPermission();
        registerReceiver(broadcastReceiver,new IntentFilter("upppp"));
        Log.e("FILE", Environment.getExternalStorageDirectory().getAbsolutePath());
    }

    private void initBroadCast() {
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateGUI();
            }
        };
    }

    private void initRefresh() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true){
                    context.sendBroadcast(new Intent("upppp"));
                    if(!isMyServiceRunning(myService.getClass())){
                        myService.releaseWakeLock();
                    }
                    Sta.sleepms(100);
                }
            }
        }).start();
    }

    private void initView() {
        TextView gyro = findViewById(R.id.gyro);
        TextView acc = findViewById(R.id.acc);
        serv = findViewById(R.id.serv);
        ip = findViewById(R.id.ip);
        button = findViewById(R.id.button);
        checkBox = findViewById(R.id.willSaveOffline);

        SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if(sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)==null){
            gyro.setText(R.string.gyro0);
            button.setEnabled(false);
        }
        if(sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)==null){
            acc.setText(R.string.gyro0);
            button.setEnabled(false);
        }

        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked)
                    ip.setVisibility(View.GONE);
                else
                    ip.setVisibility(View.VISIBLE);
            }
        });

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(isMyServiceRunning(myService.getClass())){
                    stopService(intent);
                }
                else{
                    startTheService();
                }
            }
        });
    }

    private void startTheService() {
        if(checkBox.isChecked() && !hasWritePermission()){
            getPermission();
        }
        else if(checkBox.isChecked()){
            mkAppDir();
            intent.putExtra("ipAddr","");
            intent.putExtra("willSave",checkBox.isChecked());
            startService(intent);
        }
        else if(Sta.isValidIPv4(ip.getText().toString()) && !checkBox.isChecked()){
            intent.putExtra("ipAddr",ip.getText().toString());
            intent.putExtra("willSave",checkBox.isChecked());
            startService(intent);
        }else {
            toast("Invalid IP");
        }
    }

    private void toast(String msg){
        Toast.makeText(this,msg, Toast.LENGTH_SHORT).show();
    }

    private void initService() {
        myService = new MyService(this);
        intent = new Intent(this,myService.getClass());
        updateGUI();
    }

    private void updateGUI() {
        if(isMyServiceRunning(myService.getClass())){
            button.setText(R.string.stop);
            serv.setText(R.string.running);
            serv.setTextColor(Color.GREEN);
            ip.setEnabled(false);
        }
        else{
            ip.setEnabled(true);
            button.setText(R.string.start);
            serv.setText(R.string.notRunning);
            serv.setTextColor(Color.RED);
        }
    }

    // https://github.com/arvi/android-never-ending-background-service
    private boolean isMyServiceRunning(Class <?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if(serviceClass.getName().equals(service.service.getClassName())) {
                //Log.i(TAG, "isMyServiceRunning? " + true + "");
                return true;
            }
        }

        //Log.i(TAG, "isMyServiceRunning? " + false + "");
        return false;
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    private void getPermission(){
        if (!hasWritePermission()) {

            // Permission is not granted
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            } else {

                // No explanation needed; request the permission
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        1);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        } else {
            mkAppDir();
        }
    }

    private boolean hasWritePermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }


    private void mkAppDir(){
        File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/"+getResources().getString(R.string.app_name));
        if(!file.exists()){
            file.mkdir();
        }
    }
}
