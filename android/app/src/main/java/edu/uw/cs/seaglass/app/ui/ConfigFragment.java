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


import android.Manifest;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.TextView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View.OnFocusChangeListener;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import edu.uw.cs.seaglass.app.Options;
import edu.uw.cs.seaglass.app.Utils;
import edu.uw.cs.seaglass.app.db.Band;
import edu.uw.cs.seaglass.app.driver.USBSerialPort;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;

import java.util.HashMap;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ConfigFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ConfigFragment extends Fragment {
    private static final String TAG = Utils.TAG_PREFIX + "ConfigFragment";
    private static final int REQUEST_FINE_LOCATION_PERMISSION_CODE = 1;

    private Switch scanEnabledSwitch;
    private Switch locationEnabledSwitch;
    private Spinner usbDeviceSpinner;
    private Spinner phoneTypeSpinner;
    private Switch scan850Switch;
    private Switch scan900Switch;
    private Switch scan1800Switch;
    private Switch scan1900Switch;
    private Switch transmitEnabledSwitch;
    private Spinner minimumRSSISpinner;
    private Switch syncEnabledSwitch;
    private EditText hostnameField;
    private Switch meteredSyncAllowed;
    private TextView uuid;

    private Options options;
    private HashMap<String, USBSerialPort> availablePorts;

    public ConfigFragment() {
    }

    public static ConfigFragment newInstance() {
        ConfigFragment fragment = new ConfigFragment();
        //Bundle args = new Bundle();
        //fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        options = new Options(getActivity());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_config, container, false);

        this.scanEnabledSwitch = (Switch)view.findViewById(R.id.enable_logging);
        this.locationEnabledSwitch = (Switch)view.findViewById(R.id.enable_location);
        this.usbDeviceSpinner = (Spinner)view.findViewById(R.id.usb_spinner);
        this.phoneTypeSpinner = (Spinner)view.findViewById(R.id.phone_type_spinner);
        this.scan850Switch = (Switch)view.findViewById(R.id.scan_850);
        this.scan900Switch = (Switch)view.findViewById(R.id.scan_900);
        this.scan1800Switch = (Switch)view.findViewById(R.id.scan_1800);
        this.scan1900Switch = (Switch)view.findViewById(R.id.scan_1900);
        this.transmitEnabledSwitch = (Switch)view.findViewById(R.id.enable_transmit);
        this.minimumRSSISpinner = (Spinner)view.findViewById(R.id.rssi_spinner);
        this.syncEnabledSwitch = (Switch)view.findViewById(R.id.enable_sync);
        this.hostnameField = (EditText)view.findViewById(R.id.server_hostname);
        this.meteredSyncAllowed = (Switch)view.findViewById(R.id.metered_upload_allowed);
        this.uuid = (TextView)view.findViewById(R.id.uuid);

        updateAvailableUSBPorts();

        this.scanEnabledSwitch.setOnCheckedChangeListener(onScanChanged);
        this.locationEnabledSwitch.setOnCheckedChangeListener(onLocationChanged);
        this.transmitEnabledSwitch.setOnCheckedChangeListener(onTransmitChanged);
        this.scan850Switch.setOnCheckedChangeListener(onScan850Changed);
        this.scan900Switch.setOnCheckedChangeListener(onScan900Changed);
        this.scan1800Switch.setOnCheckedChangeListener(onScan1800Changed);
        this.scan1900Switch.setOnCheckedChangeListener(onScan1900Changed);
        this.syncEnabledSwitch.setOnCheckedChangeListener(onSyncEnabledChanged);
        this.meteredSyncAllowed.setOnCheckedChangeListener(onMeteredSyncAllowedChanged);
        this.uuid.setOnClickListener(onUuidClicked);

        return view;
    }

    private Switch.OnCheckedChangeListener onScanChanged = new Switch.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            Log.d(TAG, "onScanChanged: " + isChecked);
            if (isChecked) {
                if (usbDeviceSpinner.getSelectedItem() == null) {
                    scanEnabledSwitch.setChecked(false);
                    scanEnabledSwitch.setEnabled(false);
                } else {
                    options.setScanEnabled(true);
                }
            } else {
                options.setScanEnabled(false);
            }
        }
    };

    private Switch.OnCheckedChangeListener onLocationChanged = new Switch.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            Log.d(TAG, "onLocationChanged: " + isChecked);
            if (isChecked) {
                if (ActivityCompat.checkSelfPermission(getActivity(),
                        Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Location permission not granted yet; requesting permission");
                    ActivityCompat.requestPermissions(getActivity(),
                            new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                            REQUEST_FINE_LOCATION_PERMISSION_CODE);
                    buttonView.setChecked(false);
                } else {
                    Log.d(TAG, "Location permission granted; enabling location");
                    options.setLocationEnabled(true);
                }
            } else {
                options.setLocationEnabled(false);
            }
        }
    };

    private Switch.OnCheckedChangeListener onTransmitChanged = new Switch.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            options.setTransmitEnabled(isChecked);
        }
    };

    private Switch.OnCheckedChangeListener onScan850Changed = new Switch.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            options.setBandEnabled(Band.GSM850, isChecked);
        }
    };

    private Switch.OnCheckedChangeListener onScan900Changed = new Switch.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            options.setBandEnabled(Band.GSM900, isChecked);
        }
    };

    private Switch.OnCheckedChangeListener onScan1800Changed = new Switch.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            options.setBandEnabled(Band.DCS1800, isChecked);
        }
    };

    private Switch.OnCheckedChangeListener onScan1900Changed = new Switch.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            options.setBandEnabled(Band.PCS1900, isChecked);
        }
    };

    private Switch.OnCheckedChangeListener onSyncEnabledChanged = new Switch.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            options.setSyncEnabled(isChecked);
        }
    };

    private Switch.OnCheckedChangeListener onMeteredSyncAllowedChanged = new Switch.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            options.setMeteredSyncAllowed(isChecked);
        }
    };

    private void requestUSBSerialPortPermission(USBSerialPort usbSerialPort) {
        Log.d(TAG, "Requesting USB port permission");
        PendingIntent permissionIntent = PendingIntent.getBroadcast(getActivity(),
                0, new Intent(MainActivity.ACTION_USB_PERMISSION_RESULT), 0);
        usbSerialPort.requestPermission(permissionIntent);
    }

    private Spinner.OnItemSelectedListener onUsbPortSelectedListener = new Spinner.OnItemSelectedListener() {
        public void onItemSelected(AdapterView<?> parent, View view,
                                   int pos, long id) {
            Log.d(TAG, "usb port selected: pos=" + pos + ", str=" + parent.getItemAtPosition(pos));
            USBSerialPort usbSerialPort = availablePorts.get(parent.getItemAtPosition(pos));
            if (usbSerialPort.hasPermission()) {
                Log.d(TAG, "selected port access is granted; setting port");
                options.setUSBPort(availablePorts.get(parent.getItemAtPosition(pos)));
                scanEnabledSwitch.setEnabled(true);
            } else {
                requestUSBSerialPortPermission(usbSerialPort);
            }
        }

        public void onNothingSelected(AdapterView<?> parent) {
            options.setUSBPort(null);
            scanEnabledSwitch.setEnabled(false);
        }
    };

    private TextView.OnClickListener onUuidClicked = new TextView.OnClickListener() {
        @Override
        public void onClick(View view) {
            AlertDialog.Builder alertBuilder = new AlertDialog.Builder(getContext());
            alertBuilder.setMessage(getString(R.string.reset_uuid))
                    .setPositiveButton(getString(R.string.yes), onUuidDialogClick)
                    .setNegativeButton(getString(R.string.no), onUuidDialogClick)
                    .show();
        }
    };

    private DialogInterface.OnClickListener onUuidDialogClick = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialogInterface, int i) {
            if (i == DialogInterface.BUTTON_POSITIVE) {
                // In case there's a race condition, we want to reset the upload key before
                // changing the UUID.
                options.resetUploadKey();
                options.resetUUID();
                uuid.setText(options.getUUID());
            }
        }
    };

    BroadcastReceiver usbPermissionResultIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getBooleanExtra("EXTRA_PERMISSION_GRANTED", false)) {
                options.setUSBPort(availablePorts.get(usbDeviceSpinner.getSelectedItem()));
                scanEnabledSwitch.setEnabled(true);
            } else {
                scanEnabledSwitch.setEnabled(false);
            }
        }
    };

    public void locationPermissionAcquired() {
        Log.d(TAG, "location permission acquired");
        locationEnabledSwitch.setChecked(true);
    }

    BroadcastReceiver usbDevicesChangedIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateAvailableUSBPorts();
        }
    };

    void updateAvailableUSBPorts() {
        availablePorts = options.getAvailablePorts();

        if (availablePorts.size() == 0) {
            Log.d(TAG, "No available USB ports");
            scanEnabledSwitch.setChecked(false);
            scanEnabledSwitch.setEnabled(false);
        } else {
            String[] availablePortNames =
                    availablePorts.keySet().toArray(new String[availablePorts.size()]);
            usbDeviceSpinner.setAdapter(new ArrayAdapter<>(getActivity(),
                    android.R.layout.simple_spinner_dropdown_item,
                    availablePorts.keySet().toArray(new String[availablePorts.size()])));
            USBSerialPort port = availablePorts.get(availablePortNames[0]);
            Log.d(TAG, "Attempting to use the first available USB port: " + port);
            if (port.hasPermission()) {
                Log.d(TAG, "default port access is granted; setting port");
                options.setUSBPort(port);
            } else {
                requestUSBSerialPortPermission(port);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        locationEnabledSwitch.setChecked(options.getLocationEnabled());
        phoneTypeSpinner.setSelection(options.getPhoneType().getValue());
        scan850Switch.setChecked(options.getBandEnabled(Band.GSM850));
        scan900Switch.setChecked(options.getBandEnabled(Band.GSM900));
        scan1800Switch.setChecked(options.getBandEnabled(Band.DCS1800));
        scan1900Switch.setChecked(options.getBandEnabled(Band.PCS1900));
        transmitEnabledSwitch.setChecked(options.getTransmitEnabled());
        syncEnabledSwitch.setChecked(options.getSyncEnabled());
        meteredSyncAllowed.setChecked(options.getMeteredSyncAllowed());
        hostnameField.setText(options.getSyncServer());
        uuid.setText(options.getUUID());
    }
}
