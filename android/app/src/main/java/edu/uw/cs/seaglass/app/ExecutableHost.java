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

package edu.uw.cs.seaglass.app;

import android.os.Process;
import android.util.Log;

import com.google.ase.Exec;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class ExecutableHost extends Thread {
    private FileDescriptor fd;
    private int pid;
    private String tag;

    public ExecutableHost(String tag, String[] cmdArgs, String[] envVars) {
        this.tag = tag;
        int[] pids = new int[1];
        fd = Exec.createSubprocess(cmdArgs, envVars, pids);
        pid = pids[0];
    }

    public void run() {
        BufferedReader stdoutReader =
                new BufferedReader(new InputStreamReader(new FileInputStream(fd)));

        for (;;) {
            try {
                String inStr = stdoutReader.readLine();
                if (inStr == null) {
                    Exec.waitFor(pid);
                    return;
                }
                Log.d(tag, inStr);
            } catch (IOException e) {
                Log.d(tag, "Got IOException reading stdout -- reaping process");
                Exec.waitFor(pid);
                return;
            }
        }
    }

    public void killProcess() {
        Process.killProcess(pid);
    }
}
