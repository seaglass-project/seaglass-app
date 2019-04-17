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

package edu.uw.cs.seaglass.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.UUID;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import edu.uw.cs.seaglass.app.db.Band;
import edu.uw.cs.seaglass.app.driver.USBSerialPort;
import edu.uw.cs.seaglass.app.driver.USBSerialPortManager;
import edu.uw.cs.seaglass.app.osmocom.PhoneType;
import edu.uw.cs.seaglass.app.ui.R;

public class Options {
    public static final String CELL_LOG_RESTART = "CELL_LOG_RESTART";
    public static final String START_SCANNING = "START_SCANNING";
    public static final String STOP_SCANNING = "STOP_SCANNING";
    public static final String LOCATION_ENABLED_CHANGED = "LOCATION_ENABLED_CHANGED";

    private static final String SCAN_ENABLED = "SCAN_ENABLED";
    private static final String LOCATION_ENABLED = "LOCATION_ENABLED";
    private static final String TRANSMIT_ENABLED = "TRANSMIT_ENABLED";
    private static final String PHONE_TYPE = "PHONE_TYPE";
    private static final String USB_PORT = "USB_PORT";
    private static final String APP_UUID = "UUID";
    private static final String APP_UPLOAD_KEY = "UPLOAD_KEY";

    private static final String SYNC_ENABLED = "SYNC_ENABLED";
    private static final String METERED_SYNC_ALLOWED = "METERED_SYNC_ALLOWED";
    private static final String SERVER_HOSTNAME = "SERVER_HOSTNAME";

    private static final String OSMOCOM_VERSION = "OSMOCOM_VERSION";
    private static final String SEEN_ONBOARDING = "SEEN_ONBOARDING";
    private static final String[] BAND_ENABLED_NAMES = {
            "BAND_GSM900_ENABLED",
            "BAND_GSM850_ENABLED",
            "BAND_DCS1800_ENABLED",
            "BAND_PCS1900_ENABLED"
    };

    private static final String PREFS_NAME = "SeaglassOptions";

    private static Options singleton = null;
    private SharedPreferences sharedPreferences = null;;
    private LocalBroadcastManager localBroadcastManager = null;
    private USBSerialPortManager usbSerialPortManager;
    private USBSerialPort usbSerialPort;
    private String defaultHostname;

    public Options(Context context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        localBroadcastManager = LocalBroadcastManager.getInstance(context);
        usbSerialPortManager = new USBSerialPortManager(context);
        defaultHostname = context.getResources().getString(R.string.default_server_hostname);
    }

    public PhoneType getPhoneType() {
        return PhoneType.valueOf(sharedPreferences.getInt(PHONE_TYPE, PhoneType.C140xor.getValue()));
    }

    public boolean getScanEnabled() {
        return sharedPreferences.getBoolean(SCAN_ENABLED, false);
    }

    public boolean getLocationEnabled() {
        return sharedPreferences.getBoolean(LOCATION_ENABLED, false);
    }

    public boolean getTransmitEnabled() {
        return sharedPreferences.getBoolean(TRANSMIT_ENABLED, false);
    }

    public boolean getBandEnabled(Band band) {
        // Enable US GSM bands by default
        boolean defaultValue = (band == Band.GSM850 || band == Band.PCS1900);
        return sharedPreferences.getBoolean(BAND_ENABLED_NAMES[band.getId()], defaultValue);
    }

    public void setBandEnabled(Band band, boolean enabled) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(BAND_ENABLED_NAMES[band.getId()], enabled);
        editor.apply();
        sendCellLogRestartRequest();
    }

    public void setLocationEnabled(boolean locationEnabled) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(LOCATION_ENABLED, locationEnabled);
        editor.apply();
        sendLocationEnabledChanged();
    }

    public void setPhoneType(PhoneType phoneType) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(PHONE_TYPE, phoneType.getValue());
        editor.apply();
        sendCellLogRestartRequest();
    }

    public void setScanEnabled(boolean scanEnabled) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(SCAN_ENABLED, scanEnabled);
        editor.apply();
        if (scanEnabled) {
            sendStartScanning();
        } else {
            sendStopScanning();
        }
    }

    public void setTransmitEnabled(boolean transmitEnabled) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(TRANSMIT_ENABLED, transmitEnabled);
        editor.apply();
    }

    public HashMap<String, USBSerialPort> getAvailablePorts() {
        return usbSerialPortManager.getPorts();
    }

    public USBSerialPort getUSBPort() {
        String usbSerialPortDeviceName = sharedPreferences.getString(USB_PORT, null);
        if (usbSerialPortDeviceName != null) {
            return getAvailablePorts().get(usbSerialPortDeviceName);
        }
        return null;
    }

    public void setUSBPort(USBSerialPort usbSerialPort) {
        sharedPreferences.edit().putString(USB_PORT, usbSerialPort.toString()).apply();
    }

    private void sendCellLogRestartRequest() {
        Intent intent = new Intent(CELL_LOG_RESTART);
        localBroadcastManager.sendBroadcast(intent);
    }

    private void sendStartScanning() {
        Intent intent = new Intent(START_SCANNING);
        localBroadcastManager.sendBroadcast(intent);
    }

    private void sendStopScanning() {
        Intent intent = new Intent(STOP_SCANNING);
        localBroadcastManager.sendBroadcast(intent);
    }

    private void sendLocationEnabledChanged() {
        Intent intent = new Intent(LOCATION_ENABLED_CHANGED);
        localBroadcastManager.sendBroadcast(intent);
    }

    public boolean getSeenOnboarding() {
        return sharedPreferences.getBoolean(SEEN_ONBOARDING, false);
    }

    public void setSeenOnboarding(boolean value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(SEEN_ONBOARDING, value);
        editor.apply();
    }

    public String getOsmocomVersion() {
        return sharedPreferences.getString(OSMOCOM_VERSION, "");
    }

    public void setOsmocomVersion(String version) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(OSMOCOM_VERSION, version);
        editor.apply();
    }

    public void setSyncEnabled(boolean syncEnabled){
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(SYNC_ENABLED, syncEnabled);
        editor.apply();
    }

    public boolean getSyncEnabled() {
        return sharedPreferences.getBoolean(SYNC_ENABLED, false);
    }

    public void setMeteredSyncAllowed(boolean meteredSyncAllowed){
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(METERED_SYNC_ALLOWED, meteredSyncAllowed);
        editor.apply();
    }

    public boolean getMeteredSyncAllowed(){
        return sharedPreferences.getBoolean(METERED_SYNC_ALLOWED, false);
    }

    public String getSyncServer() {
        return sharedPreferences.getString(SERVER_HOSTNAME, defaultHostname);
    }

    public void setSyncServer(String hostname) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(SERVER_HOSTNAME, hostname);
        editor.apply();
    }

    public void initializeUUID(){
        String uuid = sharedPreferences.getString(APP_UUID, "");

        if (uuid == ""){
            uuid = UUID.randomUUID().toString();

            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(APP_UUID, uuid);
            editor.apply();
        }
    }

    public void initializeUploadKey(){
        String uploadKey = sharedPreferences.getString(APP_UPLOAD_KEY, "");

        if (uploadKey == ""){
            uploadKey = UUID.randomUUID().toString();

            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(APP_UPLOAD_KEY, uploadKey);
            editor.apply();
        }
    }

    public String getUUID(){
        return sharedPreferences.getString(APP_UUID, "");
    }

    public String getUploadKey(){
        return sharedPreferences.getString(APP_UPLOAD_KEY, "");
    }
}
