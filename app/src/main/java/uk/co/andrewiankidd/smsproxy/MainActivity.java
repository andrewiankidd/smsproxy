package uk.co.andrewiankidd.smsproxy;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.telephony.SmsManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.EditText;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    // UI elements
    private static EditText logView;
    private static EditText authKeyText;
    private static MainActivity ma;

    // Preferences
    SharedPreferences settingReader;
    SharedPreferences.Editor settingWriter;

    // auth
    public static String authKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Actvity
        ma = MainActivity.this;

        // Define LogView
        logView = findViewById(R.id.logView);

        // Define authKeyText
        authKeyText = findViewById(R.id.authKeyText);

        authKeyText.addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable s) {}

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                authKey = s.toString();
                settingWriter.putString("authKey", authKey);
                settingWriter.commit();
            }
        });

        settingReader = getSharedPreferences("smsproxyPreferences", MODE_PRIVATE);
        settingWriter = settingReader.edit();

        // Check various permissions
        initPermCheck();
    }



    private void startServer(){
        // Get Unique ID;
        authKey = settingReader.getString("authKey", UUID.randomUUID().toString());

        // Save to input
        authKeyText.setText(authKey);
        logger(authKey);

        // Start ServerThread
        Thread socketServerThread = new Thread(new SocketServerThread(getIpAddress(), 1993));
        socketServerThread.start();
    }

    private void initPermCheck() {

        String[] permissions = {
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.INTERNET,
            Manifest.permission.SEND_SMS
        };

        if(hasPermissions(this, permissions)){
            logger("Have all necessary permissions.");
            startServer();
        }
        else{
            ActivityCompat.requestPermissions(this, permissions, 0);
        }

    }

    public static boolean hasPermissions(Context context, String... permissions) {
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    logger("ASKING FOR PERMISSION: " + permission);
                    return false;
                }
            }
        }
        return true;
    }

    public static void sendSMS(String phoneNumber, String message) {
        SmsManager sms = SmsManager.getDefault();
        logger("NEW SMS - NUMBER " + phoneNumber + ", MSG: " + message);
        sms.sendTextMessage(phoneNumber, null, message, null, null);

    }

    public static void logger(String text)
    {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HH:mm:ss");
        String timestamp = sdf.format(new Date());
        final String msg = "[" + timestamp + "] " + text;
        MainActivity.ma.runOnUiThread(new Runnable() {
            public void run() {
                Log.i("smsproxy", msg);
                try {
                    MainActivity.logView.append(msg + System.lineSeparator());
                }
                catch(Exception e){
                    Log.e("smsproxy", "Failed to write to logView: " + e.getMessage());
                }
            }
        });
    }

    private String getIpAddress() {
        WifiManager wifiMgr = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (!wifiMgr.isWifiEnabled())
        {
            wifiMgr.setWifiEnabled(true);
        }

        String ipAddress = "0.0.0.0";
        long startTime = System.currentTimeMillis();
        while (ipAddress.equals("0.0.0.0") && (System.currentTimeMillis()-startTime<60000))
        {
            ipAddress = Formatter.formatIpAddress(wifiMgr.getConnectionInfo().getIpAddress());
        }
        return ipAddress;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Cleanup Server
        killServer();
    }

    private void killServer() {

    }

}

class SocketServerThread extends Thread {

    // Server specific variables
    private ServerSocket socketServer;
    private Integer socketServerPort;
    private String socketServerIp;

    SocketServerThread(String serverIp, Integer port)
    {
        socketServerIp = serverIp;
        socketServerPort = port;
    }


    @Override
    public void run() {

        // Start Server
        startServer();
    }

    private void startServer() {

        try {
            // Create new Isntance of the server
            socketServer = new ServerSocket(socketServerPort);
            MainActivity.logger("Server Started: " + socketServerIp + ":" + socketServerPort);
        }
        catch (IOException e)
        {
            e.printStackTrace();
            MainActivity.logger("FAILED TO INIT SERVER");
            MainActivity.logger(e.getMessage());
        }

        while (true) {
            Socket socket = null;
            String HTTP_STATUS = "";
            String HTTP_BODY = "";
            try {
                socket = socketServer.accept();
                MainActivity.logger("REC from " + socket.getInetAddress() + ":" + socket.getPort());

                //socket is an instance of Socket
                InputStream is = socket.getInputStream();
                InputStreamReader isReader = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(isReader);

                //code to read and print headers
                String headerLine = null;
                while((headerLine = br.readLine()).length() != 0){
                    System.out.println(headerLine);
                }

                //code to read the post payload data
                StringBuilder payload = new StringBuilder();
                while(br.ready()){
                    payload.append((char) br.read());
                }
                String payloadStr = payload.toString();
                MainActivity.logger("body: " + payloadStr);

                // Parse
                JSONObject payloadObj = new JSONObject(payloadStr);
                MainActivity.logger("parsed body successfully");

                HTTP_STATUS = "403 Forbidden";
                HTTP_BODY = "Invalid Authentication Header";
                if (payloadObj.has("auth"))
                {
                    if (payloadObj.getString("auth").equals(MainActivity.authKey))
                    {
                        MainActivity.sendSMS(payloadObj.getString("to"), payloadObj.getString("message"));

                        HTTP_STATUS = "200 OK";
                        HTTP_BODY = "Request Received";
                    }

                }

            } catch (Exception e) {
                HTTP_STATUS = "500 - Internal Server Error";
                HTTP_BODY = e.getMessage();
                MainActivity.logger(e.getMessage());
                e.printStackTrace();
            }
            finally
            {
                MainActivity.logger(HTTP_STATUS + System.lineSeparator() + HTTP_BODY);
                try {
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());

                    out.writeBytes("HTTP/1.1 " + HTTP_STATUS + System.lineSeparator());
                    out.writeBytes("Content-Type: application/json" + System.lineSeparator() + System.lineSeparator());
                    out.writeBytes("{\"result\": \""+ HTTP_BODY +"\"}");
                    out.flush();
                    out.close();

                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}