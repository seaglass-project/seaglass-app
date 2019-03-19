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

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

@Database(entities = {CellObservation.class, GSMPacket.class,
        SpectrumMeasurement.class, LocationMeasurement.class}, version = 2, exportSchema = false)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {
    public abstract CellObservationDAO cellObservationDAO();
    public abstract GSMPacketDAO gsmPacketDAO();
    public abstract SpectrumMeasurementDAO spectrumMeasurementDAO();
    public abstract LocationMeasurementDAO locationMeasurementDAO();
}
