/*
 * Copyright (C) 2018 - 2019 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.uw.cs.seaglass.app.logging;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.location.Location;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.Binder;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.WorkManager;
import androidx.work.PeriodicWorkRequest;
import androidx.work.Constraints;

import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import edu.uw.cs.seaglass.app.Options;
import edu.uw.cs.seaglass.app.Utils;
import edu.uw.cs.seaglass.app.db.DatabaseService;
import edu.uw.cs.seaglass.app.db.SyncUploadWorker;
import edu.uw.cs.seaglass.app.osmocom.OsmocomBinaries;
import edu.uw.cs.seaglass.app.osmocom.OsmoconService;
import edu.uw.cs.seaglass.app.db.CellObservation;
import edu.uw.cs.seaglass.app.db.GSMPacket;
import edu.uw.cs.seaglass.app.db.LocationMeasurement;
import edu.uw.cs.seaglass.app.db.SpectrumMeasurement;
import edu.uw.cs.seaglass.app.ui.MainActivity;
import edu.uw.cs.seaglass.app.ui.R;

public class LoggingService extends Service implements
        SpectrumMeasurementReceiver, GSMPacketReceiver, CellObservationReceiver {
    public static final String CELL_LOG_POWER_MEASUREMENT = "CELL_LOG_POWER_MEASUREMENT";
    public static final String CELL_LOG_CELL_OBSERVATION = "CELL_LOG_CELL_OBSERVATION";
    public static final String CELL_LOG_ARFCN = "edu.uw.cs.seaglass.app.logging.ARFCN";
    public static final String CELL_LOG_BAND = "edu.uw.cs.seaglass.app.logging.BAND";
    public static final String CELL_LOG_DBM = "edu.uw.cs.seaglass.app.logging.DBM";
    public static final String CELL_LOG_TIMESTAMP = "edu.uw.cs.seaglass.app.logging.TIMESTAMP";
    public static final String CELL_LOG_CELL_INFO = "edu.uw.cs.seaglass.app.logging.CELL_INFO";

    public static final String SYNC_PERIODIC_NAME = "unique-periodic-sync";

    private FusedLocationProviderClient mFusedLocationClient;
    private Notification mLoggingServiceActiveNotification;
    private LocalBroadcastManager localBroadcastManager;
    private Location mLastLocation;
    private LoggerLocationCallback loggerLocationCallback;
    private OsmocomBinaries ob;
    private OsmoconService os;
    private Options options;
    private DatabaseService mDatabaseService;

    private IPGSMTAPProvider gsmPktProviderThread;
    private CellLogProvider cellLogProviderThread;

    private final IBinder mBinder = new LocalBinder();

    private static final int ONGOING_NOTIFICATION_ID = 1;
    private static final String TAG = Utils.TAG_PREFIX + "LoggingService";
    private static final int SYNC_REPEAT_INTERVAL = 15;     //in minutes

    public LoggingService() {
        localBroadcastManager = LocalBroadcastManager.getInstance(this);
    }

    private ServiceConnection mDatabaseConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            DatabaseService.LocalBinder binder = (DatabaseService.LocalBinder) service;
            mDatabaseService = binder.getService();
            onDatabaseConnected();
            Log.d(TAG, "Database Service Bound");
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            Log.d(TAG, "Database Service Unbound");
            mDatabaseService = null;
        }
    };

    private class insertLocationAsyncTask extends
        AsyncTask<LocationMeasurement, Void, Void> {
        @Override
        protected Void doInBackground(final LocationMeasurement... lms) {
            mDatabaseService.insertLocationMeasurement(lms[0]);
            return null;
        }
    }

    private class LoggerLocationCallback extends LocationCallback {
        @Override
        public void onLocationAvailability(LocationAvailability locationAvailability) {

        }
        @Override
        public void onLocationResult(LocationResult locationResult) {
            synchronized (this) {
                mLastLocation = locationResult.getLastLocation();
                LocationMeasurement lm = new LocationMeasurement();
                lm.timestamp = mLastLocation.getTime();
                lm.latitude = mLastLocation.getLatitude();
                lm.longitude = mLastLocation.getLongitude();
                lm.altitude = mLastLocation.getAltitude();
                lm.bearing = mLastLocation.getBearing();
                lm.speed = mLastLocation.getSpeed();
                lm.horizontalAccuracy = mLastLocation.getAccuracy();
                new insertLocationAsyncTask().execute(lm);
            }
        }
    }

    public class LocalBinder extends Binder{
       public LoggingService getService() {
            // Return this instance of LocalService so clients can call public methods
            return LoggingService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        options = new Options(this);
    }

    private void onDatabaseConnected() {
        if (options.getLocationEnabled()) {
            mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
            LocationRequest locationRequest = new LocationRequest();
            locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            locationRequest.setInterval(5000);
            loggerLocationCallback = new LoggerLocationCallback();

            mFusedLocationClient.requestLocationUpdates(locationRequest,
                    loggerLocationCallback, null);
        }

        localBroadcastManager.registerReceiver(restartCellLogIntentReceiver,
                new IntentFilter(Options.CELL_LOG_RESTART));

        try {
            // FIXME: Don't hardcode phone type
            InputStream inStream = getAssets().open("arm-calypso/compal_e86/layer1.highram.bin");
            byte[] appPayload = new byte[inStream.available()];
            inStream.read(appPayload);
            inStream.close();
            os = new OsmoconService(localBroadcastManager,"osmocom_l2",
                    options.getUSBPort(), options.getPhoneType(), appPayload);
            //os.start();
        } catch (IOException ex) {
            Log.e(TAG, "Error starting OsmoconService", ex);
            stopScan();
            return;
        }

        ob = new OsmocomBinaries(getFilesDir(),
                new File(getApplicationInfo().dataDir, "lib"),
                options);
        ob.start();

        try {
            gsmPktProviderThread = new IPGSMTAPProvider(this);
            gsmPktProviderThread.start();
        } catch (Exception e) {
            Log.e(TAG, "Exception thrown while creating IPGSMTAPProvider", e);
            stopScan();
            return;
        }

        try {
            // FIXME: This will fail the first time because cell_log hasn't created the pipe yet
            cellLogProviderThread = new CellLogProvider(
                    new File(getFilesDir(), "cell_log_fifo").getAbsolutePath(),
                    this, this);
            cellLogProviderThread.start();
        } catch (Exception e) {
            Log.e(TAG, "Exception thrown while creating CellLogProvider", e);
            stopScan();
            return;
        }


    }

    private BroadcastReceiver restartCellLogIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ob.restartCellLog();
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        Intent databaseServiceIntent = new Intent(this, DatabaseService.class);
        bindService(databaseServiceIntent, mDatabaseConnection, Context.BIND_AUTO_CREATE);

        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, notificationIntent, 0);

        mLoggingServiceActiveNotification = new Notification.Builder(this)
                .setContentTitle(getText(R.string.logging_notification_title))
                .setContentText(getText(R.string.logging_notification_text))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .build();
        startForeground(ONGOING_NOTIFICATION_ID, mLoggingServiceActiveNotification);

        NetworkType networkType;
        if (options.getMeteredSyncAllowed()){
            networkType = NetworkType.CONNECTED;
        }
        else{
            networkType = NetworkType.UNMETERED;
        }

        Log.d(TAG, "Logging Service About to Start Periodic Workers");

        if (options.getSyncEnabled()) {
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(networkType)
                    .build();

            PeriodicWorkRequest syncRequest =
                    new PeriodicWorkRequest.Builder(SyncUploadWorker.class, SYNC_REPEAT_INTERVAL, TimeUnit.MINUTES)
                            .setConstraints(constraints)
                            .build();

            WorkManager.getInstance()
                    .enqueueUniquePeriodicWork(SYNC_PERIODIC_NAME,
                            ExistingPeriodicWorkPolicy.REPLACE, syncRequest);
        }

        return START_NOT_STICKY;
    }

    public void stopScan() {
        if (mDatabaseService!= null) {
            unbindService(mDatabaseConnection);
            mDatabaseService = null;
        }
        if (ob != null) {
            ob.close();
            ob = null;
        }
        if (os != null) {
            os.shutdown();
            os = null;
        }
        if (mFusedLocationClient != null){
            mFusedLocationClient.removeLocationUpdates(loggerLocationCallback);
            mFusedLocationClient = null;
        }
        if (gsmPktProviderThread != null){
            gsmPktProviderThread.interrupt();
            gsmPktProviderThread = null;
        }
        if (cellLogProviderThread != null){
            cellLogProviderThread.interrupt();
            cellLogProviderThread = null;
        }
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        stopScan();
    }

    public void onSpectrumMeasurementReceived(SpectrumMeasurement measurement) {
        Intent intent = new Intent(CELL_LOG_POWER_MEASUREMENT);
        intent.putExtra(CELL_LOG_ARFCN, measurement.measurementHeader.arfcn);
        intent.putExtra(CELL_LOG_BAND, measurement.measurementHeader.band);
        intent.putExtra(CELL_LOG_DBM, measurement.measurementHeader.dBm);
        intent.putExtra(CELL_LOG_TIMESTAMP, measurement.measurementHeader.timestamp);
        localBroadcastManager.sendBroadcast(intent);

        if (mDatabaseService != null) {
            mDatabaseService.insertSpectrumMeasurement(measurement);
        }
    }

    public void onGSMPacketReceived(GSMPacket pkt) {
        if (mDatabaseService != null) {
            mDatabaseService.insertGSMPacket(pkt);
        }
    }

    public void onCellObservationReceived(CellObservation observation) {
        if (observation.si3 != null) {
            CellInfo ci;
            ci = CellInfo.cellInfoBuilder(observation);

            Intent intent = new Intent(CELL_LOG_CELL_OBSERVATION);
            intent.putExtra(CELL_LOG_CELL_INFO, ci);
            localBroadcastManager.sendBroadcast(intent);
        }

        if (mDatabaseService != null) {
            mDatabaseService.insertCellObservation(observation);
        }
    }
}
