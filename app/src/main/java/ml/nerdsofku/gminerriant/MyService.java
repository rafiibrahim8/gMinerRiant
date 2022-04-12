package ml.nerdsofku.gminerriant;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import com.r3bl.stayawake.HandleNotifications;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

import static android.util.Log.d;
import static com.r3bl.stayawake.HandleNotifications.isPreAndroidO;

public class MyService extends Service implements SensorEventListener {

    public static final int SAM_FREQ = 100; //Samsung phone max
    public static final int PORT_GYRO = 17916;
    public static final int PORT_ACC = 17908;
    public static final int PORT_COM = 17938;
    public static final boolean TCP_NODELAY = true;

    public static final String TAG = "SA_MyService";
    private SensorManager sensorManager;

    private long startTime;

    private OutputStream outCom,outGy,outAcc;
    private InputStream inCom;
    private AtomicBoolean isConnected,willSend,willSave;
    private String ipv4;
    private Context context;
    private boolean willTransmit;
    private PrintWriter gyLoacal,accLocal;

    private PowerManager.WakeLock wakeLock;

    public MyService(Context applicationContext) {
        super();
        context = applicationContext;

    }
    public MyService(){

    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        initService();
    }

    private void initService(){
        isConnected = new AtomicBoolean(false);
        willSend = new AtomicBoolean(false);
        willSave= new AtomicBoolean(false);
        int samplingPreiodUs = 1000000/SAM_FREQ;

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorManager.registerListener(this,sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION),samplingPreiodUs);
        sensorManager.registerListener(this,sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),samplingPreiodUs);

    }

    @Override
    public void onDestroy() {
        if(willSave.get()){
            willSave.set(false);
            accLocal.close();
            gyLoacal.close();
        }
        super.onDestroy();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        ipv4 = intent.getStringExtra("ipAddr");
        willTransmit = !ipv4.isEmpty();
        willSave.set(intent.getBooleanExtra("willSave",true));
        if(willSave.get()){
            try {
                File dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/"+getResources().getString(R.string.app_name)+"/"+Sta.getDateTime());
                dir.mkdir();

                gyLoacal = new PrintWriter(new BufferedWriter(new FileWriter(new File(dir.getAbsolutePath()+"/gyro.csv"),true)));
                accLocal = new PrintWriter(new BufferedWriter(new FileWriter(new File(dir.getAbsolutePath()+"/accelerometer.csv"),true)));

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if(willTransmit)
            new ConnectServer().execute();
        if (isPreAndroidO()) {
            HandleNotifications.PreO.createNotification(this);
        } else {
            HandleNotifications.O.createNotification(this);
        }
        acquireWakeLock();
        startTime = System.currentTimeMillis();
        return START_NOT_STICKY;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if(event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION && willSend.get()){
            Sta.sendSensorData(outAcc,event.values,startTime);
        }

        else if(event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION && willSave.get()){
            accLocal.print(Sta.formatCSV(event.values,startTime));
        }

        if(event.sensor.getType() == Sensor.TYPE_GYROSCOPE && willSend.get()){
            Sta.sendSensorData(outGy,event.values,startTime);
        }

        else if(event.sensor.getType() == Sensor.TYPE_GYROSCOPE && willSave.get()){
            gyLoacal.print(Sta.formatCSV(event.values,startTime));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public boolean isConnected(){
        return isConnected.get();
    }

    private void startThreads() {
        //com Listen Thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true){
                    listenCmd();
                }
            }
        }).start();
    }

    private void listenCmd(){
        byte[] comm;
        try {
            if(inCom.available()<1){ //1 bytes
                Sta.sleepms(5);
                return;
            }
            comm = new byte[1];
            inCom.read(comm);
        } catch (IOException ex) {
            //doSomething
            return;
        }

        switch(new String(comm)){
            case "S":
                Sta.sendData(outCom,"SA".getBytes());
                startTime = System.currentTimeMillis();
                willSend.set(true);
                break;
            case "P":
                Sta.sendData(outCom,"PA".getBytes());
                willSend.set(false);
                break;
        }

    }

    private void acquireWakeLock() {
        if (wakeLock == null) {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "app:"+TAG);
            wakeLock.acquire();
            d(TAG, "acquireWakeLock: ");
        }
    }

    public void releaseWakeLock() {
        if (wakeLock != null) {
            wakeLock.release();
            wakeLock = null;
            d(TAG, "releaseWakeLock: ");
        }
    }

    //class definations
    class DisconnectServer extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPreExecute() {}

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                willSend.set(false);
                isConnected.set(false);
                Sta.sendData(outCom,"DX".getBytes());
                Sta.sleepms(200);
                outAcc.close();
                outGy.close();
                outCom.close();
                inCom.close();
            } catch (IOException e) {
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {}
    }
    class ConnectServer extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPreExecute() {}

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                Socket sCom = new Socket(ipv4,PORT_COM);
                Sta.sleepms(250);
                Socket sAcc = new Socket(ipv4,PORT_ACC);
                Sta.sleepms(250);
                Socket sGy = new Socket(ipv4,PORT_GYRO);
                sCom.setTcpNoDelay(TCP_NODELAY);
                sAcc.setTcpNoDelay(TCP_NODELAY);
                sGy.setTcpNoDelay(TCP_NODELAY);
                outAcc = sAcc.getOutputStream();
                outGy = sGy.getOutputStream();
                outCom = sCom.getOutputStream();
                inCom = sCom.getInputStream();
                isConnected.set(true);
            } catch (IOException e) {
                Log.e("TAG",ipv4+e.getLocalizedMessage());
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            if(!isConnected.get()){
                stopSelf();
            }
            startThreads();
        }
    }

}
