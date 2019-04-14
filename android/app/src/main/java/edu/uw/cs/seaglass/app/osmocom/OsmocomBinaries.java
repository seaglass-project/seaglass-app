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

package edu.uw.cs.seaglass.app.osmocom;

import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;

import edu.uw.cs.seaglass.app.ExecutableHost;
import edu.uw.cs.seaglass.app.Options;
import edu.uw.cs.seaglass.app.Utils;
import edu.uw.cs.seaglass.app.db.Band;

public class OsmocomBinaries extends Thread {
    private ExecutableHost osmoconHost;
    private ExecutableHost cellLogHost;
    private File filesDir;
    private File libsDir;
    private Options options;

    private static final String TAG = Utils.TAG_PREFIX + "OsmocomBinaries";

    public OsmocomBinaries(File filesDir, File libsDir, Options options) {
        this.filesDir = filesDir;
        this.libsDir = libsDir;
        this.options = options;
    }

    public void run() {
        // Need to set the correct permissions so that we can talk to the USB device
        //Utils.sudo("setenforce 0");
        //Utils.sudo("chmod 666 /dev/ttyUSB0");

        /*
        Log.d(TAG, "Starting osmocon...");
        osmoconHost = new ExecutableHost(Utils.TAG_PREFIX + "osmocon", new String[]{
                new File(filesDir, "osmocon").getAbsolutePath(),
                "-v",
                "-s", new File(filesDir, "osmocom_l2").getAbsolutePath(),
                "-l", new File(filesDir, "osmocom_loader").getAbsolutePath(),
                "-p", "/dev/ttyUSB0",
                "-m", "c140xor",
                "-c", new File(filesDir, "layer1.highram.bin").getAbsolutePath()
        }, new String[] {
                // FIXME: Make this programmatic
                "LD_LIBRARY_PATH=/data/data/edu.uw.cs.seaglass.app.ui/lib"
        });

        osmoconHost.start();
        Log.d(TAG, "Launched osmocon");

        try{
            Thread.sleep(1000);
        } catch(InterruptedException e){}
        */
        runCellLog();
    }

    private void runCellLog() {
        ArrayList<String> arfcnList = new ArrayList<>();
        if (options.getBandEnabled(Band.GSM900)) {
            arfcnList.add("1-124");
        }
        if (options.getBandEnabled(Band.GSM850)) {
            arfcnList.add("128-251");
        }
        if (options.getBandEnabled(Band.DCS1800)) {
            arfcnList.add("512-885");
        }
        if (options.getBandEnabled(Band.PCS1900)) {
            arfcnList.add("33280-33578");
        }
        if (arfcnList.size() == 0) {
            return;
        }

        ArrayList<String> cmdArgs = new ArrayList<>();
        cmdArgs.add(new File(filesDir, "cell_log").getAbsolutePath());
        cmdArgs.add("-s");
        cmdArgs.add(":ABSTRACT:osmocom_l2");
        cmdArgs.add("-i");
        cmdArgs.add("127.0.0.1");
        cmdArgs.add("-l");
        cmdArgs.add(new File(filesDir, "cell_log_fifo").getAbsolutePath());
        cmdArgs.add("-A");
        cmdArgs.add(TextUtils.join(",", arfcnList));
        if (!options.getTransmitEnabled()) {
            cmdArgs.add("-n");
        }

        StringBuilder ldLibraryPathBuilder = new StringBuilder();
        ldLibraryPathBuilder.append("LD_LIBRARY_PATH=");
        ldLibraryPathBuilder.append(libsDir.getAbsolutePath());

        String[] env = new String[] {
                ldLibraryPathBuilder.toString()
        };

        Log.d(TAG, "Starting cell_log...");
        Log.d(TAG, "Args is " + TextUtils.join(" ", cmdArgs.toArray(new String[cmdArgs.size()])));
        Log.d(TAG, "Environment is " + ldLibraryPathBuilder);

        cellLogHost = new ExecutableHost(Utils.TAG_PREFIX + "cell_log",
                cmdArgs.toArray(new String[cmdArgs.size()]), env);

        cellLogHost.start();
        Log.d(TAG, "Launched cell_log");
    }

    public void close(){
        if (osmoconHost != null){
            osmoconHost.killProcess();
            Log.d(TAG, "Killed osmocon");
        }
        if (cellLogHost != null){
            cellLogHost.killProcess();
            Log.d(TAG, "Killed cell_log");
        }
    }

    public void restartCellLog() {
        Log.d(TAG, "Restarting cell_log");
        if (cellLogHost != null) {
            cellLogHost.killProcess();
        }
        runCellLog();
    }
}
