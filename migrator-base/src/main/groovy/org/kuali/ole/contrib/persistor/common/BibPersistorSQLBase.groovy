package org.kuali.ole.contrib.persistor.common
import groovy.sql.Sql
import groovy.util.logging.Slf4j
import groovyx.gpars.agent.Agent
import org.kuali.ole.contrib.ConfigLoader

import java.sql.SQLException

import static com.google.common.collect.Sets.newHashSet
/**
 * Base class for bib record persistors that interact directly with the OLE database.
 * Implementations will override doPersist(), init(), and join().
 * <p>
 *  Subclasses will maintain an internal cache,
 *
 * <p>
 *     Bib records have what is, overall, a fairly simple record format.
 * </p>
 * <table id="bib_record_format">
 *     <caption>Bib Record Format</caption>
 *     <thead>
 *         <tr>
 *             <th>Field Name</th>
 *             <th>Field Type</th>
 *             <th>Required?</th>
 *             <th>Notes</th>
 *         </tr>
 *    </thead>
 *    <tbody>
 *        <tr>
 <td><code>bib_id</code></td>
 <td>Integer</td>
 <td>Y</td>
 <td>Will also accept strings that can be converted to Integers.</td>
 </tr>
 <tr>
 <td><code>former_id</code></td>
 <td>String</td>
 <td>N</td>
 <td>identifier from older system (presumably a ILS previous to the one you are migrating from, unless
     you are migrating from an ILS that doesn't use integer-based identifiers</td>
 </tr>
 <tr>
 <td><code>fast_add</code></td>
 <td>Y/N (boolean char)</td>
 <td>N</td>
 <td>defaults to "N"</td>
 </tr>
 <tr>
 <td><code>staff_only</code></td>
 <td>Y/N (boolean char)</td>
 <td>N</td>
 <td>Defaults to "N"</td>
 </tr>
 <tr>
 <td><code>created_by</code></td>
 <td>String</td>
 <td>N</td>
 <td>username/identifier for person who created teh bib record.  Defaults to "olemigrator"</td>
 </tr>
 <tr>
 <td><code>date_created</code></td>
 <td>java.util.Date</td>
 <td>Y</td>
 <td>Timestamp for original record creation</td>
 </tr>
 <tr>
 <td><code>updated_by</code></td>
 <td>String</td>
 <td>N</td>
 <td>defaults to "olemigrator"</td>
 </tr>
 <tr>
 <td><code>date_updated</code></td>
 <td>java.util.Date</td>
 <td>N</td>
 <td>(default current time)</td>
 </tr>
 <tr>
 <td><code>status</code></td>
 <td>String</td>
 <td>N</td>
 <td>Current cataloguing status; defaults to "Catalogued"</td>
 </tr>
 <tr>
 <td><code>status_updated_date</code></td>
 <td>java.util.Date</td>
 <td>N</td>
 <td>defaults to current timestamp</td>
 </tr>
 <tr>
 <td><code>unique_id_prefix</code></td>
 <td>String</td>
 <td>N</td>
 <td>forced to "wbm" (work/bibliographic/MARC)</td>
 </tr>
 <tr>
 <td><code>content</code></td>
 <td>String (CLOB)</td>
 <td>Y</td>
 <td>MARCXML content of record (including outer "collection" element)</td>
 </tr>
 </tbody>
 </table>
**/
@Slf4j
abstract class BibPersistorSQLBase {
    public static final int DEFAULT_COMMIT_THRESHOLD = 500;

    protected Sql oleSql


    protected RecordCache _cache

    protected ConfigLoader configLoader

    protected ConfigObject config

    protected Map listeners = [commit: newHashSet(), shutdown: newHashSet(), error: newHashSet()]


    private Agent<BitSet> seen = new Agent(new BitSet())

    /**
     * list to filter out any extraneous fields in record.
     */
    protected final List<String> insert_fields = [
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
    ].asImmutable()

    /**
     * Default values for field; sites can update any of these values if they like
     */
    protected final Map<String, ?> field_defaults = [
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

    /**
     * Constructor.
     * @param configLoader configuration loader for access to datasources and configuration.
     * @param autoInit <code>true</code> to call <code>init()</code> in constructor, <code>false</code>
     * if you want to do this manually.
     * @see #init()
     */
    public BibPersistorSQLBase(ConfigLoader configLoader, boolean autoInit = true) {
        this.configLoader = configLoader
        this._cache = new RecordCache(DEFAULT_COMMIT_THRESHOLD, true)
        if (autoInit) {
            init()
        }
    }

    /**
     * Persists current contents of cache to the output.
     * @return
     */
    public def persistCache() {
        doPersist(_cache.readCache())
    }

    /**
     * Add a single record.
     * @param recordMap
     * @return
     */
    def leftShift(Map<String,?> recordMap) {
        recordMap.fast_add = "N"
        recordMap.unique_id_prefix = 'wbm'
        recordMap.former_id = recordMap.bib_id
        recordMap.status = 'Catalogued'


        if (('shadow' in recordMap) && !('staff_only' in recordMap)) {
            recordMap['staff_only'] = recordMap['shadow'] ? 'Y' : 'N'
        }

        field_defaults.each { k, v ->
            if (!(k in recordMap)) {
                recordMap[k] = v
            }
        }

        if (_cache == null) {
            try {
                bibTable.add(recordMap.subMap(insert_fields))
            } catch (SQLException sqx) {
                log.warn("Unable to insert ${recordMap.id}", sqx)
            }
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
    }

    /**
     * Adds multiple records to the cache.
     * @param records
     * @return
     */
    def leftShift(List<Map<String,?>> records) {
        records.each { Map<String,?> rec -> leftShift(rec) }
    }

    /**
     * Writes a batch of records to the output.
     * @param records
     */
    protected abstract void doPersist(List<Map<String, ?>> records);


    void fireCommitEvent(event) {
        listeners.commit.each { it(this, event) }
    }

    void addCommitListener(listener) {
        if (listener.respondsTo("call")) {
            listeners.commit << listener
        } else {
            throw new IllegalArgumentException("Commit listeners must be callable.")
        }
    }

    void addShutdownListener(Closure listener) {
        listeners.shutdown << listener
    }

    def init() {
        config = configLoader.loadConfig()
        oleSql = new Sql(configLoader.getDataSource("ole"))
        mapper = new TableAnalyzer(sql: oleSql).getLoaderColumnMapper("ole_ds_bib_t")
    }

    /**
     * Shuts down this persistor.
     */
    public abstract void join();


}
