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

package edu.uw.cs.seaglass.app.ui;

import edu.uw.cs.seaglass.app.db.Band;
import edu.uw.cs.seaglass.app.db.SpectrumMeasurement;

public class SpectrogramData {
    private Band band;
    private int[] powerMeas;
    private int numArfcns;
    private int beginArfcn;
    private String[] arfcnNames;

    private static final int NUM_BAND_850 = 124;
    private static final int NUM_BAND_900 = 124;
    private static final int NUM_BAND_1800 = 374;
    private static final int NUM_BAND_1900 = 299;

    private boolean dataNotSeen;
    private boolean wasDataAdded;

    public SpectrogramData(Band band){
        this.band = band;

        if (band == Band.GSM850){
            numArfcns = NUM_BAND_850;
            beginArfcn = 128;
        }
        else if (band == Band.GSM900){
            numArfcns = NUM_BAND_900;
            beginArfcn = 0;
        }
        else if (band == Band.DCS1800){
            numArfcns = NUM_BAND_1800;
            beginArfcn = 512;
        }
        else {
            numArfcns = NUM_BAND_1900;
            beginArfcn = 512;
        }

        this.powerMeas = new int[numArfcns];
        for (int i = 0; i < numArfcns; i++){
            this.powerMeas[i] = -110;
        }

        arfcnNames = new String[numArfcns];
        for (int i = beginArfcn; i < (beginArfcn + arfcnNames.length); i++){
            arfcnNames[i - beginArfcn] = String.valueOf(i);
        }

        this.dataNotSeen = true;
        this.wasDataAdded = false;
    }

    public int getNumArfcns(){
        return this.numArfcns;
    }

    public Band getBand(){
        return this.band;
    }

    public void setPowerMeas(SpectrumMeasurement sm){
        int arfcnIndex;
        Band curBand = sm.measurementHeader.band;
        boolean lastArfcn = false;

        if (curBand == Band.GSM850){
            arfcnIndex = sm.measurementHeader.arfcn - 128;
            this.powerMeas[arfcnIndex] = sm.measurementHeader.dBm;

            if (arfcnIndex == NUM_BAND_850 - 1){
                lastArfcn = true;
            }
        }
        else if (curBand == Band.GSM900){
            arfcnIndex = sm.measurementHeader.arfcn - 1;
            this.powerMeas[arfcnIndex] = sm.measurementHeader.dBm;

            if (arfcnIndex == NUM_BAND_900 - 1){
                lastArfcn = true;
            }
        }
        else if (curBand == Band.DCS1800){
            arfcnIndex = sm.measurementHeader.arfcn - 512;
            this.powerMeas[arfcnIndex] = sm.measurementHeader.dBm;

            if (arfcnIndex == NUM_BAND_1800 - 1){
                lastArfcn = true;
            }
        }
        else {
            arfcnIndex = sm.measurementHeader.arfcn - 512;
            this.powerMeas[arfcnIndex] = sm.measurementHeader.dBm;

            if (arfcnIndex == NUM_BAND_1900 - 1){
                lastArfcn = true;
            }
        }

        if (lastArfcn){
            this.dataNotSeen = true;
        }

        this.wasDataAdded = true;
    }

    public int[] getRawPowerMeas(){
        return this.powerMeas;
    }

    public String[] getArfcns() {
        return this.arfcnNames;
    }

    public boolean dataNotSeen(){
        return this.dataNotSeen;
    }

    public void setDataSeen(){
        this.dataNotSeen = false;
    }

    public boolean wasDataAdded(){
        return this.wasDataAdded;
    }
}
