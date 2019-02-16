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

import android.util.Log;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.DatagramChannel;
import java.util.Arrays;
import java.nio.ByteBuffer;

import edu.uw.cs.seaglass.app.Utils;
import edu.uw.cs.seaglass.app.db.GSMPacket;
import edu.uw.cs.seaglass.app.db.MeasurementHeader;

public class IPGSMTAPProvider extends Thread {
    private static String IP = "127.0.0.1";
    //private static final String IP = "0.0.0.0";
    private static final int PORT = 4729;
    private static final String TAG = Utils.TAG_PREFIX + "IPGSMTAPProvider";

    private DatagramSocket socket;
    private DatagramChannel channel;
    private GSMPacketReceiver receiver;

    public IPGSMTAPProvider(GSMPacketReceiver receiver) throws
            UnknownHostException, SocketException {
        this.receiver = receiver;
        InetSocketAddress addr = new InetSocketAddress(IP, PORT);

        try {
            channel = DatagramChannel.open();
            channel.socket().bind(new InetSocketAddress(IP, PORT));
        } catch(IOException e){
            Log.e(TAG, "Problem opening the empty datagram channel", e);
        }
        //socket = new DatagramSocket(PORT, InetAddress.getByName(IP));
    }

    private void close(){
        try {
            channel.close();
        } catch(IOException e){}
    }

    private GSMPacket gsmPktFromGSMTAP(byte[] gsmtapPktBuf) throws IllegalArgumentException {
        GSMPacket pkt = new GSMPacket();
        pkt.measurementHeader = new MeasurementHeader();
        if (gsmtapPktBuf[0] != 0x02) {
            throw new IllegalArgumentException("Unexpected GSMTAP version");
        }
        pkt.measurementHeader.timestamp = System.currentTimeMillis();
        pkt.measurementHeader.arfcn =
                (short)(((gsmtapPktBuf[4] & 0xff) << 8) |
                         (gsmtapPktBuf[5] & 0xff));
        pkt.measurementHeader.arfcn = (short) (pkt.measurementHeader.arfcn & 0x3fff);
        pkt.measurementHeader.band = Utils.bandFromARFCN(pkt.measurementHeader.arfcn,
                Utils.isPCS(pkt.measurementHeader.arfcn));
        pkt.measurementHeader.dBm = gsmtapPktBuf[7];
        pkt.type = gsmtapPktBuf[2];
        pkt.subtype = gsmtapPktBuf[12];
        pkt.timeslot = gsmtapPktBuf[3];
        pkt.frameNumber = ((gsmtapPktBuf[8] & 0xff) << 24) | ((gsmtapPktBuf[9] & 0xff) << 16) |
                          ((gsmtapPktBuf[10] & 0xff) << 8) | (gsmtapPktBuf[11] & 0xff);
        pkt.payload = Arrays.copyOfRange(gsmtapPktBuf, 16 , gsmtapPktBuf.length);
        return pkt;
    }

    public void run() {
        byte[] rawBuf = new byte[39];
        ByteBuffer gsmtapPktBuf = ByteBuffer.wrap(rawBuf);
        //byte[] gsmtapPktBuf = new byte[39];
        // gsmtapPkt = new DatagramPacket(gsmtapPktBuf, gsmtapPktBuf.length);

        for (;;) {
            if (Thread.interrupted()){
                Log.d(TAG, "ISPGSMTAPProvider interrupted");
                break;
            }

            gsmtapPktBuf.clear();
            try {
                channel.receive(gsmtapPktBuf);
            } catch (ClosedByInterruptException e){
                // Thread interrupt, don't do anything
                Log.d(TAG, "ISPGSMTAPProvider interrupted");
                break;
            } catch (IOException e) {
                Log.e(TAG, "IOException thrown on channel.receive", e);
                break;
            }

            try {
                GSMPacket pkt = gsmPktFromGSMTAP(gsmtapPktBuf.array());
                receiver.onGSMPacketReceived(pkt);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, e.toString(), e);
                break;
            } catch (Exception e){
                // It is possible that when closing the logging service that the
                // receive may not exist. Just return.
                Log.d(TAG, "ISPGSMTAPProvider interrupted");
                break;
            }
        }

        close();
        return;
    }
}
