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

import android.util.Log;

import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import androidx.room.Embedded;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import edu.uw.cs.seaglass.app.Utils;

@Entity
public class CellObservation {
    public static final byte NO_TA = (byte) 0xFF;
    private static final String TAG = Utils.TAG_PREFIX + "CellObservation";

    @PrimaryKey(autoGenerate = true)
    public int id;

    @Embedded
    public MeasurementHeader measurementHeader;
    public byte bsic;
    public byte ta;

    public byte[] si1;
    public byte[] si2;
    public byte[] si2quat;
    public byte[] si3;
    public byte[] si4;
    public byte[] si13;

    public boolean synced = false;

    public static JSONObject getJson(CellObservation co) throws JSONException{
        Gson gson = new Gson();

        JSONObject cellObsJson = new JSONObject(gson.toJson(co));
        cellObsJson.remove("synced");
        return cellObsJson;
    }
}
