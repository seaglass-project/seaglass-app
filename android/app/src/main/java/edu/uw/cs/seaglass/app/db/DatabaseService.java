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

import androidx.core.content.FileProvider;
import androidx.room.Room;
import edu.uw.cs.seaglass.app.Utils;

public class DatabaseService extends Service {
    private static final String TAG = Utils.TAG_PREFIX + "DatabaseService";

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
        db = Room.databaseBuilder(this, AppDatabase.class, "spiglass-ui-db").build();
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
        File dbCache = new File(getCacheDir(), "spiglass-ui-db");
        dbCache.deleteOnExit();
        // Hopefully this ensures that the DB is in a consistent state...
        synchronized (db) {
            copyFile(getDatabasePath("spiglass-ui-db"), dbCache);
        }
        return FileProvider.getUriForFile(this,
                "edu.uw.cs.seaglass.app.FileProvider", dbCache);
    }

    public boolean deleteExportCache() {
        return new File(getCacheDir(), "spiglass-ui-db").delete();
    }
}
