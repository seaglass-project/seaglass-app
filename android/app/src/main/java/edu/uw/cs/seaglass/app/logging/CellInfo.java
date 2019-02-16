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

import java.io.Serializable;
import java.util.Calendar;
import java.util.Date;
import java.nio.*;

import edu.uw.cs.seaglass.app.db.Band;
import edu.uw.cs.seaglass.app.db.CellObservation;

public class CellInfo implements Serializable {
    private int mcc;
    private int mnc;
    private int lac;
    private int cell_id;
    private int arfcn;
    private int dBm;
    private Band band;

    private Date datetime;

    public static CellInfo cellInfoBuilder(CellObservation co){
        int cell_id;
        int mcc;
        int mnc;
        int lac;

        byte[] si3 = co.si3;

        ByteBuffer wrapped;
        StringBuilder sb;

        // First two bytes are 0
        byte[] raw_cell_id = new byte[4];
        byte[] raw_lac = new byte[4];

        // The 4th and 5th bytes the cell id
        raw_cell_id[0] = 0;
        raw_cell_id[1] = 0;
        raw_cell_id[2] = si3[3];
        raw_cell_id[3] = si3[4];
        wrapped = ByteBuffer.wrap(raw_cell_id);
        wrapped.order(ByteOrder.BIG_ENDIAN);
        cell_id = wrapped.getInt();

        raw_lac[0] = 0;
        raw_lac[1] = 0;
        raw_lac[2] = si3[8];
        raw_lac[3] = si3[9];
        wrapped = ByteBuffer.wrap(raw_lac);
        wrapped.order(ByteOrder.BIG_ENDIAN);
        lac = wrapped.getInt();

        // MCC and MNC are encoded in a completely stupid way:
        // bytes = 21 36 54 --> MCC -> 123 and MNC -> 456
        // The half-bytes represent decimal digits
        sb = new StringBuilder();
        sb.append(si3[5] & 0x0f);
        sb.append((si3[5] >> 4) & 0x0f);
        sb.append((si3[6] >> 4) & 0x0f);
        mcc = Integer.parseInt(sb.toString());

        sb = new StringBuilder();
        sb.append(si3[7] & 0x0f);
        sb.append((si3[7] >> 4) & 0x0f);
        sb.append(si3[6] & 0x0f);
        mnc = Integer.parseInt(sb.toString());

        return new CellInfo(mcc, mnc, lac, cell_id,
                co.measurementHeader.arfcn, co.measurementHeader.dBm,
                co.measurementHeader.band);
    }

    public CellInfo(int mcc, int mnc, int lac, int cell_id, int arfcn, int dBm, Band band) {
        this.mcc = mcc;
        this.mnc = mnc;
        this.lac = lac;
        this.cell_id = cell_id;
        this.arfcn = arfcn;
        this.dBm = dBm;
        this.band = band;

        updateTimestamp();
    }

    public void updateTimestamp(){
        this.datetime = Calendar.getInstance().getTime();
    }

    @Override
    public boolean equals(Object o){
        if (o == this){
            return true;
        }

        if (!(o instanceof CellInfo)){
            return false;
        }

        CellInfo ci = (CellInfo) o;

        return ci.mcc == this.mcc &&
                ci.mnc == this.mnc &&
                ci.lac == this.lac &&
                ci.cell_id == this.cell_id;
    }

    public int getMCC(){
        return mcc;
    }

    public int getMNC(){
        return mnc;
    }

    public int getLAC(){
        return lac;
    }

    public int getCellId(){
        return cell_id;
    }

    public int getArfcn(){
        return arfcn;
    }

    public int getdBm(){
        return dBm;
    }

    public Band getBand(){
        return band;
    }
}
