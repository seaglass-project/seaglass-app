package edu.uw.cs.seaglass.app.db;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import edu.uw.cs.seaglass.app.Utils;

public class ClearSyncWorker extends Worker {

    private DatabaseService mDatabaseService;
    private static final String TAG = Utils.TAG_PREFIX + "ClearSyncWorker";

    public static final String CELL_OBSERVATION_IDS = "CellObservationPrimaryKeys";
    public static final String GSM_PACKET_IDS = "GSMPacketPrimaryKeys";
    public static final String SPECTRUM_MEAS_IDS = "SpectrumMeasurementPrimaryKeys";
    public static final String LOCATION_MEAS_IDS = "LocationMeasurementPrimaryKeys";

    private ServiceConnection mDatabaseConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            DatabaseService.LocalBinder binder = (DatabaseService.LocalBinder) service;
            mDatabaseService = binder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            mDatabaseService = null;
        }
    };

    public ClearSyncWorker(
            @NonNull Context context,
            @NonNull WorkerParameters params) {

        super(context, params);

        Intent databaseServiceIntent = new Intent(context, DatabaseService.class);
        context.bindService(databaseServiceIntent, mDatabaseConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public Result doWork() {
        int[] coids = getInputData().getIntArray(CELL_OBSERVATION_IDS);
        int[] gmids = getInputData().getIntArray(GSM_PACKET_IDS);
        int[] smids = getInputData().getIntArray(SPECTRUM_MEAS_IDS);
        int[] lmids = getInputData().getIntArray(LOCATION_MEAS_IDS);

        mDatabaseService.markCellObservations(coids);
        mDatabaseService.markGSMPackets(gmids);
        mDatabaseService.markSpectrumMeasurements(smids);
        mDatabaseService.markLocationMeasurements(lmids);

        Log.d(TAG, "Finish Marking Records");

        return Result.success();
    }
}
