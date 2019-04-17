package edu.uw.cs.seaglass.app.db;

import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Data.Builder;

import edu.uw.cs.seaglass.app.Options;
import edu.uw.cs.seaglass.app.Utils;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.IBinder;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.util.List;

import com.android.volley.toolbox.Volley;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.RequestQueue;
import com.android.volley.AuthFailureError;

public class SyncUploadWorker extends Worker {

    private DatabaseService mDatabaseService;
    private static final String TAG = Utils.TAG_PREFIX + "SyncUploadWorker";
    private static final int DB_QUERY_LIMIT = 10000;
    private static final String HTTPS_HEADER = "https://";

    List<CellObservation> cellObsToSync;
    List<GSMPacket> gsmPktsToSync;
    List<SpectrumMeasurement> spectrumMeasToSync;
    List<LocationMeasurement> locationMeasToSync;

    private RequestQueue requestQueue;

    private String endpoint;
    private String uuid;
    private String uploadKey;

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

    public SyncUploadWorker(
            @NonNull Context context,
            @NonNull WorkerParameters params) {

        super(context, params);

        Intent databaseServiceIntent = new Intent(context, DatabaseService.class);
        context.bindService(databaseServiceIntent, mDatabaseConnection, Context.BIND_AUTO_CREATE);

        Options options = new Options(context);
        endpoint = HTTPS_HEADER + options.getSyncServer();

        uuid = options.getUUID();
        uploadKey = options.getUploadKey();

        requestQueue = Volley.newRequestQueue(context);
    }

    @Override
    public Result doWork() {
        cellObsToSync = mDatabaseService.getUnsyncedCellObservations(DB_QUERY_LIMIT);
        gsmPktsToSync = mDatabaseService.getUnsyncedGSMPackets(DB_QUERY_LIMIT);
        spectrumMeasToSync = mDatabaseService.getUnsyncedSpectrumMeasurements(DB_QUERY_LIMIT);
        locationMeasToSync = mDatabaseService.getUnsyncedLocationMeasurements(DB_QUERY_LIMIT);

        try {
            String requestJson = getJson(cellObsToSync, gsmPktsToSync,
                    spectrumMeasToSync, locationMeasToSync);

            postRequest(requestJson);
            Log.d(TAG, "Sync Work Finished.");

            return Result.success();
        }
        catch (JSONException e){
            Log.e(TAG, "Error constructing JSON for POST request");
            return Result.failure();
        }
    }

    private String getJson(List<CellObservation> cos, List<GSMPacket> gsmpkts,
                           List<SpectrumMeasurement> sms, List<LocationMeasurement> lms)
                  throws JSONException
    {

        JSONObject postData = new JSONObject();
        JSONArray jsonArray;

        postData.put("uuid", uuid);
        postData.put("upload-key", uploadKey);

        jsonArray = new JSONArray();
        for (CellObservation co : cos) {
            jsonArray.put(CellObservation.getJson(co));
        }
        postData.put("cell_observations", jsonArray);

        jsonArray = new JSONArray();
        for (GSMPacket gp : gsmpkts) {
            jsonArray.put(GSMPacket.getJson(gp));
        }
        postData.put("gsm_packets", jsonArray);

        jsonArray = new JSONArray();
        for (SpectrumMeasurement sm : sms) {
            jsonArray.put(SpectrumMeasurement.getJson(sm));
        }
        postData.put("spectrum_measurements", jsonArray);

        jsonArray = new JSONArray();
        for (LocationMeasurement lm : lms) {
            jsonArray.put(LocationMeasurement.getJson(lm));
        }
        postData.put("location_measurements", jsonArray);
        //Log.d(TAG, postData.toString());

        return postData.toString();
    }

    private void postRequest(final String json){

        StringRequest request = new StringRequest(Request.Method.POST, endpoint,
                new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                Log.d(TAG, "POST Request for DB Sync Succeeded");

                int[] coids = new int[cellObsToSync.size()];
                int[] gpids = new int[gsmPktsToSync.size()];
                int[] smids = new int[spectrumMeasToSync.size()];
                int[] lmids = new int[locationMeasToSync.size()];

                for (int i = 0; i < coids.length; i++){
                    coids[i] = cellObsToSync.get(i).id;
                }
                for (int i = 0; i < gpids.length; i++){
                    gpids[i] = gsmPktsToSync.get(i).id;
                }
                for (int i = 0; i < smids.length; i++){
                    smids[i] = spectrumMeasToSync.get(i).id;
                }
                for (int i = 0; i < lmids.length; i++){
                    lmids[i] = locationMeasToSync.get(i).id;
                }

                Data dbIds = new Data.Builder()
                        .putIntArray(ClearSyncWorker.CELL_OBSERVATION_IDS, coids)
                        .putIntArray(ClearSyncWorker.GSM_PACKET_IDS, gpids)
                        .putIntArray(ClearSyncWorker.SPECTRUM_MEAS_IDS, smids)
                        .putIntArray(ClearSyncWorker.LOCATION_MEAS_IDS, lmids)
                        .build();

                OneTimeWorkRequest clearSyncRequest =
                        new OneTimeWorkRequest.Builder(ClearSyncWorker.class)
                                .setInputData(dbIds)
                                .build();

                WorkManager.getInstance().enqueue(clearSyncRequest);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.d(TAG, "POST Request for DB Sync Failed");
                Log.d(TAG, error.toString());
            }
        }){
            @Override
            public String getBodyContentType() {
                return "application/json; charset=utf-8";
            }

            @Override
            public byte[] getBody() throws AuthFailureError {
                try {
                    return json.getBytes("utf-8");
                } catch (UnsupportedEncodingException uee) {
                    Log.e(TAG, "POST Request invalid utf-8 encoding");
                    return null;
                }
            }
        };

        requestQueue.add(request);
    }
}