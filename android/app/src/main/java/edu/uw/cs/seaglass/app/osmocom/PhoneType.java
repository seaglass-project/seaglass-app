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

import android.util.SparseArray;

public enum PhoneType {
    UNKNOWN(0),
    C123(1),
    C123xor(2),
    C140(3),
    C140xor(4),
    C155(5);

    private int value;
    private static SparseArray<PhoneType> map = new SparseArray<>();

    PhoneType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    static {
        for (PhoneType pt : PhoneType.values()) {
            map.put(pt.value, pt);
        }
    }
    public static PhoneType valueOf(int i) {
        return map.get(i);
    }
}
