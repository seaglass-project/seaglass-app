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

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;

import java.io.IOException;
import java.nio.ByteBuffer;

public class CP210xSerialPort extends USBSerialPort {
    private static final int REQTYPE_HOST_TO_INTERFACE = 0x41;
    private static final int USB_WRITE_TIMEOUT_MILLIS = 500;

    private static final int SILABSER_IFC_ENABLE_REQUEST_CODE = 0x00;
    private static final int SILABSER_SET_BAUDDIV_REQUEST_CODE = 0x01;
    private static final int SILABSER_SET_LINE_CTL_REQUEST_CODE = 0x03;
    private static final int SILABSER_SET_MHS_REQUEST_CODE = 0x07;
    private static final int SILABSER_SET_BAUDRATE = 0x1E;
    private static final int SILABSER_FLUSH_REQUEST_CODE = 0x12;

    private static final int UART_ENABLE = 0x0001;
    private static final int UART_DISABLE = 0x0000;

    private static final int CFG_8N1 = 0x0800;

    private static final int DEFAULT_BAUD_RATE = 115200;
    private static final int BAUD_RATE_GEN_FREQ = 0x384000;

    private UsbEndpoint readEndpoint;
    private UsbEndpoint writeEndpoint;

    CP210xSerialPort(UsbManager usbManager, UsbDevice usbDevice) {
        super(usbManager, usbDevice);
    }

    private void setConfigValue(int request, int value) {
        usbConnection.controlTransfer(REQTYPE_HOST_TO_INTERFACE, request, value,
                0, null, 0, USB_WRITE_TIMEOUT_MILLIS);
    }

    @Override
    public void open() throws IOException {
        super.open();

        UsbInterface usbInterface = usbDevice.getInterface(0);
        if (!usbConnection.claimInterface(usbInterface, true)) {
            throw new IOException("Cannot claim interface");
        }

        for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
            UsbEndpoint ep = usbInterface.getEndpoint(i);
            if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.getDirection() == UsbConstants.USB_DIR_IN) {
                    readEndpoint = ep;
                } else {
                    writeEndpoint = ep;
                }
            }
        }

        setConfigValue(SILABSER_IFC_ENABLE_REQUEST_CODE, UART_ENABLE);
        setConfigValue(SILABSER_SET_MHS_REQUEST_CODE, 0x0303);
        setConfigValue(SILABSER_SET_BAUDDIV_REQUEST_CODE, BAUD_RATE_GEN_FREQ / DEFAULT_BAUD_RATE);
        //setConfigValue(SILABSER_SET_LINE_CTL_REQUEST_CODE, CFG_8N1);
    }

    @Override
    public void setBaudRate(int baudRate) throws IOException {
        byte[] data = new byte[] {
                (byte) ( baudRate & 0xff),
                (byte) ((baudRate >> 8 ) & 0xff),
                (byte) ((baudRate >> 16) & 0xff),
                (byte) ((baudRate >> 24) & 0xff)
        };
        int ret = usbConnection.controlTransfer(REQTYPE_HOST_TO_INTERFACE, SILABSER_SET_BAUDRATE,
                0, 0, data, 4, USB_WRITE_TIMEOUT_MILLIS);
        if (ret < 0) {
            throw new IOException("Error setting baud rate.");
        }
    }

    @Override
    public byte[] read(int length) throws IOException {
        // bulkTransfers can't be aborted, so use the async API
        UsbRequest usbRequest = new UsbRequest();
        ByteBuffer buf = ByteBuffer.allocate(length);
        usbRequest.initialize(usbConnection, readEndpoint);
        usbRequest.queue(buf, length);
        if (usbConnection.requestWait() != usbRequest) {
            throw new IOException("requestWait() didn't return expected request");
        }
        usbRequest.close();
        byte[] data = new byte[buf.position()];
        buf.rewind();
        buf.get(data);
        return data;
    }

    @Override
    public int write(byte[] data) throws IOException {
       return usbConnection.bulkTransfer(writeEndpoint, data, data.length, 0);
    }

    @Override
    public void writeAsync(byte[] data) throws IOException {
        UsbRequest usbRequest = new UsbRequest();
        usbRequest.initialize(usbConnection, writeEndpoint);
        ByteBuffer byteBuf = ByteBuffer.allocate(data.length);
        byteBuf.put(data);
        usbRequest.queue(byteBuf, data.length);
    }

    @Override
    public void requestWait() {
        usbConnection.requestWait();
    }

    @Override
    public void close() {
        readEndpoint = null;
        writeEndpoint = null;
        if (usbConnection != null) {
            setConfigValue(SILABSER_IFC_ENABLE_REQUEST_CODE, UART_DISABLE);
        }
        // Release interface?
        super.close();
    }
}
