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

import java.util.ArrayList;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import android.graphics.Color;

import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import edu.uw.cs.seaglass.app.Utils;
import edu.uw.cs.seaglass.app.db.MeasurementHeader;
import edu.uw.cs.seaglass.app.db.SpectrumMeasurement;
import edu.uw.cs.seaglass.app.logging.LoggingService;
import edu.uw.cs.seaglass.app.db.Band;
import edu.uw.cs.seaglass.app.osmocom.OsmoconService;

import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Spinner;

import com.github.mikephil.charting.charts.*;
import com.github.mikephil.charting.data.*;
import com.github.mikephil.charting.components.*;
import com.github.mikephil.charting.components.XAxis.*;
import com.github.mikephil.charting.formatter.*;

import android.widget.AdapterView;
import android.widget.TextView;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link StatusFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class StatusFragment extends Fragment {
    private static final int MAX_CONSOLE_TEXT_LEN = 100000;

    private BarChart spectrogramChart;
    private Context mContext;
    private LocalBroadcastManager localBroadcastManager;

    private static final String TAG = Utils.TAG_PREFIX + "StatusFragment";

    private SpectrogramData spec850;
    private SpectrogramData spec900;
    private SpectrogramData spec1800;
    private SpectrogramData spec1900;

    private SpectrogramData activeSpectrogram;

    private Spinner spectrogramSpinner;
    private TextView statusTextView;
    private TextView consoleTextView;

    public StatusFragment() {
        // Required empty public constructor
    }

    public static StatusFragment newInstance() {
        StatusFragment fragment = new StatusFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_status, container, false);
        this.spectrogramChart = (BarChart) view.findViewById(R.id.spectrogram);
        this.spectrogramSpinner = (Spinner)view.findViewById(R.id.spectrogram_band_spinner);
        this.statusTextView = (TextView)view.findViewById(R.id.scan_status);
        this.consoleTextView = (TextView) view.findViewById(R.id.console_view);

        this.spectrogramSpinner.setOnItemSelectedListener(onBandSelectListener);
        this.consoleTextView.setMovementMethod(new ScrollingMovementMethod());

        spec850 = new SpectrogramData(Band.GSM850);
        spec900 = new SpectrogramData(Band.GSM900);
        spec1800 = new SpectrogramData(Band.DCS1800);
        spec1900 = new SpectrogramData(Band.PCS1900);

        this.activeSpectrogram = this.spec850;

        this.spectrogramChart.setScaleEnabled(false);
        this.spectrogramChart.setTouchEnabled(false);
        this.spectrogramChart.setDragEnabled(false);
        this.spectrogramChart.setScaleEnabled(false);
        this.spectrogramChart.setPinchZoom(false);

        this.spectrogramChart.getDescription().setEnabled(false);
        this.spectrogramChart.getLegend().setEnabled(false);   // Hide the legend

        XAxis xAxis = this.spectrogramChart.getXAxis();
        xAxis.setPosition(XAxisPosition.BOTTOM);
        xAxis.setTextSize(10f);
        xAxis.setTextColor(Color.BLACK);
        xAxis.setDrawAxisLine(true);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(10f);

        YAxis left = this.spectrogramChart.getAxisLeft();
        left.setDrawLabels(true); // no axis labels
        left.setDrawAxisLine(true);
        left.setDrawGridLines(false); // no grid lines
        left.setDrawZeroLine(false); // draw a zero line
        left.setValueFormatter(new SpectrogramYAxisFormatter());
        this.spectrogramChart.getAxisRight().setEnabled(false); // no right axis

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateSpectrumDisplay();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mContext = context;
        localBroadcastManager = LocalBroadcastManager.getInstance(mContext);

        // Setup the Intent receivers
        localBroadcastManager.registerReceiver(scanUpdateIntentReceiver,
                new IntentFilter(LoggingService.CELL_LOG_POWER_MEASUREMENT));
        localBroadcastManager.registerReceiver(phoneConsoleIntentReceiver,
                new IntentFilter(OsmoconService.OSMOCON_CONSOLE_DATA_RECEIVED));
        localBroadcastManager.registerReceiver(cellObservationIntentReceiver,
                new IntentFilter(LoggingService.CELL_LOG_CELL_OBSERVATION));
    }

    @Override
    public void onDetach() {
        super.onDetach();

        localBroadcastManager.unregisterReceiver(scanUpdateIntentReceiver);
        localBroadcastManager.unregisterReceiver(phoneConsoleIntentReceiver);
        localBroadcastManager.unregisterReceiver(cellObservationIntentReceiver);

        mContext = null;
    }

    private BroadcastReceiver scanUpdateIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            SpectrumMeasurement sm = new SpectrumMeasurement();
            sm.measurementHeader = new MeasurementHeader();
            Bundle extras = intent.getExtras();

            sm.measurementHeader.arfcn = extras.getShort(LoggingService.CELL_LOG_ARFCN);
            sm.measurementHeader.band = (Band) extras.get(LoggingService.CELL_LOG_BAND);
            sm.measurementHeader.dBm = extras.getByte(LoggingService.CELL_LOG_DBM);
            sm.measurementHeader.timestamp = extras.getLong(LoggingService.CELL_LOG_TIMESTAMP);

            addSpecMeasurement(sm);
            if (activeSpectrogram.dataNotSeen()){
                updateSpectrumDisplay();
                activeSpectrogram.setDataSeen();
            }
        }
    };

    private BroadcastReceiver phoneConsoleIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();
            String consoleTxt = extras.getString(OsmoconService.OSMOCON_CONSOLE_DATA_EXTRA);

            // If there isn't too much text then just append otherwise truncate
            // text in the view
            String tmpText = consoleTextView.getText().toString();

            if (tmpText.length() > MAX_CONSOLE_TEXT_LEN){
                tmpText = tmpText.substring(tmpText.length() - 500);
                consoleTextView.setText(tmpText + consoleTxt);
            }
            else{
                consoleTextView.append(consoleTxt);
            }

            String displayText = parseConsoleText(consoleTxt);
            if (!displayText.equals("")){
                statusTextView.setText(displayText);
            }
        }
    };

    private BroadcastReceiver cellObservationIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            statusTextView.setText("Cell Observed!");
        }
    };

    private void addSpecMeasurement(SpectrumMeasurement sm){
        Band band = sm.measurementHeader.band;

        if (band == Band.GSM850){
            spec850.setPowerMeas(sm);
        }
        else if (band == Band.GSM900){
            spec900.setPowerMeas(sm);
        }
        else if (band == Band.DCS1800){
            spec1800.setPowerMeas(sm);
        }
        else {
            spec1900.setPowerMeas(sm);
        }
    }

    private void updateSpectrumDisplay(){
        ArrayList<BarEntry> barEntry = new ArrayList<>();

        YAxis left = this.spectrogramChart.getAxisLeft();
        if (activeSpectrogram.wasDataAdded()){
            left.setDrawLabels(true); // no axis labels
        }
        else{
            left.setDrawLabels(false);
        }

        // Sets the axis values
        String[] xAxisNames = activeSpectrogram.getArfcns();
        XAxis xAxis = spectrogramChart.getXAxis();
        xAxis.setValueFormatter(new SpectrogramXAxisFormatter(xAxisNames));

        int[] rawPowerMeas = activeSpectrogram.getRawPowerMeas();

        for (int i = 0; i < rawPowerMeas.length; i++){
            barEntry.add(new BarEntry(i, rawPowerMeas[i] + 110));
        }

        BarDataSet dataSet = new BarDataSet(barEntry, "Projects");
        dataSet.setColor(Color.RED);
        dataSet.setBarShadowColor(Color.RED);
        dataSet.setBarBorderColor(Color.RED);
        dataSet.setBarBorderWidth(0);

        BarData data = new BarData(dataSet);
        this.spectrogramChart.setData(data);
        this.spectrogramChart.invalidate();
    }

    public class SpectrogramYAxisFormatter implements IAxisValueFormatter {

        public SpectrogramYAxisFormatter() {}

        @Override
        public String getFormattedValue(float value, AxisBase axis) {
            // "value" represents the position of the label on the axis (x or y)
            return String.valueOf((int) value - 110) + " dBm";
        }
    }

    public class SpectrogramXAxisFormatter implements IAxisValueFormatter {

        private String[] mValues;

        public SpectrogramXAxisFormatter(String[] values) {
            this.mValues = values;
        }

        @Override
        public String getFormattedValue(float value, AxisBase axis) {
            // "value" represents the position of the label on the axis (x or y)
            return mValues[(int) value];
        }
    }

    private Spinner.OnItemSelectedListener onBandSelectListener = new Spinner.OnItemSelectedListener() {
        public void onItemSelected(AdapterView<?> parent, View view,
                                   int pos, long id) {
            long bandNum = parent.getItemIdAtPosition(pos);

            if (bandNum == 0){
                activeSpectrogram = spec850;
            }
            else if (bandNum == 1){
                activeSpectrogram = spec900;
            }
            else if (bandNum == 2){
                activeSpectrogram = spec1800;
            }
            else{
                activeSpectrogram = spec1900;
            }

            updateSpectrumDisplay();
        }

        public void onNothingSelected(AdapterView<?> parent) {}
    };

    public String parseConsoleText(String rawText){
        String retTxt = "";

        // For now we are only parsing the syncing and power measurement strings
        if (rawText.contains("Starting FCCH RecognitionL1CTL_RESET_REQ: FULL!L1CTL_FBSB_REQ (arfcn=")){
            try {
                String bandStr;
                short tmpArfcn;
                short adjustedArfcn;

                tmpArfcn = (short) Integer.parseInt(rawText.substring(69).split(",")[0]);
                adjustedArfcn = Utils.getAdjustedARFCN(tmpArfcn);
                Band band = Utils.bandFromARFCN(adjustedArfcn, Utils.isPCS(tmpArfcn));

                bandStr = Band.bandToString(band);
                retTxt = "Attempting Sync to (" + bandStr + "," + adjustedArfcn + ")...";
            }
            catch (StringIndexOutOfBoundsException e){
                Log.w(TAG, "Index Out of bounds error in parsing raw console text");
            }
            catch (NumberFormatException e){
                Log.w(TAG, "Number format error parsing raw console text");
            }
        }
        else if (rawText.contains("PM MEAS:")){
            retTxt = "Gathering Power Measurements...";
        }

        return retTxt;
    }
}
