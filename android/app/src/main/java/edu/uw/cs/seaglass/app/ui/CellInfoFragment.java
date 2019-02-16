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


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import edu.uw.cs.seaglass.app.db.Band;
import edu.uw.cs.seaglass.app.logging.CellInfo;
import edu.uw.cs.seaglass.app.logging.LoggingService;

import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link CellInfoFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class CellInfoFragment extends Fragment {
    private Context mContext;

    private TextView cellInfoTextView;

    public CellInfoFragment() {
        // Required empty public constructor
    }

    public static CellInfoFragment newInstance() {
        CellInfoFragment fragment = new CellInfoFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_cell_info, container, false);
        cellInfoTextView = (TextView) view.findViewById(R.id.cell_info_text_view);
        cellInfoTextView.setMovementMethod(new ScrollingMovementMethod());

        return view;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mContext = context;

        LocalBroadcastManager.getInstance(mContext).registerReceiver(
                cellObservationIntentReceiver,
                new IntentFilter(LoggingService.CELL_LOG_CELL_OBSERVATION));
    }

    @Override
    public void onDetach() {
        super.onDetach();

        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(cellObservationIntentReceiver);

        mContext = null;
    }

    private BroadcastReceiver cellObservationIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            CellInfo ci;
            Bundle extras = intent.getExtras();
            String displayString = "";

            ci = (CellInfo) extras.getSerializable(LoggingService.CELL_LOG_CELL_INFO);

            displayString += "MCC: " + ci.getMCC() + " MNC: " + ci.getMNC();
            displayString += "\n";
            displayString += "LAC: " + ci.getLAC() + " Cell ID: " + ci.getCellId();
            displayString += "\n";
            displayString += "Channel: (" + Band.bandToString(ci.getBand()) + "," + ci.getArfcn() + ")";
            displayString += "\n";
            displayString += "Power: " + ci.getdBm() + " dBm";
            displayString += "\n\n";

            String startText = getResources().getString(R.string.cell_info_start_text);
            if (cellInfoTextView.getText().toString().equals(startText)){
                cellInfoTextView.setText("");
            }
            cellInfoTextView.append(displayString);
        }
    };
}
