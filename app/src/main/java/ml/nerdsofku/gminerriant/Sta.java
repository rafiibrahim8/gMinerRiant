package ml.nerdsofku.gminerriant;

import android.util.Log;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.regex.Pattern;

public class Sta {

    public static String getDateTime(){
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HHmmss");
        return dateFormat.format(new Date());
    }

    public static void sendSensorData(final OutputStream outputStream, float[] values, long  startTime){

        final byte[] bytes = new byte[16];
        float elipseTime = System.currentTimeMillis() - startTime;
        System.arraycopy(toByteArray(values[0]),0,bytes,0,4); //x
        System.arraycopy(toByteArray(values[1]),0,bytes,4,4); //y
        System.arraycopy(toByteArray(values[2]),0,bytes,8,4); //z
        System.arraycopy(toByteArray(elipseTime/1000),0,bytes,12,4); //t in sec

        new Thread(new Runnable() {
            @Override
            public void run() {
                sendData(outputStream,bytes);
            }
        }).start();
    }

    public static void sendData(OutputStream stream,byte[] bytes){
        try {
            stream.write(bytes);
        } catch (Exception e) {
            Log.e("Exception",e.getLocalizedMessage());
        }
    }

    public static boolean isValidIPv4(String ip){
        if(ip == null) return false;

        // https://www.regextester.com/95309
        return Pattern.matches("^(?:(?:^|\\.)(?:2(?:5[0-5]|[0-4]\\d)|1?\\d?\\d)){4}$", ip);

    }


    public static byte[] toByteArray(float f){
        byte[] bytes = new byte[4];
        ByteBuffer.wrap(bytes).putFloat(f);
        return bytes;
    }

    public static void sleepms(int t){
        try {
            Thread.sleep(t);
        } catch (InterruptedException ex) {
            //doSomething
        }
    }


    public static String formatCSV(float[] values, long startTime) {
        float elipseTime = System.currentTimeMillis() - startTime;
        return elipseTime/1000+","+values[0]+","+values[1]+","+values[2]+"\r\n";
    }
}
