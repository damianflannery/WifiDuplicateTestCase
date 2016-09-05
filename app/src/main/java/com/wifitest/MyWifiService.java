package com.wifitest;

import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class MyWifiService extends Service {

    private static final String TAG = MyWifiService.class.getSimpleName();

    List<ScanResult> sWifiScanResults;

    WifiManager mWifiManager;

    StringBuilder mReportString = new StringBuilder(1035);

    StringBuilder mSb = new StringBuilder(1000);

    /* Directory where we will store all data */
    File mDataDirectory;

    /* File handle for file */
    File mFile;

    /* Outputstream for file */
    OutputStream mOutputStream1;

    /* Notification object for displaying feedback to the user on which service is running */
    Notification mNotification;

    /* Build object to construct notification */
    NotificationCompat.Builder mNotificationBuilder;

    /* Handle on BigText style for notification */
    NotificationCompat.BigTextStyle mBigStyle;

    private WifiManager.WifiLock mWifiLock;

    private String mLastScanResult = "";

    private int mDuplicateCount = 0;

    public enum Action {
        START_LOGGING, STOP_LOGGING
    }

    private BroadcastReceiver mScanResultsReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {

                sWifiScanResults = mWifiManager.getScanResults();

                mReportString.append(System.currentTimeMillis())
                    .append(",");

                int wifiCount = 0;

                for (ScanResult scanResult : sWifiScanResults) {

                    wifiCount++;
                    mSb.append(scanResult.BSSID)
                        .append(",")
                        .append(scanResult.level)
                        .append(",");
                }

                mReportString.append(wifiCount)
                    .append(",")
                    .append(mSb)
                    .append("\n");

                Log.d(TAG, mReportString.toString());

                writeFile1(mReportString.toString().getBytes());

                if (mSb.toString().equals(mLastScanResult)) {
                    writeFile1(("#duplicate " + mDuplicateCount++ + "\n").getBytes());
                } else {
                    mDuplicateCount = 0;
                }
                mLastScanResult = mSb.toString();

                mReportString.setLength(0);
                mSb.setLength(0);

                mWifiManager.startScan();
            }
        }
    };

    public MyWifiService() {
    }

    public static File createDataDirectory(Context context) {

        File dataDirectory = new File(context.getExternalFilesDir(null).getAbsolutePath()
            + "/log");

        if (!dataDirectory.exists()) {
            if (!dataDirectory.mkdirs()) {
                Log.e(TAG, "Cannot make dir.");
            }
        }
        return dataDirectory;
    }

    synchronized void writeFile1(byte[] bytes) {
        writeFile1(bytes, false);
    }

    synchronized void writeFile1(byte[] bytes, boolean flush) {

        try {

            if (mOutputStream1 != null) {
                mOutputStream1.write(bytes);

                if (flush) {
                    mOutputStream1.flush();
                }
            }

        } catch (IOException ev) {
            Log.e(TAG, "An error occurred while writing data to file", ev);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {

        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent.getAction().equals(Action.START_LOGGING.toString())) {

            showNotification();

            if (setupFiles()) {
                startSensing();
            } else {
                Log.e(TAG, "There was an error setting up files, so aborting");
            }

        } else if (intent.getAction().equals(Action.STOP_LOGGING.toString())) {
            Log.i(TAG, "Received Stop Foreground Intent");
            stopForeground(true);
            stopSelf();
        }

        return START_REDELIVER_INTENT;
    }

    boolean setupFiles() {

        //get location of private app only storage directory and create
        //a sub dir called /log if it doesn't already exist
        mDataDirectory = createDataDirectory(this);

        try {
            mFile = new File(mDataDirectory, "wifi.log");
            mOutputStream1 = new BufferedOutputStream(new FileOutputStream(mFile));
            Log.d(TAG, mFile.getAbsolutePath());

            return true;

        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
        }

        return false;

    }

    // start the sensing
    void startSensing() {

        Log.d(TAG, "registering wifi broadcast receiver and obtaining wifi lock");

        mWifiLock = mWifiManager.createWifiLock(WifiManager.WIFI_MODE_SCAN_ONLY, TAG);
        mWifiLock.acquire();

        registerReceiver(mScanResultsReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

        mWifiManager.startScan();

        Log.d(TAG, "Wifi sensing started");
    }

    void stopSensing() {

        //mHandler.removeCallbacks(mScanRunnable);

        // Unregister broadcast listeners
        unregisterScanResultsReceiver();

        closeAllStreams();

        Log.d(TAG, "Scanning stopped");
    }

    private void unregisterScanResultsReceiver() {
        if (mScanResultsReceiver != null) {
            try {
                unregisterReceiver(mScanResultsReceiver);
            } catch (IllegalArgumentException ex) {
                Log.w(TAG, "Recovered from trying to unregister mScanResultsReceiver receiver that was not already registered");
            }
        } else {
            Log.e(TAG, "mScanResultsReceiver is null. Why?");
        }

        if (mWifiLock != null) {
            if (mWifiLock.isHeld()) {
                mWifiLock.release();
            } else {
                Log.e(TAG, "Wifi lock is not held. Why?");
            }
        } else {
            Log.e(TAG, "Wifi lock is null. Why?");
        }
    }

    void showNotification() {

        mBigStyle =
            new NotificationCompat.BigTextStyle()
                .setBigContentTitle(getString(R.string.app_name))
                .setSummaryText("Scanning for Wifi");

        Bitmap icon = BitmapFactory.decodeResource(getResources(),
            R.mipmap.ic_launcher);

        mNotificationBuilder = new NotificationCompat.Builder(this)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Scanning for Wifi")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(
                Bitmap.createScaledBitmap(icon, 128, 128, false))
            .setOngoing(true)
            .setStyle(mBigStyle);

        mNotification = mNotificationBuilder.build();

        startForeground(10001, mNotification);
    }

    void closeAllStreams() {

        if (mFile != null) {
            if (mOutputStream1 != null) {
                try {
                    mOutputStream1.flush();
                    mOutputStream1.close();
                    mOutputStream1 = null;
                } catch (IOException ev) {
                    Log.e(TAG, "IOException closing BufferedOutputStream", ev);
                }
            }
        } else {
            Log.e(TAG, "in closeStream1, file already null");
        }
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        stopSensing();
        super.onDestroy();
    }

}
