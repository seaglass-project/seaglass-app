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

import android.app.PendingIntent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;

import java.io.IOException;

public abstract class USBSerialPort {
    protected UsbManager usbManager;
    protected UsbDevice usbDevice;
    protected UsbDeviceConnection usbConnection;

    protected USBSerialPort(UsbManager usbManager, UsbDevice usbDevice) {
        this.usbManager = usbManager;
        this.usbDevice = usbDevice;
    }

    public boolean hasPermission() {
        return usbManager.hasPermission(usbDevice);
    }

    public void requestPermission(PendingIntent pendingIntent) {
        usbManager.requestPermission(usbDevice, pendingIntent);
    }

    public void open() throws IOException {
        usbConnection = usbManager.openDevice(usbDevice);
    }

    public void close() {
        if (usbConnection != null) {
            usbConnection.close();
            usbConnection = null;
        }
    }

    public String toString() {
        return usbDevice.getDeviceName();
    }

    abstract public void setBaudRate(int baudRate) throws IOException;
    abstract public byte[] read(int length) throws IOException;
    abstract public int write(byte[] data) throws IOException;
    abstract public void writeAsync(byte[] data) throws IOException;
    abstract public void requestWait();
}
