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

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import edu.uw.cs.seaglass.app.Utils;

public class OsmoconSocketThread extends Thread {
    private static final String TAG = Utils.TAG_PREFIX + "OsmoSocketThread";

    private OsmoconService osmoconService;
    private LocalServerSocket listenSocket;
    private LocalSocket clientSocket;

    public OsmoconSocketThread(OsmoconService osmoconService, String socketName) throws IOException {
        this.osmoconService = osmoconService;
        listenSocket = new LocalServerSocket(socketName);
    }

    void write(byte[] payload) throws IOException {
        byte[] pktBytes = new byte[payload.length + 2];
        pktBytes[0] = (byte)((payload.length >> 8) & 0xff);
        pktBytes[1] = (byte)(payload.length & 0xff);
        System.arraycopy(payload, 0, pktBytes, 2, payload.length);

        synchronized (this) {
            // Drop incoming packets if we don't have a layer2 client connected
            if (clientSocket == null) {
                return;
            }

            OutputStream outputStream = clientSocket.getOutputStream();
            outputStream.write(pktBytes);
        }
    }

    public void run() {
        try {
            for (;;) {
                LocalSocket acceptedSocket = listenSocket.accept();
                synchronized (this) {
                    clientSocket = acceptedSocket;
                }
                handleClient(clientSocket);
                synchronized (this) {
                    clientSocket = null;
                }
            }
        } catch (IOException ex) {
            Log.d(TAG, ex.toString());
            return;
        }
    }

    public void shutdown() {
        try {
            listenSocket.close();
        } catch (IOException ex) {
            Log.w(TAG, ex.toString());
        }
    }

    private int getLengthFromInputStream(InputStream inputStream) throws IOException {
        byte[] inBytes = new byte[2];
        int readCount = 0;
        while (readCount < 2) {
            int ret = inputStream.read(inBytes, readCount, 2 - readCount);
            if (ret < 0) {
                throw new IOException("socket closed while reading length");
            }
            readCount += ret;
        }
        return (inBytes[0] << 8) | inBytes[1];
    }

    private void handleClient(LocalSocket clientSocket) {
        try {
            InputStream inputStream = clientSocket.getInputStream();
            for (;;) {
                int len = getLengthFromInputStream(inputStream);
                byte[] payload = new byte[len];
                int readCount = 0;
                while (readCount < len) {
                    int ret = inputStream.read(payload, readCount, len - readCount);
                    if (ret < 0) {
                        clientSocket.close();
                        return;
                    }
                    readCount += ret;
                }
                osmoconService.sendToPhone(OsmoconService.SC_DLCI_L1A_L23, payload);
            }
        } catch (IOException ex) {
            Log.d(TAG, ex.toString());
        }
    }
}
