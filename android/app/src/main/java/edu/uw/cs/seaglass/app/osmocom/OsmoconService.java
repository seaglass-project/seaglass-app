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

import android.content.Intent;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import edu.uw.cs.seaglass.app.Utils;
import edu.uw.cs.seaglass.app.driver.USBSerialPort;

public class OsmoconService {
    private OsmoconSerialThread serialThread;
    private OsmoconSocketThread socketThread;

    public static final String OSMOCON_STATUS_UPDATE = "OSMOCON_STATUS_UPDATE";
    public static final String OSMOCON_CONSOLE_DATA_RECEIVED = "OSMOCON_CONSOLE_DATA_RECEIVED";
    public static final String OSMOCON_CONSOLE_DATA_EXTRA =
            "edu.uw.cs.seaglass.app.osmocom.OsmoconService.ConsoleData";
    public static final String OSMOCON_STATUS_STATE =
            "edu.uw.cs.seaglass.app.osmocom.OsmoconService.STATE";
    public static final String OSMOCON_STATUS_UPLOAD_PERCENT =
            "edu.uw.cs.seaglass.app.osmocom.OsmoconService.UPLOAD_PERCENT";
    private static final String TAG = Utils.TAG_PREFIX + "OsmoSocketThread";
    private static final String CONSOLE_TAG = Utils.TAG_PREFIX + "Console";

    static final byte SC_DLCI_L1A_L23 = 5;
    static final byte SC_DLCI_CONSOLE = 10;

    private LocalBroadcastManager localBroadcastManager;

    public OsmoconService(LocalBroadcastManager localBroadcastManager,
                          String socketName, USBSerialPort usbSerialPort,
                          PhoneType phoneType, byte[] appPayload) throws IOException {
        this.localBroadcastManager = localBroadcastManager;
        serialThread = new OsmoconSerialThread(this, usbSerialPort, phoneType, appPayload);
        socketThread = new OsmoconSocketThread(this, socketName);
        serialThread.start();
        socketThread.start();
    }

    void serialThreadStatusUpdated(PhoneState phoneState,
                                   int payloadBytesSent, int payloadTotalSize) {
        Intent intent = new Intent(OSMOCON_STATUS_UPDATE);
        intent.putExtra(OSMOCON_STATUS_STATE, phoneState);
        if (phoneState == PhoneState.DOWNLOAD_APP_BLOCKS) {
            intent.putExtra(OSMOCON_STATUS_UPLOAD_PERCENT,
                    (float)payloadBytesSent / payloadTotalSize);
        }
        localBroadcastManager.sendBroadcast(intent);
    }

    private void consoleDataReceived(String data) {
        Intent intent = new Intent(OSMOCON_CONSOLE_DATA_RECEIVED);
        intent.putExtra(OSMOCON_CONSOLE_DATA_EXTRA, data);
        localBroadcastManager.sendBroadcast(intent);
        Log.d(CONSOLE_TAG, data);
    }

    void recvFromPhone(byte[] hdlcBuf) throws IOException {
        byte[] payload = Utils.hdlcToRaw(hdlcBuf);
        byte dlci = hdlcBuf[1];
        switch (dlci) {
            case SC_DLCI_L1A_L23:
                socketThread.write(payload);
                break;
            case SC_DLCI_CONSOLE:
                consoleDataReceived(new String(payload, StandardCharsets.US_ASCII));
                break;
            default:
                Log.w(TAG, "Unknown dlci!");
                break;
        }
    }

    void sendToPhone(byte dlci, byte[] payload) throws IOException {
        byte[] hdlcBuf = Utils.rawToHDLC(dlci, payload);
        serialThread.write(hdlcBuf);
    }
}
