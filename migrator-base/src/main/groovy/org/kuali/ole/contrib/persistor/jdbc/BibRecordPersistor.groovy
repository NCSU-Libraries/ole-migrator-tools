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


import groovy.sql.BatchingPreparedStatementWrapper
import groovy.sql.DataSet
import org.kuali.ole.contrib.ConfigLoader
import org.kuali.ole.contrib.persistor.common.BibPersistorSQLBase

/**
 * Persistor implementation that saves bib records to the <code>ole_ds_bib_t</code> table via direct (batched)
 * JDBC calls.  Untested in this form.
 */
class BibRecordPersistor extends BibPersistorSQLBase {

     // MySQL syntax for"upsert"

    def baseInsertSQL = """
            INSERT INTO ole_ds_bib_t
                (
                    bib_id,
                    former_id,
                    fast_add,
                    staff_only,
                    created_by,
                    date_created,
                    updated_by,
                    date_updated,
                    status,
                    status_updated_by,
                    status_updated_date,
                    unique_id_prefix,
                    content
                 )
                 VALUES(
                    ?, -- bib_id
                    ?, -- former_id
                    ?, -- fast_add
                    ?, -- staff_only
                    ?, -- created_by
                    ?, -- date_created
                    ?, -- updated_by,
                    ?, -- date_updated,
                    ?, -- status
                    ?, -- status_updated_by
                    ?, -- status_updated_date
                    ?, -- unique_id_prefix
                    ? -- content
                  )"""

    // leaves room for adding other databases e.g. Oracle, later.
    def inserts = [ 'mysql' : baseInsertSQL + "ON DUPLICATE KEY update former_id = ''" ]

    def insertSQL

    DataSet bibTable


    public BibRecordPersistor(ConfigLoader loader, boolean autoInit =true) {
        super(loader,autoInit)
    }


    @Override
    protected void doPersist(List<Map<String, ?>> records) {
        def result = []
        def insertRecords = records.collect { it.subMap(insert_fields) }
        oleSql.withTransaction {
            result << oleSql.withBatch(50, insertSQL) {
                BatchingPreparedStatementWrapper ps ->
                    records.each {
                        Map<String, ?>rec ->
                           def row = rec.subMap(insert_fields)
                                bibTable.add(row)
                    }
            }
        }
        fireCommitEvent([ source: this, records: records ])
    }

    @Override
    def init() {
        super.init()
        bibTable = oleSql.dataSet("ole_ds_bib_t")
        if ( config.ole.driverName.contains("mysql") || config.ole.driverName.contains("mariadb" ) ) {
            insertSQL = inserts.mysql
        } else {
            throw new UnsupportedOperationException("Currently, JDBC BibRecordPersistor only supports MySQL")
        }
    }

    @Override
    public void join() {
        log.info("Shutting down; persisting all records in cache")
        persistCache()
        log.info("Done.")
        bibTable.close()
        oleSql.close()
        fireShutdownEvent("")
        log.info("Shut down")
    }
}
