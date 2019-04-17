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

package edu.uw.cs.seaglass.app.db;

public enum Band {
    GSM900(0), GSM850(1), DCS1800(2), PCS1900(3);

    private int id;

    Band(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static String bandToString(Band band){
        String bandStr;

        if (band == Band.GSM850){
            bandStr = "GSM850";
        }
        else if (band == Band.GSM900){
            bandStr = "GSM900";
        }
        else if (band == Band.DCS1800){
            bandStr = "DCS1800";
        }
        else{
            bandStr = "PCS1900";
        }

        return bandStr;
    }

    public static Band intToBand(int b) {
        switch (b) {
            case 0:
                return GSM900;
            case 1:
                return GSM850;
            case 2:
                return DCS1800;
            case 3:
                return PCS1900;
            default:
                throw new IllegalArgumentException();
        }
    }
}
