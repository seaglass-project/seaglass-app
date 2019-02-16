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

import java.io.DataOutputStream;
import java.io.IOException;

import edu.uw.cs.seaglass.app.db.Band;

public class Utils {
    public static final String TAG_PREFIX = "SpiG-";

    public static Band bandFromARFCN(short arfcn, boolean isPCS) throws IllegalArgumentException {
        if (arfcn <= 124) {
            return Band.GSM900;
        } else if (arfcn >= 128 && arfcn <= 251) {
            return Band.GSM850;
        } else if (arfcn >= 512 && arfcn <= 885 && !isPCS) {
            return Band.DCS1800;
        } else if (arfcn >= 512 && arfcn <= 810 && isPCS) {
            return Band.PCS1900;
        } else {
            throw new IllegalArgumentException("Unknown band for ARFCN " + arfcn);
        }
    }

    // Convert from the OSMOCOM arfcn format to the usual one (where PCS has the top bit set)
    public static short getAdjustedARFCN(short arfcn){
        return (short) (arfcn & 0x7fff);
    }

    public static boolean isPCS(short arfcn){
        return (arfcn & 0x8000) != 0;
    }

    // TODO: Write own version of this function
    // Code for this comes from
    // https://stackoverflow.com/questions/20932102/execute-shell-command-from-android
    public static void sudo(String...strings) {
        try{
            Process su = Runtime.getRuntime().exec("su");
            DataOutputStream outputStream = new DataOutputStream(su.getOutputStream());

            for (String s : strings) {
                outputStream.writeBytes(s+"\n");
                outputStream.flush();
            }

            outputStream.writeBytes("exit\n");
            outputStream.flush();
            try {
                su.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            outputStream.close();
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    public static byte[] rawToHDLC(byte dlci, byte[] payload) {
        int hdlcLen = 4; // FLAG + dlci + control + FLAG

        for (int i = 0; i < payload.length; i++) {
            if (payload[i] == 0x7d || payload[i] == 0x7e || payload[i] == 0x00) {
                hdlcLen++;
            }
            hdlcLen++;
        }

        byte[] hdlcBuf = new byte[hdlcLen];
        hdlcBuf[0] = 0x7e; // FLAG
        hdlcBuf[1] = dlci;
        hdlcBuf[2] = 3;
        hdlcBuf[hdlcLen - 1] = 0x7e;
        int hdlcOffset = 3;
        for (int i = 0; i < payload.length; i++) {
            if (payload[i] == 0x7d || payload[i] == 0x7e || payload[i] == 0x00) {
                hdlcBuf[hdlcOffset++] = 0x7d; // ESCAPE
                hdlcBuf[hdlcOffset++] = (byte)(payload[i] ^ 0x20);
            } else {
                hdlcBuf[hdlcOffset++] = payload[i];
            }
        }

        return hdlcBuf;
    }

    public static byte[] hdlcToRaw(byte[] hdlcBuf) {
        int payloadLen = 0;
        for (int i = 3; i < hdlcBuf.length - 1; i++) {
            if (hdlcBuf[i] != 0x7d) {
                payloadLen++;
            }
        }
        byte[] payload = new byte[payloadLen];
        int payloadOffset = 0;
        for (int i = 3; i < hdlcBuf.length - 1; i++) {
            if (hdlcBuf[i] == 0x7d) {
                payload[payloadOffset++] = (byte)(hdlcBuf[++i] ^ 0x20);
            } else {
                payload[payloadOffset++] = hdlcBuf[i];
            }
        }

        return payload;
    }

    public static boolean isHDLCPacket(byte[] buf) {
        if (buf.length < 4)
            return false;
        return (buf[0] == 0x7e && buf[2] == 0x03 && buf[buf.length - 1] == 0x7e);
    }
}
