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

import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import androidx.room.Embedded;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import edu.uw.cs.seaglass.app.Utils;

@Entity
public class GSMPacket {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public boolean synced = false;

    @Embedded
    public MeasurementHeader measurementHeader;
    public byte type;
    public byte subtype;
    public byte timeslot;
    public int frameNumber;
    public byte[] payload;

    public static JSONObject getJson(GSMPacket gp) throws JSONException {
        JSONObject gpJson = new JSONObject(Utils.gson.toJson(gp));
        gpJson.remove("synced");
        return gpJson;
    }
}
