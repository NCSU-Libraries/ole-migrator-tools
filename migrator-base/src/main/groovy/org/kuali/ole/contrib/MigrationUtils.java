/*
     Copyright (C) 2015 North Carolina State University

     This program is free software: you can redistribute it and/or modify
     it under the terms of the GNU General Public License as published by
     the Free Software Foundation, either version 3 of the License, or
     (at your option) any later version.

     This program is distributed in the hope that it will be useful,
     but WITHOUT ANY WARRANTY; without even the implied warranty of
     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
     GNU General Public License for more details.

     You should have received a copy of the GNU General Public License
     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.kuali.ole.contrib;

import java.util.UUID;

/**
 * Utility class for methods used in migration routines.
 */
public class MigrationUtils {


    /**
     * Creates a new UUID, as many Rice-based objects (especially in maintenance tables)
     * require them.
     * @return a new random UUID.
     */
    public static String getUUID() {
        return UUID.randomUUID().toString();
    }
}
