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

import androidx.room.TypeConverter;

import static edu.uw.cs.seaglass.app.db.Band.*;

public class Converters {
    @TypeConverter
    public static Band fromBandInt(int b) {
        if (b == GSM900.getId()) {
            return GSM900;
        } else if (b == GSM850.getId()) {
            return GSM850;
        } else if (b == DCS1800.getId()) {
            return DCS1800;
        } else if (b == PCS1900.getId()) {
            return PCS1900;
        } else {
            throw new IllegalArgumentException("Unknown band id");
        }
    }

    @TypeConverter
    public static Integer toInteger(Band b) {
        return b.getId();
    }
}
