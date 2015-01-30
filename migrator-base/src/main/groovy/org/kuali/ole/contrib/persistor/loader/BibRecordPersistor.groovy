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
package org.kuali.ole.contrib.persistor.loader
import groovy.util.logging.Slf4j
import groovyx.gpars.agent.Agent
import org.kuali.ole.contrib.persistor.common.AgentWriter
import org.kuali.ole.contrib.ConfigLoader
import org.kuali.ole.contrib.persistor.common.TableAnalyzer
import org.kuali.ole.contrib.persistor.common.BibPersistorSQLBase
/**
 * Persistor implementation that outputs MySQL loader files for the <code>ole_ds_bib_t</code> table.
 * <p>
    See the sample driver file for usage ideas.
 * </p>
 * @see BibPersistorSQLBase for information on expected form of records.
 */
@Slf4j
class BibRecordPersistor extends BibPersistorSQLBase {

    private File outputFile

    private Writer outputWriter

    private Agent<BitSet> seen = new Agent(new BitSet())

    private List<String> insert_fields = [
            "bib_id",
            "former_id",
            "fast_add",
            "staff_only",
            "created_by",
            "date_created",
            "updated_by",
            "date_updated",
            "status_updated_by",
            "status",
            "status_updated_date",
            "unique_id_prefix",
            "content"
    ]

    private final Map<String, ?> field_defaults = [
            former_id          : '',
            fast_add           : 'N',
            created_by         : 'olemigrator',
            updated_by         : 'olemigrator',
            unique_id_prefix   : 'wbm',
            date_updated       : new Date(),
            status_updated_by  : 'olemigrator',
            status             : 'Catalogued',
            status_updated_date: new Date()
    ]

    private Closure mapper

    private String loaderQuery

    public BibRecordPersistor(ConfigLoader configLoader, boolean autoInit=true) {
        super(configLoader,autoInit)
    }

    /**
     * Adds a new record to the cache.
     * @param recordMap a map in the form described above.
     **/
    def leftShift(Map<String,?> recordMap) {
        log.trace "Entering leftShift(single)"
        recordMap.fast_add = "N"
        recordMap.unique_id_prefix = 'wbm'
        recordMap.former_id = recordMap.bib_id
        recordMap.status = 'Catalogued'
        // coerce bib_id to Integer 
        if ( recordMap.bib_id instanceof String ) {
            recordMap.bib_id = Integer.valueOf(recordMap.bib_id, 10)
        }
        assert recordMap.bib_id instanceof Integer, "${bib_id} is not numeric (${bib_id.class.name})"

        if (('shadow' in recordMap) && !('staff_only' in recordMap)) {
            recordMap['staff_only'] = recordMap['shadow'] ? 'Y' : 'N'
        }

        field_defaults.each { k, v ->
            if (!(k in recordMap)) {
                recordMap[k] = v
            }
        }

        if (_cache == null) {
            doPersist([ recordMap ])
        } else {
            boolean submitted = _cache.offer(recordMap)
            if (!submitted) {
                log.warn "queue rejected record, doing an 'early' clear of the cache"
            } else {
                log.trace("added ${recordMap.bib_id}")
            }
            if (_cache.atCapacity || !submitted) {
                log.trace("Hit commit interval")
                persistCache()
                log.trace("Done.")
            }

            if (!submitted) {
                log.warn("Resumbitting record ${recordMap.bib_id}")
                _cache << recordMap
                log.warn("Record ${recordMap.bib_id} submitted")
            }

            log.trace("exiting leftShift")
        }
        recordMap

    }

    /**
     * Writes the currently cached bib records to the OLE database.
     * @param records
     */
    @Override
    void doPersist(List<Map<String, ?>> records) {
        def insertRecords = records.collect { it.subMap(insert_fields) }
        insertRecords.each {
           Map rec ->
               int bibId = rec.bib_id
               if ( seen.val.get(bibId) ) {
                    log.warn "Duplicate bib id ${bibId}"
                    return
                }
                seen.val.set(bibId)
                def mapped=mapper(rec)
                def values = mapped.values()

               // omit content from trace
               if ( log.traceEnabled ) {
                   log.trace "bib record: {}", values.asList()[0..-2]
               }
               def line = values.join('$$$$') + "--\n--"
               outputWriter.write(line.toCharArray(), 0, line.length())
        }
        fireCommitEvent([ source: this, records: insertRecords ])
    }

    @Override
    def init() {
        super.init()
        mapper  = new TableAnalyzer(sql:oleSql).getLoaderColumnMapper("ole_ds_bib_t")
        def outputDir = new File( config.persistor.loader.outputDir ?: "/tmp")
        outputFile = new File(outputDir, "ole-bibs.txt")
        outputFile.delete()
        outputWriter = new AgentWriter(outputFile.newWriter("utf-8"))
        loaderQuery = """LOAD DATA INFILE '${ outputFile.name }'
                        INTO TABLE ole.ole_ds_bib_t
                        CHARACTER SET utf8mb4
                        FIELDS TERMINATED BY '\$\$\$\$'
                        OPTIONALLY ENCLOSED BY '"'
                        LINES TERMINATED BY '--\\n--'
                    """
    }

    @Override
    public void join() {
        log.info "Shutting down"
        persistCache()
        oleSql.close()
        outputWriter.close()
        listeners.shutdown.each { listener ->
            listener([ source: this, data: [  [ table: "ole_ds_bib_t", file: outputFile, loaderQuery: loaderQuery ] ] ])
        }
        log.info "Shutdown complete."
    }
}
