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

import java.util.Date;
import java.text.SimpleDateFormat;

import android.Manifest;
import android.app.Notification;
import android.content.pm.PackageManager;
import android.os.Bundle;
import androidx.annotation.NonNull;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.Menu;
import android.location.Location;
import android.widget.TextView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;

import edu.uw.cs.seaglass.app.Utils;

public class LocationActivity extends AppCompatActivity implements OnMapReadyCallback,
        ActivityCompat.OnRequestPermissionsResultCallback {

    private MapView mMapView;
    private GoogleMap mMap;
    private boolean mHaveLocationPermission;
    private FusedLocationProviderClient mFusedLocationClient;
    private Notification mLoggingServiceActiveNotification;
    private BottomNavigationView navigation;

    private static final String TAG = Utils.TAG_PREFIX + "LocationActivity";
    private static final String MAPVIEW_BUNDLE_KEY = "MapViewBundleKey";
    private static final int REQUEST_FINE_LOCATION_PERMISSION_CODE = 1;

    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            return false;
        }
    };

    private class LoggerLocationCallback extends LocationCallback {
        @Override
        public void onLocationAvailability(LocationAvailability locationAvailability) {

        }
        @Override
        public void onLocationResult(LocationResult locationResult) {
            synchronized (this) {
                TextView tv;
                String formattedDate;
                Location lastLocation = locationResult.getLastLocation();

                double lat = lastLocation.getLatitude();
                double lon = lastLocation.getLongitude();
                long time = lastLocation.getTime();

                Date date = new Date(time);
                SimpleDateFormat dateFormatter = new SimpleDateFormat("HH:mm:ss MM-dd-yy");
                dateFormatter.setTimeZone(java.util.TimeZone.getTimeZone("GMT-7"));
                formattedDate = dateFormatter.format(date);

                tv = (TextView) findViewById(R.id.latValueTextView);
                tv.setText(" " + String.valueOf(lat));

                tv = (TextView) findViewById(R.id.lonValueTextView);
                tv.setText(" " + String.valueOf(lon));

                tv = (TextView) findViewById(R.id.timeValueTextView);
                tv.setText(" " + formattedDate);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location);

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        LocationRequest locationRequest = new LocationRequest();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(5000);
        mFusedLocationClient.requestLocationUpdates(locationRequest, new LocationActivity.LoggerLocationCallback(),null);

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_FINE_LOCATION_PERMISSION_CODE);
        } else {
            onLocationPermissionAcquired();
        }

        Bundle mapViewBundle = null;
        if (savedInstanceState != null) {
            mapViewBundle = savedInstanceState.getBundle(MAPVIEW_BUNDLE_KEY);
        }
        mMapView = (MapView)findViewById(R.id.mapView);
        mMapView.onCreate(mapViewBundle);
        mMapView.getMapAsync(this);

        navigation = (BottomNavigationView) findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);
    }



    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        Bundle mapViewBundle = outState.getBundle(MAPVIEW_BUNDLE_KEY);
        if (mapViewBundle == null) {
            mapViewBundle = new Bundle();
            outState.putBundle(MAPVIEW_BUNDLE_KEY, mapViewBundle);
        }

        mMapView.onSaveInstanceState(mapViewBundle);
    }


    @Override
    protected void onResume() {
        super.onResume();
        mMapView.onResume();

        Menu menu = navigation.getMenu();
        MenuItem menuItem = menu.getItem(1);
        menuItem.setChecked(true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mMapView.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mMapView.onStop();
    }

    @Override
    protected void onPause() {
        mMapView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mMapView.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mMapView.onLowMemory();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_FINE_LOCATION_PERMISSION_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                onLocationPermissionAcquired();
            }
        }
    }

    @Override
    public void onMapReady(GoogleMap map) {
        mMap = map;
        if (mHaveLocationPermission) {
            onMapAndLocationPermissionAcquired();
        }
    }

    @Override
    public void onBackPressed() {}

    private void onMapAndLocationPermissionAcquired() {
        // TODO: Should handle this when the user doesn't
        // give location.
        mMap.setMyLocationEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);
    }

    private void onLocationPermissionAcquired() {
        mHaveLocationPermission = true;
        if (mMap != null) {
            onMapAndLocationPermissionAcquired();
        }
    }
}
