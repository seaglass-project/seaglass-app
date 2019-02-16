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

package edu.uw.cs.seaglass.app.logging;

import android.util.Log;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import edu.uw.cs.seaglass.app.Utils;
import edu.uw.cs.seaglass.app.db.CellObservation;
import edu.uw.cs.seaglass.app.db.MeasurementHeader;
import edu.uw.cs.seaglass.app.db.SpectrumMeasurement;

public class CellLogProvider extends Thread {
    private enum LogSection {
        NONE,
        POWER,
        SYSINFO
    }

    private LogSection currentSection = LogSection.NONE;
    private String logName;
    private static final String TAG = Utils.TAG_PREFIX + "CellLogProvider";

    private MeasurementHeader measurementHeader;
    private CellObservation cellObservation;
    private CellObservationReceiver cellObservationReceiver;
    private SpectrumMeasurement spectrumMeasurement;
    private SpectrumMeasurementReceiver spectrumMeasurementReceiver;
    private BufferedReader log;

    public CellLogProvider(String logName,
                           CellObservationReceiver cellObservationReceiver,
                           SpectrumMeasurementReceiver spectrumMeasurementReceiver)
            throws IOException {
        this.logName = logName;
        this.cellObservationReceiver = cellObservationReceiver;
        this.spectrumMeasurementReceiver = spectrumMeasurementReceiver;
    }

    public void run() {
        // Loop until we open the fifo
        for (;;) {
            try {
                log = new BufferedReader(new FileReader(logName));
                break;
            } catch (FileNotFoundException e) {
                //Log.e(TAG, "Log file not ready yet...", e);
            }
        }

        for (;;) {
            if (Thread.interrupted()){
                Log.d(TAG, "CellLogProvider interrupted");
                break;
            }

            try {
                String line = log.readLine();
                long lineTimestamp = System.currentTimeMillis();

                if (line == null) {
                    break;
                } else if (line.equals("[power]")) {
                    currentSection = LogSection.POWER;
                    measurementHeader = new MeasurementHeader();
                    spectrumMeasurement = new SpectrumMeasurement();
                    spectrumMeasurement.measurementHeader = measurementHeader;
                } else if (line.equals("[sysinfo]")) {
                    currentSection = LogSection.SYSINFO;
                    measurementHeader = new MeasurementHeader();
                    cellObservation = new CellObservation();

                    // Need to initialize the TA (since sometimes it isn't specified)
                    cellObservation.ta = CellObservation.NO_TA;
                    cellObservation.measurementHeader = measurementHeader;
                } else if (line.equals("") && currentSection == LogSection.SYSINFO) {
                    cellObservationReceiver.onCellObservationReceived(cellObservation);
                } else {
                    String[] tokens = line.split(" ");
                    if (tokens[0].equals("time")) {
                        measurementHeader.timestamp = Long.parseLong(tokens[1] + "000");
                    } else if (tokens[0].equals("arfcn")) {
                        measurementHeader.arfcn = (short)Integer.parseInt(tokens[1]);
                        measurementHeader.band = Utils.bandFromARFCN(measurementHeader.arfcn,
                                Utils.isPCS(measurementHeader.arfcn));
                        measurementHeader.arfcn = (short) (measurementHeader.arfcn & 0x3fff);
                        measurementHeader.timestamp = lineTimestamp;
                        if (currentSection == LogSection.POWER) {
                            for (int i = 2; i < tokens.length; i++) {
                                measurementHeader.dBm = Byte.parseByte(tokens[i]);
                                spectrumMeasurementReceiver.
                                        onSpectrumMeasurementReceived(spectrumMeasurement);
                                measurementHeader.arfcn++;
                            }
                        }
                    } else if (tokens[0].equals("rxlev")) {
                        measurementHeader.dBm = Byte.parseByte(tokens[1]);
                    } else if (tokens[0].equals("ta")) {
                        cellObservation.ta = Byte.parseByte(tokens[1]);
                    } else if (tokens[0].equals("bsic")) {
                        cellObservation.bsic = (byte)(((tokens[1].charAt(0) - 0x30) << 4) |
                                (tokens[1].charAt(2) - 0x30));
                    } else if (tokens[0].startsWith("si")) {
                        byte[] payload = new byte[tokens.length - 1];
                        for (int i = 0; i < payload.length; i++) {
                            // Java sucks and doesn't have unsigned bytes so this is a dumb hack
                            payload[i] = (byte)Short.parseShort(tokens[i + 1], 16);
                        }
                        if (tokens[0].equals("si1")) {
                            cellObservation.si1 = payload;
                        } else if (tokens[0].equals("si2")) {
                            cellObservation.si2 = payload;
                        } else if (tokens[0].equals("si2quater")) {
                            cellObservation.si2quat = payload;
                        } else if (tokens[0].equals("si3")) {
                            cellObservation.si3 = payload;
                        } else if (tokens[0].equals("si4")) {
                            cellObservation.si4 = payload;
                        } else if (tokens[0].equals("si13")) {
                            cellObservation.si13 = payload;
                        }

                    }
                }
                Log.d(TAG, line);
            } catch (IOException e) {
                Log.e(TAG, "Got IOException reading cell log", e);
                break;
            }
        }

        close();
        return;
    }

    private void close(){
        try {
            log.close();
        } catch (IOException e){}
    }
}
