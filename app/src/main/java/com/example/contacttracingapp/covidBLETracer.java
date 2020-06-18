package com.example.contacttracingapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class covidBLETracer extends Service {
    private static final String TAG = "bluetooth";

    List<ScanFilter> filters = new ArrayList<>();

    AdvertiseSettings settings = new AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
            .setConnectable(false)
            .build();

    private BluetoothLeAdvertiser advertiser = BluetoothAdapter.getDefaultAdapter().getBluetoothLeAdvertiser();
    private BluetoothLeScanner mBluetoothLeScanner = BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner();
    private JSONObject closeProximityUUIDs = new JSONObject();

    private UUID userUUID;
    private String userStatus = "";

    public IBinder onBind(Intent intent) {
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void onCreate() {
        //Construct the service notification required for background services in newer android api's
        String NOTIFICATION_CHANNEL_ID = "com.example.contacttracingapp";
        String channelName = "My Background Service";
        NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
        chan.setLightColor(Color.BLUE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);

        NotificationManager serviceManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        assert serviceManager != null;
        serviceManager.createNotificationChannel(chan);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        Notification serviceNotification = notificationBuilder.setOngoing(true)
                .setSmallIcon(R.drawable.notification_icon)
                .setContentTitle("Corona virus contact tracing service")
                .setPriority(NotificationManager.IMPORTANCE_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();

        //Starts notification, needs to start within 5 seconds of serviceReceiver trigger
        startForeground(2, serviceNotification);

        //Load in string from file and convert to JSON for parsing
        try {
            JSONObject userDataJson = new JSONObject(fileReadWrite.loadFromFile(this, "userData.json"));
            userUUID = UUID.fromString(userDataJson.getString("uuid"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startLeScan();
        startLeAdvert();

        checkStatus();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mBluetoothLeScanner.stopScan(mScanCallback);
    }

    public void startLeScan() {
        //Setup scan settings, mode set to Low Power for battery decreased use
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .build();

        mBluetoothLeScanner.startScan(filters, settings, mScanCallback);
    }

    private void checkStatus() {
        final JSONObject userUUIDJson = new JSONObject();

        try {
            userUUIDJson.put("uuid", userUUID.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL("http://192.168.0.90:3000/getStatus");
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                    //Setup connection preferences
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                    connection.setRequestProperty("Accept", "application/json");
                    connection.setDoOutput(true);
                    connection.setDoInput(true);

                    DataOutputStream os = new DataOutputStream(connection.getOutputStream());

                    os.writeBytes(userUUIDJson.toString());

                    os.flush();
                    os.close();

                    InputStreamReader input = new InputStreamReader(connection.getInputStream());
                    BufferedReader bufferedReader = new BufferedReader(input);
                    JSONObject userDataJson;

                    userStatus = bufferedReader.readLine();

                    //If status has changed and status is RED, post UUIDs of potentially exposed users
                    if (connection.getResponseCode() == 200) {
                        //Load userData from file
                        userDataJson = new JSONObject(fileReadWrite.loadFromFile(getApplicationContext(), "userData.json"));

                        //Change JSON status value then write file back
                        userDataJson.remove("status");
                        userDataJson.put("status", userStatus);

                        fileReadWrite.writeToFile(userDataJson.toString(), "userData.json", getApplicationContext());
                    }

                    //Throw exception if code represents connection failure
                    if (connection.getResponseCode() != 200){
                        throw new ConnectException();
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                    Toast toast = Toast.makeText(getApplicationContext(), "Connection error occurred! Please make sure you have an active internet connection, then try again.", Toast.LENGTH_LONG);
                    toast.show();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

        //Every 20 seconds check the current status of the user via the uuid
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(thread, 0, 20, TimeUnit.SECONDS);
    }

    public void startLeAdvert() {
        AdvertiseCallback advertisingCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                super.onStartSuccess(settingsInEffect);
            }

            @Override
            public void onStartFailure(int errorCode) {
                Log.e("BLE", "Advertising onStartFailure: " + errorCode);
                super.onStartFailure(errorCode);
            }
        };

        //Add user specific uuid to parcel for broadcasting
        ParcelUuid uuidParcel = new ParcelUuid(userUUID);

        //Setup advertisement data preferences. Takes in user specific UUID
        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(uuidParcel)
                .build();


        advertiser.startAdvertising(settings, data, advertisingCallback);
    }

    private ScanCallback mScanCallback = new ScanCallback() {
        //Truncates data to derive UUID, no built in function for get UUID so this was the solution
        private UUID trimUUID(String data) {
            try {
                return UUID.fromString(data.substring(data.indexOf("mServiceUuids=[") + 15, data.indexOf("mServiceUuids=[") + 51));
            } catch (Exception e) {
                Log.i(TAG, "trimUUID: returned UUID value is invalid");
                return null;
            }
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);

            //If the scanned devices signal (representative of distance) is < value or null break
            if (result == null || result.getRssi() < -70)
                return;

            //Process trim on result UUID, getUUID is not a function of result by default
            UUID uuid = trimUUID(result.toString());

            //Prevents potential null object errors
            if (uuid != null) {
                try {
                    if (potentialProximityUuids.toString().contains(uuid.toString())) {
                        long prevTime = (long) potentialProximityUuids.get(uuid.toString());

                        //Check how long the user had been near the UUID (10 minute exposure time)
                        if (new Date().getTime() - prevTime > 10000) {
                            writeUuidsToFile(uuid);
                        }
                    } else {
                        try {
                            potentialProximityUuids.put(uuid.toString(), new Date().getTime());
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

        private void writeUuidsToFile(UUID uuid) throws JSONException {
            //Read in proximity UUIDs file
            try {
                closeProximityUUIDs = new JSONObject(fileReadWrite.loadFromFile(getApplicationContext(), "proximityUuids.json"));
            } catch (JSONException e) {
                e.printStackTrace();
            }

            //If the UUID is already in ArrayList, don't write value
            assert uuid != null;
            if (!closeProximityUUIDs.toString().contains(uuid.toString())) {
                closeProximityUUIDs.put(uuid.toString(), new Date().getTime());

                //Write proximity uuids back to file
                fileReadWrite.writeToFile(closeProximityUUIDs.toString(), "proximityUuids.json", getApplicationContext());
                Log.i(TAG, "Adding UUID to file : " + uuid);

                //If user status equals red post any new proximity uuid
                if (userStatus.equals("RED"))
                    postData("http://192.168.0.90:3000/receiveProximityUuids", closeProximityUUIDs.keys().toString());

            }
        }

        JSONObject potentialProximityUuids = new JSONObject();

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e("BLE", "Discovery onScanFailed: " + errorCode);
            super.onScanFailed(errorCode);
        }
    };

    public void postData(final String urlData, final String data)  {
        final boolean[] connectionSuccess = new boolean[1];

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(urlData);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                    connection.setRequestProperty("Accept","application/json");
                    connection.setDoOutput(true);
                    connection.setDoInput(true);

                    DataOutputStream os = new DataOutputStream(connection.getOutputStream());

                    os.writeBytes(data);

                    os.flush();
                    os.close();

                    if(connection.getResponseCode() == 200) {
                        connectionSuccess[0] = true;
                    }
                    else {
                        connectionSuccess[0] = false;
                        throw new Exception();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        thread.start();

        if (!connectionSuccess[0]){
            Toast.makeText(this, "Connection error occurred! Please make sure you have an active internet connection, then try again.", Toast.LENGTH_LONG).show();
        }
    }
}

