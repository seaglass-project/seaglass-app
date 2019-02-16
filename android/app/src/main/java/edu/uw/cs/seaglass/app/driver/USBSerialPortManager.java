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

package edu.uw.cs.seaglass.app.driver;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import java.util.HashMap;
import java.util.Map;

public class USBSerialPortManager {
    private UsbManager usbManager;

    public USBSerialPortManager(Context context) {
        usbManager = (UsbManager)context.getSystemService(Context.USB_SERVICE);
    }

    /*
    public HashMap<String, UsbDevice> getDevices() {
        HashMap<String, UsbDevice> deviceList =  usbManager.getDeviceList();
        HashMap<String, UsbDevice> filteredList = new HashMap<String, UsbDevice>();
        for (Map.Entry<String, UsbDevice> deviceEntry : deviceList.entrySet()) {
            UsbDevice usbDevice = deviceEntry.getValue();
            // TODO: Support multiple device types and don't hardcode values here
            if (usbDevice.getVendorId() == USBDeviceIDs.VID_CYGNAL &&
                    usbDevice.getProductId() == USBDeviceIDs.PID_CP210x) {
                filteredList.put(deviceEntry.getKey(), usbDevice);
            }
        }
        return filteredList;
    }

    public USBSerialPort createPort(UsbDevice usbDevice) {
        // TODO: Support multiple device types and don't hardcode values here
        if (usbDevice.getVendorId() == USBDeviceIDs.VID_CYGNAL &&
                usbDevice.getProductId() == USBDeviceIDs.PID_CP210x) {
            return new CP210xSerialPort(usbManager, usbDevice);
        }
        throw new IllegalArgumentException("Unsupported USB device");
    }
    */

    public HashMap<String, USBSerialPort> getPorts() {
        HashMap<String, UsbDevice> deviceList =  usbManager.getDeviceList();
        HashMap<String, USBSerialPort> portList = new HashMap<>();
        for (Map.Entry<String, UsbDevice> deviceEntry : deviceList.entrySet()) {
            UsbDevice usbDevice = deviceEntry.getValue();
            // TODO: Support multiple device types and don't hardcode values here
            if (usbDevice.getVendorId() == USBDeviceIDs.VID_CYGNAL &&
                    usbDevice.getProductId() == USBDeviceIDs.PID_CP210x) {
                portList.put(deviceEntry.getKey(), new CP210xSerialPort(usbManager, usbDevice));
            }
        }
        return portList;
    }
}
