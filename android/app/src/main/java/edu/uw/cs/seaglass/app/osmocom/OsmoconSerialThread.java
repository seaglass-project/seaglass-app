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

package edu.uw.cs.seaglass.app.osmocom;

import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.util.Arrays;

import edu.uw.cs.seaglass.app.driver.USBSerialPort;
import edu.uw.cs.seaglass.app.Utils;

class OsmoconSerialThread extends Thread {
    enum SerialState {
        NONE,
        BOOTLOADER_PKT,
        ROMLOADER_PKT,
        HDLC_PKT
    }

    private static final byte HDLC_FLAG = 0x7e;
    private static final byte BOOTLOADER_PKT_INITIAL_BYTE = 0x1b;
    private static final int BOOTLOADER_PKT_LENGTH = 7;
    private static final byte ROMLOADER_PKT_INITIAL_BYTE_TO_PHONE = '<';
    private static final byte ROMLOADER_PKT_INITAL_BYTE_FROM_PHONE = '>';

    private static final byte[] PROMPT1 = { 0x1b, (byte)0xf6, 0x02, 0x00, 0x41, 0x01, 0x40 };
    private static final byte[] PROMPT2 = { 0x1b, (byte)0xf6, 0x02, 0x00, 0x41, 0x02, 0x43 };
    private static final byte[] DNLOAD = { 0x1b, (byte)0xf6, 0x02, 0x00, 0x52, 0x01, 0x53 };
    private static final byte[] ACK = { 0x1b, (byte)0xf6, 0x02, 0x00, 0x41, 0x03, 0x42 };
    private static final byte[] NACK = { 0x1b, (byte)0xf6, 0x02, 0x00, 0x45, 0x53, 0x16 };
    private static final byte[] NACK_MAGIC = { 0x1b, (byte)0xf6, 0x02, 0x00, 0x41, 0x03, 0x57 };
    private static final byte[] FTMTOOL = { 'F', 'T', 'M', 'T', 'O', 'O', 'L' };
    private static final byte[] MAGIC = { '1', '0', '0', '3' };
    private static final byte[] HDR_C123 = { (byte)0xee, 0x4c, (byte)0x9f, 0x63 };
    private static final byte[] HDR_C155 = { 0x78, 0x47, (byte)0xc0, 0x46 };

    private static final short MAGIC_OFFSET = 0x3be2;

    private static final byte[] CHAINLOADER = {
            0x0a, 0x18, (byte)0xa0, (byte)0xe3, 0x01, 0x10, 0x51, (byte)0xe2,
            (byte)0xfd, (byte)0xff, (byte)0xff, 0x1a, 0x08, 0x10, (byte)0x9f, (byte)0xe5,
            0x01, 0x2c, (byte)0xa0, (byte)0xe3, (byte)0xb0, 0x20, (byte)0xc1, (byte)0xe1,
            0x00, (byte)0xf0, (byte)0xa0, (byte)0xe3, 0x10, (byte)0xfb, (byte)0xff, (byte)0xff
    };

    private static final byte[] ROMLOADER_IDENT = { '<', 'i' };
    private static final byte[] ROMLOADER_IDENT_ACK = { '>', 'i' };
    private static final byte[] ROMLOADER_PARAM =
            { '<', 'p', 0x00, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00 };
    private static final byte[] ROMLOADER_BLOCK_ACK = { '>', 'w' };
    private static final byte[] ROMLOADER_BRANCH = { '<', 'b', 0x00, (byte)0x82, 0x00, 0x00 };
    private static final byte[] ROMLOADER_BRANCH_ACK = { '>', 'b' };

    private static final String TAG = Utils.TAG_PREFIX + "OsmoSerialThread";
    private static final int SERIAL_BUF_SIZE = 4096;
    private static long SERIAL_STATE_TIMEOUT = 1000;

    private OsmoconService osmoconService;
    private USBSerialPort usbSerialPort;
    private PhoneType phoneType;
    private PhoneState phoneState;
    private int maxBlockSize;
    private int blockMemPtr;
    private int appPayloadPtr;
    private byte[] chainloaderPayload;
    private byte[] appPayload;
    private byte appChecksum;
    private byte[] serialBuf;
    private int serialBufReadPtr;
    private int serialBufWritePtr;
    private SerialState serialState;
    private long lastRecvTime;

    OsmoconSerialThread(OsmoconService osmoconService, USBSerialPort usbSerialPort,
                        PhoneType phoneType, byte[] appPayload) {
        this.osmoconService = osmoconService;
        this.usbSerialPort = usbSerialPort;
        this.phoneType = phoneType;
        this.appPayload = appPayload;
        this.serialBuf = new byte[SERIAL_BUF_SIZE];
    }

    void write(byte[] payload) throws IOException {
        usbSerialPort.write(payload);
    }

    private int getSerialBufSize() {
        if (serialBufWritePtr > serialBufReadPtr) {
            return serialBufWritePtr - serialBufReadPtr;
        } else {
            return serialBufWritePtr + serialBuf.length - serialBufReadPtr;
        }
    }

    private byte[] getPacket() {
        byte[] pktBuf;

        if (serialBufWritePtr > serialBufReadPtr) {
            pktBuf = Arrays.copyOfRange(serialBuf, serialBufReadPtr, serialBufWritePtr);
        } else {
            int pktLen = serialBufWritePtr + serialBuf.length - serialBufReadPtr;
            pktBuf = new byte[pktLen];
            System.arraycopy(serialBuf, serialBufReadPtr, pktBuf, 0,
                    serialBuf.length - serialBufReadPtr);
            System.arraycopy(serialBuf, 0, pktBuf,
                    serialBuf.length - serialBufReadPtr, serialBufWritePtr);
        }

        serialBufReadPtr = serialBufWritePtr;
        return pktBuf;
    }

    private void writeSerialBufByte(byte b) throws BufferOverflowException {
        serialBuf[serialBufWritePtr++] = b;
        if (serialBufWritePtr >= serialBuf.length)
            serialBufWritePtr = 0;
        if (serialBufWritePtr == serialBufReadPtr) {
            throw new BufferOverflowException();
        }
    }

    private void handleBootloaderPkt(byte[] pkt) throws IOException {
        PhoneState oldPhoneState = phoneState;
        if (Arrays.equals(pkt, PROMPT1)) {
            phoneState = PhoneState.PROMPT2;
            usbSerialPort.write(DNLOAD);
            //Log.d(TAG, "Received PROMPT1");
        } else if (phoneState == PhoneState.PROMPT2 && Arrays.equals(pkt, PROMPT2)) {
            //Log.d(TAG, "Received PROMPT2, beginning download");
            phoneState = PhoneState.CHAINLOADER;
            usbSerialPort.writeAsync(chainloaderPayload);
            sendStatusUpdate();
            usbSerialPort.requestWait();
        } else if (phoneState == PhoneState.CHAINLOADER && Arrays.equals(pkt, ACK)) {
            //Log.d(TAG, "Received ACK, switching to romloader");
            usbSerialPort.setBaudRate(19200);
            phoneState = PhoneState.IDENT;
            new BeaconThread().start();
        } else if (phoneState == PhoneState.CHAINLOADER && Arrays.equals(pkt, NACK)) {
            Log.d(TAG, "Received NACK from phone");
            phoneState = PhoneState.ERROR;
        } else if (phoneState == PhoneState.CHAINLOADER && Arrays.equals(pkt, NACK_MAGIC)) {
            Log.d(TAG, "Received NACK_MAGIC from phone");
            phoneState = PhoneState.ERROR;
        } else {
            Log.w(TAG, "Received unexpected bootloader packet from phone");
            phoneState = PhoneState.ERROR;
        }
        if (phoneState != oldPhoneState) {
            //sendStatusUpdate();
        }
    }

    private boolean haveCompleteRomloaderPkt() {
        byte type;
        if (serialBufReadPtr + 1 >= serialBuf.length) {
            type = serialBuf[serialBufReadPtr + 1 - serialBuf.length];
        } else {
            type = serialBuf[serialBufReadPtr + 1];
        }

        switch (type) {
            case 'c':
            case 'C':
                return getSerialBufSize() >= 3;
            case 'p':
                return getSerialBufSize() >= 4;
            default:
                return getSerialBufSize() >= 2;
        }
    }

    private void sendStatusUpdate() {
        osmoconService.serialThreadStatusUpdated(phoneState, appPayloadPtr, appPayload.length);
    }

    private void handleRomloaderPkt(byte[] pkt) throws IOException {
        if (phoneState == PhoneState.IDENT && Arrays.equals(pkt, ROMLOADER_IDENT_ACK)) {
            Log.d(TAG, "Received ROMLOADER_IDENT_ACK from phone");
            phoneState = PhoneState.PARAM;
            usbSerialPort.write(ROMLOADER_PARAM);
        } else if (phoneState == PhoneState.PARAM && pkt[0] == '>' && pkt[1] == 'p') {
            Log.d(TAG, "Received ROMLOADER_PARAM_ACK from phone; sending payload");
            usbSerialPort.setBaudRate(115200);
            phoneState = PhoneState.DOWNLOAD_APP_BLOCKS;
            maxBlockSize = (pkt[3] << 8) + pkt[2];
            blockMemPtr = 0x820000;
            appPayloadPtr = 0;
            appChecksum = 0;
            osmoconService.serialThreadStatusUpdated(phoneState, 0, appPayload.length);
            sendNextBlock();
        } else if (Arrays.equals(pkt, ROMLOADER_BLOCK_ACK)) {
            Log.d(TAG, "Received ROMLOADER_BLOCK_ACK from phone");
            if (phoneState == PhoneState.DOWNLOAD_APP_BLOCKS) {
                sendStatusUpdate();
                sendNextBlock();
            } else if (phoneState == PhoneState.APP_CHECKSUM) {
                byte[] checksumCmd = new byte[3];
                checksumCmd[0] = '<';
                checksumCmd[1] = 'c';
                checksumCmd[2] = (byte)((~appChecksum) & 0xff);
                usbSerialPort.write(checksumCmd);
                sendStatusUpdate();
            } else {
                Log.d(TAG, "... in an unexpected phoneState; switching to ERROR");
                phoneState = PhoneState.ERROR;
                sendStatusUpdate();
            }
        } else if (phoneState == PhoneState.APP_CHECKSUM && pkt[0] == '>') {
            if (pkt[1] == 'c') {
                if (pkt[2] == appChecksum) {
                    Log.d(TAG, "Checksum confirmed, branching to executable");
                    phoneState = PhoneState.BRANCH;
                    usbSerialPort.write(ROMLOADER_BRANCH);
                } else {
                    Log.w(TAG, "Unexpected value in romloader checksum ack");
                    phoneState = PhoneState.ERROR;
                }
            } else if (pkt[1] == 'C') {
                Log.w(TAG, "Romloader hecksum NACKed");
                phoneState = PhoneState.ERROR;
            } else {
                Log.d(TAG, "Unexpected response in APP_CHECKSUM phoneState");
                phoneState = PhoneState.ERROR;
            }
            sendStatusUpdate();
        } else if (phoneState == PhoneState.BRANCH && Arrays.equals(pkt, ROMLOADER_BRANCH_ACK)) {
            phoneState = PhoneState.APP_RUNNING;
            Log.d(TAG, "Phone app is now running");
            sendStatusUpdate();
        } else {
            Log.w(TAG, "Unexpected data received in handleRomloaderPkt()");
            phoneState = PhoneState.ERROR;
            sendStatusUpdate();
        }
    }

    public void run() {
        chainloaderPayload = getChainloader();
        phoneState = PhoneState.PROMPT1;
        serialState = SerialState.NONE;

        try {
            usbSerialPort.open();
            usbSerialPort.setBaudRate(115200);

            for (;;) {
                byte[] buf = usbSerialPort.read(4096);
                long recvTime = SystemClock.elapsedRealtime();
                if (recvTime - lastRecvTime > SERIAL_STATE_TIMEOUT) {
                    serialState = SerialState.NONE;
                    serialBufReadPtr = serialBufWritePtr;
                }
                lastRecvTime = recvTime;

                for (int i = 0; i < buf.length; i++) {
                    if (serialState == SerialState.NONE) {
                        if (buf[i] == BOOTLOADER_PKT_INITIAL_BYTE) {
                            writeSerialBufByte(buf[i]);
                            serialState = SerialState.BOOTLOADER_PKT;
                        } else if (buf[i] == HDLC_FLAG) {
                            writeSerialBufByte(buf[i]);
                            serialState = SerialState.HDLC_PKT;
                        } else if (buf[i] == ROMLOADER_PKT_INITIAL_BYTE_TO_PHONE ||
                                buf[i] == ROMLOADER_PKT_INITAL_BYTE_FROM_PHONE) {
                            writeSerialBufByte(buf[i]);
                            serialState = SerialState.ROMLOADER_PKT;
                        }
                    } else if (serialState == SerialState.BOOTLOADER_PKT) {
                        writeSerialBufByte(buf[i]);
                        if (getSerialBufSize() == BOOTLOADER_PKT_LENGTH) {
                            handleBootloaderPkt(getPacket());
                            serialState = SerialState.NONE;
                        }
                    } else if (serialState == SerialState.HDLC_PKT) {
                        writeSerialBufByte(buf[i]);
                        if (buf[i] == HDLC_FLAG) {
                            if (phoneState != PhoneState.APP_RUNNING) {
                                phoneState = PhoneState.APP_RUNNING;
                            }
                            osmoconService.recvFromPhone(getPacket());
                            serialState = SerialState.NONE;
                        }
                    } else if (serialState == SerialState.ROMLOADER_PKT) {
                        writeSerialBufByte(buf[i]);
                        if (haveCompleteRomloaderPkt()) {
                            handleRomloaderPkt(getPacket());
                            serialState = SerialState.NONE;
                        }
                    } else {
                        Log.d(TAG,String.format(
                                "Received unexpected byte from serial: %02x", buf[i]));
                    }
                }
            }
        } catch (Exception ex) {
            Log.w(TAG, ex);
        }
    }

    private byte computeXorChkSum(byte[] data) {
        byte xorChkSum = 0;
        for (byte b : data) {
            xorChkSum ^= b;
        }
        return xorChkSum;
    }

    private byte[] getChainloader() {
        int payloadSize = CHAINLOADER.length;
        boolean useXor = (phoneType == PhoneType.C123xor || phoneType == PhoneType.C140xor);
        boolean useMagic = (phoneType == PhoneType.C140 || phoneType == PhoneType.C140xor);
        if (useMagic) {
            if (payloadSize <= MAGIC_OFFSET) {
                payloadSize = MAGIC_OFFSET + MAGIC.length;
            }
        }
        byte[] dnloadData = new byte[(useXor ? 2 : 0) + 2 + HDR_C123.length + payloadSize];
        int offset = 0;
        if (useXor) {
            dnloadData[offset++] = 0x02;
        }
        dnloadData[offset++] = (byte)(((HDR_C123.length + payloadSize) >> 8) & 0xff);
        dnloadData[offset++] = (byte)((HDR_C123.length + payloadSize) & 0xff);
        switch (phoneType) {
            case C123:
            case C123xor:
            case C140:
            case C140xor:
                System.arraycopy(HDR_C123, 0, dnloadData, offset, HDR_C123.length);
                offset += HDR_C123.length;
                break;
            case C155:
                System.arraycopy(HDR_C123, 0, dnloadData, offset, HDR_C123.length);
                offset += HDR_C123.length;
                break;
        }
        System.arraycopy(CHAINLOADER, 0, dnloadData, offset, CHAINLOADER.length);
        if (useMagic) {
            System.arraycopy(MAGIC, 0,
                    dnloadData, MAGIC_OFFSET + (useXor ? 1 : 0), MAGIC.length);
        }
        if (useXor) {
            dnloadData[dnloadData.length - 1] = computeXorChkSum(dnloadData);
        }
        return dnloadData;
    }

    private void sendNextBlock() throws IOException {
        int payloadLen = appPayload.length - appPayloadPtr;
        if (payloadLen > maxBlockSize - 10) {
            payloadLen = maxBlockSize - 10;
        }
        byte[] block = new byte[payloadLen + 10];
        block[0] = '<';
        block[1] = 'w';
        block[2] = 0x01;
        block[3] = 0x01;
        block[4] = (byte)((payloadLen >> 8) & 0xff);
        block[5] = (byte)(payloadLen & 0xff);
        block[6] = (byte)((blockMemPtr >> 24) & 0xff);
        block[7] = (byte)((blockMemPtr >> 16) & 0xff);
        block[8] = (byte)((blockMemPtr >> 8) & 0xff);
        block[9] = (byte)(blockMemPtr & 0xff);
        System.arraycopy(appPayload, appPayloadPtr, block, 10, payloadLen);

        usbSerialPort.write(block);

        byte blockChecksum = 5;
        for (int i = 5; i < block.length; i++) {
            blockChecksum += block[i];
        }
        appChecksum += (~blockChecksum) & 0xff;

        appPayloadPtr += payloadLen;
        blockMemPtr += payloadLen;
        if (appPayloadPtr >= appPayload.length) {
            phoneState = PhoneState.APP_CHECKSUM;
        }
    }

    // It would be nice if we could just call usbSerialPort.read() with a timeout to send these,
    // but read() requires using the async API to allow cancelling reads (e.g., when logging is
    // turned off). But the async API doesn't support timeouts on anything below Android 8.
    // And you know how well Android phones are updated...
    class BeaconThread extends Thread {
        public void run() {
            for (;;) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ex) {
                    return;
                }
                if (phoneState != PhoneState.IDENT) {
                    return;
                }
                Log.d(TAG, "Sending beacon...");
                try {
                    usbSerialPort.write(ROMLOADER_IDENT);
                } catch (IOException ex) {
                    Log.d(TAG, "IOException writing ROMLOADER_IDENT");
                }
            }
        }
    }
}
