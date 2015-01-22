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
package org.kuali.ole.contrib.persistor.jdbc

/**
 * This class currently does nothing, it is used to hold direct JDBC persisting logic from an earlier implementation.
 */
class HoldingsPersistorStrategy {

    def holdings


    def persist = {
        holdings.groupBy { it.keySet() }.each {

                   Set<String> columnKeys, List<Map> records ->
                       oleSql.withTransaction {
                           oleSql.withBatch(batchSize) {
                               records.each {
                                   Map rec ->
                                       holdingsTable.add(rec)
                               }
                           }
                       }
               }

               // ditto for items
               items.groupBy { it.keySet() }.each {
                   Set<String> keys, List<Map> records ->
                       oleSql.withTransaction {
                           oleSql.withBatch(batchSize) {
                               records.each { Map row -> itemTable.add(row) }
                           }
                       }
               }


               try {
                   oleSql.withTransaction { tx ->
                       uris.each { Map row ->
                           uriTable.add(row)
                       }
                   }
               } catch (Exception e) {
                   e.printStackTrace(System.err)
                   System.exit(8)
               }
               // TODO add extentOfOwnership
    }
}
