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

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

import java.util.ArrayList;
import java.util.Arrays;

import androidx.core.content.FileProvider;
import androidx.room.Room;
import edu.uw.cs.seaglass.app.Utils;

import java.util.List;

public class DatabaseService extends Service {
    private static final String TAG = Utils.TAG_PREFIX + "DatabaseService";
    private static final String DB_NAME = "seaglass-app-db";

    private static final int MARK_LIMIT = 500;

    private final IBinder mBinder = new LocalBinder();
    private AppDatabase db;

    public DatabaseService() {
    }

    public class LocalBinder extends Binder {
        public DatabaseService getService() {
            // Return this instance of LocalService so clients can call public methods
            return DatabaseService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        db = Room.databaseBuilder(this, AppDatabase.class, DB_NAME).build();
    }

    public void insertLocationMeasurement(LocationMeasurement locationMeasurement) {
        synchronized (db) {
            db.locationMeasurementDAO().insert(locationMeasurement);
        }
    }

    public void insertSpectrumMeasurement(SpectrumMeasurement spectrumMeasurement) {
        synchronized (db) {
            db.spectrumMeasurementDAO().insert(spectrumMeasurement);
        }
    }

    public void insertCellObservation(CellObservation cellObservation) {
        synchronized (db) {
            db.cellObservationDAO().insert(cellObservation);
        }
    }

    public void insertGSMPacket(GSMPacket gsmPacket) {
        synchronized (db) {
            db.gsmPacketDAO().insert(gsmPacket);
        }
    }

    private void copyFile(File src, File dest) throws IOException {
        FileChannel srcChan;
        FileChannel destChan;

        srcChan = new FileInputStream(src).getChannel();
        destChan = new FileOutputStream(dest).getChannel();
        destChan.transferFrom(srcChan, 0, srcChan.size());
        srcChan.close();
        destChan.close();
    }

    public Uri getExportUri() throws IOException {
        File dbCache = new File(getCacheDir(), DB_NAME);
        dbCache.deleteOnExit();
        // Hopefully this ensures that the DB is in a consistent state...
        synchronized (db) {
            copyFile(getDatabasePath(DB_NAME), dbCache);
        }
        return FileProvider.getUriForFile(this,
                "edu.uw.cs.seaglass.app.FileProvider", dbCache);
    }

    public boolean deleteExportCache() {
        return new File(getCacheDir(), DB_NAME).delete();
    }

    public List<CellObservation> getUnsyncedCellObservations(int maxBatchSize){
        synchronized (db) {
            return db.cellObservationDAO().getUnsynced(maxBatchSize);
        }
    }

    public List<GSMPacket> getUnsyncedGSMPackets(int maxBatchSize){
        synchronized (db) {
            return db.gsmPacketDAO().getUnsynced(maxBatchSize);
        }
    }

    public List<SpectrumMeasurement> getUnsyncedSpectrumMeasurements(int maxBatchSize){
        synchronized (db) {
            return db.spectrumMeasurementDAO().getUnsynced(maxBatchSize);
        }
    }

    public List<LocationMeasurement> getUnsyncedLocationMeasurements(int maxBatchSize){
        synchronized (db) {
            return db.locationMeasurementDAO().getUnsynced(maxBatchSize);
        }
    }

    public void markCellObservations(List<CellObservation> cellObservations) {
        for (CellObservation cellObservation : cellObservations) {
            db.cellObservationDAO().markSynced(cellObservation.id);
        }
    }

    public void markGSMPackets(List<GSMPacket> gsmPackets) {
        for (GSMPacket gsmPacket : gsmPackets) {
            db.gsmPacketDAO().markSynced(gsmPacket.id);
        }
    }

    public void markSpectrumMeasurements(List<SpectrumMeasurement> spectrumMeasurements) {
        for (SpectrumMeasurement spectrumMeasurement : spectrumMeasurements) {
            db.spectrumMeasurementDAO().markSynced(spectrumMeasurement.id);
        }
    }

    public void markLocationMeasurements(List<LocationMeasurement> locationMeasurements) {
        for (LocationMeasurement locationMeasurement : locationMeasurements) {
            db.locationMeasurementDAO().markSynced(locationMeasurement.id);
        }
    }
}
