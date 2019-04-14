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

package edu.uw.cs.seaglass.app.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;

import androidx.annotation.NonNull;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.snackbar.SnackbarContentLayout;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback;
import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.content.ServiceConnection;
import android.content.ComponentName;
import android.os.IBinder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import edu.uw.cs.seaglass.app.Options;
import edu.uw.cs.seaglass.app.Utils;
import edu.uw.cs.seaglass.app.db.DatabaseService;
import edu.uw.cs.seaglass.app.logging.LoggingService;
import edu.uw.cs.seaglass.app.osmocom.OsmoconService;
import edu.uw.cs.seaglass.app.osmocom.PhoneState;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class MainActivity extends AppCompatActivity implements OnRequestPermissionsResultCallback {
    private BottomNavigationView navigation;

    private static final String TAG = Utils.TAG_PREFIX + "MainActivity";
    private static final int REQUEST_FINE_LOCATION_PERMISSION_CODE = 1;
    private static final int EXPORT_DB_CODE = 2;
    static final String ACTION_USB_PERMISSION_RESULT = "edu.uw.cs.seaglass.USB_PERMISSION_RESULT";

    private boolean loggingServiceRunning = false;

    private LoggingService mLoggingService;
    private DatabaseService mDatabaseService;

    private Options options;

    private ConfigFragment configFragment;
    private StatusFragment statusFragment;
    private CellInfoFragment cellInfoFragment;
    private ProgressBar uploadProgressBar;

    // Sets up the binding to the Logging service
    private ServiceConnection mLoggingConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            LoggingService.LocalBinder binder = (LoggingService.LocalBinder) service;
            mLoggingService = binder.getService();

            Log.d(TAG, "Logging Service Bound");
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            Log.d(TAG, "Logging Service Unbound");
            mLoggingService = null;
        }
    };

    private ServiceConnection mDatabaseConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            DatabaseService.LocalBinder binder = (DatabaseService.LocalBinder) service;
            mDatabaseService = binder.getService();
            Log.d(TAG, "Database Service Bound");
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            mDatabaseService = null;
            Log.d(TAG, "Database Service Unbound");
        }
    };

    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            Fragment newFragment = null;

            switch (item.getItemId()) {
                case R.id.navigation_configuration:
                    newFragment = configFragment;
                    break;
                case R.id.navigation_status:
                    newFragment = statusFragment;
                    break;
                //case R.id.navigation_mapping:
                case R.id.navigation_cell_info:
                    newFragment = cellInfoFragment;
                    break;
            }

            if (newFragment != null) {
                getSupportFragmentManager().beginTransaction().replace(R.id.content,
                        newFragment).commit();
                return true;
            }

            return false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        options = new Options(this);

        configFragment = ConfigFragment.newInstance();
        statusFragment = StatusFragment.newInstance();
        cellInfoFragment = CellInfoFragment.newInstance();

        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
        localBroadcastManager.registerReceiver(startLoggingIntentReceiver,
                new IntentFilter(Options.START_SCANNING));
        localBroadcastManager.registerReceiver(stopLoggingIntentReceiver,
                new IntentFilter(Options.STOP_SCANNING));
        localBroadcastManager.registerReceiver(configFragment.usbPermissionResultIntentReceiver,
                new IntentFilter(ACTION_USB_PERMISSION_RESULT));
        localBroadcastManager.registerReceiver(phoneStatusUpdateIntentReceiver,
                new IntentFilter(OsmoconService.OSMOCON_STATUS_UPDATE));

        navigation = findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);

        if (!options.getOsmocomVersion().equals(BuildConfig.OsmocomVersion)) {
            Log.d(TAG, "Osmocom version doesn't match; extracting assets");
            extractAssets();
            options.setOsmocomVersion(BuildConfig.OsmocomVersion);
        } else {
            Log.d(TAG, "Osmocom version matched");
        }

        // Show the configuration fragment by default
        Menu menu = navigation.getMenu();
        // TODO terrible hard coding of the index
        MenuItem menuItem = menu.getItem(0);
        menuItem.setChecked(true);

        getSupportFragmentManager().beginTransaction().replace(R.id.content, configFragment).commit();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_toolbar_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_export_db:
                Uri databaseExportUri;
                try {
                    databaseExportUri = mDatabaseService.getExportUri();
                } catch (IOException ex) {
                    //
                    Log.e(TAG, "IOException thrown while trying to get database export URI", ex);
                    showSnackbar(Snackbar.make(findViewById(R.id.container),
                            R.string.export_db_exception, Snackbar.LENGTH_LONG));
                    return true;
                }
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("application/octet-stream");
                shareIntent.putExtra(Intent.EXTRA_STREAM, databaseExportUri);
                startActivityForResult(Intent.createChooser(shareIntent,
                        "Export database to"), EXPORT_DB_CODE);
                return true;
            case R.id.action_about:
                Intent aboutIntent = new Intent(this, AboutActivity.class);
                aboutIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(aboutIntent);
                return true;
            case R.id.action_exit:
                //options.setSeenOnboarding(false);
                moveTaskToBack(true);
                Process.killProcess(Process.myPid());
                System.exit(1);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == EXPORT_DB_CODE) {
            mDatabaseService.deleteExportCache();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String action = intent.getAction();
        if (action != null && action.equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
            configFragment.updateAvailableUSBPorts();
        }
        Log.d(TAG, intent.toString());
    }

    @Override
    protected void onStart() {
        super.onStart();

        bindService(new Intent(this, DatabaseService.class),
                mDatabaseConnection, Context.BIND_AUTO_CREATE);
        bindService(new Intent(MainActivity.this, LoggingService.class),
                mLoggingConnection, Context.BIND_AUTO_CREATE);

        /*
        if (!options.getSeenOnboarding()) {
            Intent introIntent = new Intent(this, IntroActivity.class);
            introIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(introIntent);
            options.setSeenOnboarding(true);
        }
        */

        // FIXME: Is this right?
        Menu menu = navigation.getMenu();
        MenuItem menuItem = menu.getItem(0);
        menuItem.setChecked(true);
    }

    @Override
    protected void onStop() {
        unbindService(mDatabaseConnection);
        unbindService(mLoggingConnection);
        super.onStop();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_FINE_LOCATION_PERMISSION_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                onLocationPermissionAcquired();
            }
        }
    }

    private void onLocationPermissionAcquired() {
        configFragment.locationPermissionAcquired();
    }

    private void extractAssets() {
        try {
            // TODO: Make more programmatic?
            //extractAsset("arm-calypso/compal_e86/loader.highram.bin", "loader.highram.bin", false);
            extractAsset("arm-calypso/compal_e86/layer1.highram.bin", "layer1.highram.bin", false);
            //extractAsset("arm-linux-androideabi/osmocon", "osmocon", true);
            extractAsset("arm-linux-androideabi/cell_log", "cell_log", true);
        } catch (IOException e) {
            Log.e(TAG, "IOException thrown while extracting assets to file system", e);
        }
    }

    private void extractAsset(String assetFileName, String targetFileName, boolean isExecutable) throws IOException {
        InputStream inStream = getAssets().open(assetFileName);
        byte[] buf = new byte[inStream.available()];
        inStream.read(buf);
        inStream.close();

        OutputStream outStream = openFileOutput(targetFileName, MODE_PRIVATE);
        outStream.write(buf);
        outStream.close();

        File outFile = getFileStreamPath(targetFileName);
        outFile.setExecutable(isExecutable);
    }

    private void showSnackbar(Snackbar snackbar) {
        View snackbarView = snackbar.getView();
        CoordinatorLayout.LayoutParams layoutParams =
                (CoordinatorLayout.LayoutParams)snackbarView.getLayoutParams();
        layoutParams.setAnchorId(R.id.navigation);
        layoutParams.anchorGravity = Gravity.TOP;
        layoutParams.gravity = Gravity.TOP;
        snackbarView.setLayoutParams(layoutParams);
        snackbar.show();
    }

    private BroadcastReceiver phoneStatusUpdateIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            PhoneState phoneState =
                    (PhoneState)intent.getSerializableExtra(OsmoconService.OSMOCON_STATUS_STATE);

            Snackbar snackbar = null;

            switch (phoneState) {
                case PROMPT1:
                    snackbar = Snackbar.make(findViewById(R.id.container),
                            R.string.power_on_osmocom, Snackbar.LENGTH_INDEFINITE);
                    break;
                case PROMPT2:
                    snackbar = Snackbar.make(findViewById(R.id.container),
                            R.string.bootloader_found, Snackbar.LENGTH_INDEFINITE);
                    break;
                case CHAINLOADER:
                    snackbar = Snackbar.make(findViewById(R.id.container),
                            R.string.uploading_chainloader, Snackbar.LENGTH_INDEFINITE);
                    break;
                case DOWNLOAD_APP_BLOCKS:
                    int percent = Math.round(100 *
                            intent.getFloatExtra(OsmoconService.OSMOCON_STATUS_UPLOAD_PERCENT, 0));
                    if (uploadProgressBar == null) {
                        snackbar = Snackbar.make(findViewById(R.id.container),
                                R.string.uploading_firmware, Snackbar.LENGTH_INDEFINITE);
                        uploadProgressBar = new ProgressBar(MainActivity.this, null,
                                android.R.attr.progressBarStyleHorizontal);

                        uploadProgressBar.setProgress(percent);
                        SnackbarContentLayout.LayoutParams progressBarLayoutParams =
                                new SnackbarContentLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT);
                        progressBarLayoutParams.gravity = Gravity.FILL_HORIZONTAL;
                        progressBarLayoutParams.weight = 32;
                        uploadProgressBar.setLayoutParams(progressBarLayoutParams);
                        uploadProgressBar.getProgressDrawable().setColorFilter(
                                0xffb7a57a, PorterDuff.Mode.SRC_IN);

                        snackbar.addCallback(progressSnackBarCallback);

                        ((ViewGroup) snackbar.getView()
                                .findViewById(com.google.android.material.R.id.snackbar_text)
                                .getParent())
                                .addView(uploadProgressBar);
                    } else {
                        uploadProgressBar.setProgress(percent);
                    }
                    break;
                case APP_RUNNING:
                    snackbar = Snackbar.make(findViewById(R.id.container),
                            R.string.osmocom_running, Snackbar.LENGTH_LONG);
                    break;
                case ERROR:
                    snackbar = Snackbar.make(findViewById(R.id.container),
                            R.string.osmocom_error, Snackbar.LENGTH_LONG);
                    break;
            }

            if (snackbar != null) {
                showSnackbar(snackbar);
            }

        }
    };

    private BaseTransientBottomBar.BaseCallback<Snackbar> progressSnackBarCallback =
            new BaseTransientBottomBar.BaseCallback<Snackbar>() {
        @Override
        public void onDismissed(Snackbar snackbar, int event) {
            uploadProgressBar = null;
        }
    };

    private BroadcastReceiver startLoggingIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            startLogging();
        }
    };

    private BroadcastReceiver stopLoggingIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            stopLogging();
        }
    };

    private void startLogging() {
        if (!this.loggingServiceRunning) {
            Intent intent = new Intent(MainActivity.this, LoggingService.class);
            startService(intent);
            this.loggingServiceRunning = true;
        }
    }

    private void stopLogging() {
        if (this.loggingServiceRunning){
            mLoggingService.stopScan();
            this.loggingServiceRunning = false;
        }
    }
}
