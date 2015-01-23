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

import groovy.sql.Sql
import groovy.util.logging.Slf4j
import org.kuali.ole.contrib.persistor.common.AgentWriter
import org.kuali.ole.contrib.ConfigLoader
import org.kuali.ole.contrib.persistor.common.RecordCache
import org.kuali.ole.contrib.persistor.common.TableAnalyzer

import java.util.concurrent.atomic.AtomicLong

import static com.google.common.collect.Sets.newHashSet

/**
 * Persistor implementation that outputs holdings and all associated child objects into MySQL loader files.
 * <p>
 *     Tables this populates:
 *     <ul>
 *      <li><code>ole_ds_holdings_t</code> (holdings info)</li>
 *      <li><code>ole_ds_holdings_stat_search_t</code> (statistical search codes for holdings)</li>
 *      <li><code>ole_ds_item_t</code> (print items)</li>
 *      <li><code>ole_ds_item_stat_search_t</code> (statistica search codes for items)</li>
 *      <li><code>ole_ds_holdings_uri_t</code> (URIs for electronic holdings and MARC 856)</li>
 *      <li><code>ole_ds_ext_ownership_t</code> (MFHD 866-868, roughly)</li>
 *      <li><code>ole_ds_ext_ownership_note_t</code> (866-8$x and $z)</li>
 *     </ul>
 *  </p>
 *  <p>
 *      General usage is instantiating this class with a ConfigLoader, and add records generated however you'd like using the
 *      <code>&lt;&lt;</code> operator.  The primary work of this class is managing the creation of database IDs for new records and
 *      the mappings of various referential data codes (e.g. for item locations) to their OLE database IDs.
 *  </p>
 *  <p>
 *      This object accesses the <code>ole</code> data source you have configured, and maintains an internal record cache, which
 *      can be flushed to the output by either manually invoking <code>doPersist()</code> or by calling its <code>commitListener</code>
 *      member closure.  This allows setting up a driver that will "autocommit" every N records, for example, which is the recommended setup.
 *  <h3>Record formats</h3>
 *  <table>
 *      <caption>Holdings Record Format</caption>
 *      <thead>
 *          <tr>
 *              <th>Field name</th>
 *              <th>Field type</th>
 *              <th>Required</th>
 *              <th>Note</th>
 *          </tr>
 *       </thead>
 *       <tbody>
 *           <tr>
 *               <td><code>bib_id</code></td>
 *               <td>Integer</td>
 *               <td>Y</td>
 *               <td>Primary key of associated bib record.</td>
 *          </tr>
 *          <tr>
 *              <td><code>holdings_id</td>
 *              <td>Integer</td>
 *              <td>N</td>
 *              <td>Will be automatically assigned if not provided.</td>
 *          </tr>
 *          <tr>
 *          <td><code>former_holdings_id</code></td>
 * <td>String</td>
 * <td>N</td>
 * <td>(possibly synthetic) ID the holdings record had in your old ILS</td>
 * </tr>
 * <tr>
 <td><code>holdings_type</code></td>
 <td>String enum: "print", "electronic"</td>
 <td>N</td>
 <td>Type of the holdings; print holdings should have items, electronic ones should not</td>
 </tr>
 <tr>
 <td><code>staff_only</code></td>
 <td>Y/N</td>
 <td>N</td>
 <td>Defaults to 'N'</td>
 </tr>
 <tr>
 <td><code>date_created</code></td>
 <td>java.util.Date</td>
 <td>Y</td>
 <td>Date record was created</td>
 </tr>
 <tr>
 <td><code>unique_id_prefix</code></td>
 <td>String</td>
 <td>N</td>
 <td>defaults to "who" (work/holdings/oleml)</td>
 </tr>
 <tr>
 <td><code>call_number</code></td>
 <td>String</td>
 <td>N</td>
 <td>"Raw" call number</td>
 </tr>
 <tr>
 <td><code>call_number_type_id</code></td>
 <td>In</td>
 <td>N*</td>
 <td>Required if call number is set. Value is ID in OLE database (ole_cat_shlvg_schm_t) -- common values here would be
  2 for LCC, 3 for Dewey, 5 for SUDOC.  </td>
 </tr>
 <tr>
  <td><code>shelving_order</code></td>
  <td>String</td>
  <td>N</td>
  <td>"padded" call number (for browsing, when available for the classification
  scheme)</td>
  </tr>
 <tr>
 <td><code>location_id</code></td>
 <td>Integer</td>
 <td>N</td>
 <td>Will be computed from the location_code if not set</td>
 </tr>
 <tr>
 <td><code>location_code</code></td>
 <td>String</td>
 <td>N*</td>
 <td>Required if location_id is not set; will be written into "location" field</td>
 </tr>
 <tr>
 <td><code>location_level</code></td>
 <td>String</td>
 <td>N</td>
 <td>Code for the location level in ole_locn_lvl_t.  Defaults to "SHELVING"</td>
 </tr>
 <tr>
 <td><code>items</code></td>
 <td>List(Item)</td>
 <td>N</td>
 <td>Only for "print" holdings type.  See <a href="#item_record_format">Item description</a> for format of items.</td>
 </tr>
 <tr>
 <td><code>uri</code></td>
 <td>List<(Map; 'uri' and 'text' keys, both String types)></td>
 <td>N</td>
 <td>Values here go into ole_ds_holdings_uri_t table.</td>
 </tr>
 <tr>
 <td><code>ext_ownership</code></td>
 <td>List<External Ownership></td>
 <td>N</td>
 <td>Data from MFHD 866-8 about extent of ownership.  Each record in the list here should have the following structure:
    <ul>
      <li><code>>type</code>: (string enum: "location", "bbu", "supplementary_material", "indexes")</td>
      <li><code>ord</code>: integer (display order)</li>
      <li><code>text</code> : String ($a of the relevant tag)</li>
      <li><code>notes</code>: a set of maps with keys <code>note</code> and <code>type</code>, where <code>type</code> is
 either "public" or "private", corresponding to subfields $x and $z of the MFHD field, respectively.</li>
 </ul>
 </td>
 </tr>

 </table>

 <p>Note that "holdings" support a lot more parameters (which you can see by looking at the fields of the
 <code>ole_ds_holdings_t</code> table.  Direct support for these other values has no current special support, but
 the framework will persist them if they are supplied.
 </p>

 <p>In general use in OLE, items <em>inherit</em> same-named values from their parent holdings, so these values
 will often not need to be set.  Use cases for items with different location include reserve copies.
 </p>

 <table id="item_record_format">
  <caption>Item Record format</caption>
 <thead>
  *          <tr>
  *              <th>Field name</th>
  *              <th>Field type</th>
  *              <th>Required</th>
  *              <th>Note</th>
  *          </tr>
  *       </thead>
 *       <tbody>
 *       <tr>
 <td><code>holdings_id</code></td>
 <td>Integer</td>
 <td>N</td>
 <td>Automatically calculated as the ID of the containing holdings object if not supplied.</td>
 </tr>
 <tr>
 <td><code>item_id</code></td>
 <td>Integer</td>
 <td>N</td>
 <td>Automatically generated if not supplied.</td>
 </tr>
 <tr>
 <td><code>location</code></td>
 <td>String</td>
 <td>N</td>
 <td>Inherited from holdings if not present.  Same use and meaning as property from parent.</td>
 </tr>
 <tr>
 <td><code>location_id</code></td>
 <td></td>
 <td>N</td>
 <td>Inherited from holdings if not present.  Same use and meaning as property from parent.</td>
 </tr>
 <tr>
  <td><code>location_code</code></td>
  <td>String</td>
  <td>N</td>
  <td>See above</td>
  </tr>
 <tr>
 <td><code>location_level</code></td>
 <td>String</td>
 <td>N</td>
 <td>See above.</td>
 </tr>
 <tr>
 <td><code>barcode</code></td>
 <td>String</td>
 <td>N</td>
 <td>Scannable barcode on print item</td>
 </tr>
 <tr>
 <td><code>barcode_arsl</code></td>
 <td>String</td>
 <td>N</td>
 <td>Barcode used in some high-density storage systems</td>
 </tr>
 <tr>
 <td><code>copy_number</code></td>
 <td>String(20)</td>
 <td>N</td>
 <td>copy number (may have form such as "c.1")</td>
 </tr>
 <tr>
 <td><code>fast_add</code></td>
 <td>Y/N</td>
 <td>N</td>
 <td>Defaults to 'N'</td>
 </tr>
 <tr>
 <td><code>staff_only</code></td>
 <td>Y/N</td>
 <td>Y</td>
 <td>Whether the item should be hidden from display.</td>
 </tr>
 <tr>
 <td><code>item_type_code</code></td>
 <td>String</td>
 <td>N</td>
 <td>The *code* from ole_itm_typ_t for the item's type</td>
 </tr>
 <tr>
 <td><code>num_pieces</code></td>
 <td>String</td>
 <td>N</td>
 <td>Number of pieces.</td>
 </tr>
 <tr>
 <td><code>price</code></td>
 <td>String</td>
 <td>N</td>
 <td></td>
 </tr>
 <tr>
 <td><code>date_created</code></td>
 <td>java.util.Date</td>
 <td>N</td>
 <td></td>
 </tr>
 <tr>
 <td><code>date_updated</code></td>
 <td>java.util.Date</td>
 <td>N</td>
 <td></td>
 </tr>
 <tr>
 <td><code>stat_search_codes</code></td>
 <td>List<String></td>
 <td>N</td>
 <td>List of *codes* for the statistical search codes on the item</td>
 </tr>
 </tbody>
 </table>

 <p>Other parameters that will be persisted but have defaults: <code>fast_add</code> is always 'N', and <code>unique_id_prefix</code> is
 always 'wio' (work-item-oleml).  There are other properties on items (see ole_ds_item_t in the database) but they get no special
 handling.
 </p>
 */
@Slf4j
class HoldingsPersistor {

    /**
     * Mapping of common names for extent of ownership fields to IDs int he default OLE database
     * <code>ole_cat_ownership_typ_t</code> table.
     **/
    def EXT_OWNERSHIP_TYPE = [
            coverage              : 1, // NCSU local name  for bbu
            bbu                   : 1, //"basic bibliographic unit"
            supplementary_material: 2,
            supp_mat              : 2,
            indexes               : 3
    ]

    /**
     * A data structure describing what is persisted by this class and objects
     * related to saving the output.
     * <ul>
     *      <li><code>table</code> (in OLE database)</li>
     *      <li><code>stream</code> (output)</code>
     *      <li><code>mapper</code> (closure that maps records to output lines)</li>
     *      <li><code>file</code> the output file</li>
     * </ul>
     **/
    Map persistables = [
            holdings              : [
                    table : 'ole_ds_holdings_t',
                    stream: null,
                    mapper: null
            ],
            holdings_stat_search_codes: [
                    table: 'ole_ds_holdings_stat_search_t',
                    stream: null,
                    mapper: null
            ],
            items                 : [
                    table : 'ole_ds_item_t',
                    stream: null,
                    mapper: null
            ],
            item_stat_search_codes: [
                    table : 'ole_ds_item_stat_search_t',
                    stream: null,
                    mapper: null
            ],
            uris                  : [
                    table : 'ole_ds_holdings_uri_t',
                    stream: null,
                    mapper: null
            ],
            ext_ownerships        : [
                    table : 'ole_ds_ext_ownership_t',
                    stream: null,
                    mapper: null
            ],
            ext_ownership_notes   : [
                    table : 'ole_ds_ext_ownership_note_t',
                    stream: null,
                    mapper: null
            ]
    ]


    private ConfigObject config

    private RecordCache _cache;

    private Sql oleSql

    private Set<String> unknownLocationCodes = new HashSet<String>()

    /**
     * Queries for extracting data (ids, reference data) from the OLE database.
     */
    def queries

    /**
     * Holder for reference data from OLE database.
     */
    private Map refData = [
            locations          : [:],
            itemTypes          : [:],
            itemStatSearchCodes: [:]
    ]

    /**
     * Holds counter closures that increment ids for each new object.
     */
    private Map counters = [:]

    private def listeners = [
            commit: newHashSet(),
            shutdown: newHashSet()
    ]

    private ConfigLoader loader

    /**
     * JDBC batch size when using direct JDBC inserts.
     */
    int batchSize = 100

    /**
     * Simple closure that persists the internal cache (writes out all stored records to a file).
     * <p>
     *     The primary use case for this is to allow transparent commit of all accumulated holdings after
     * the associated <code>BibPersistor</code> has done a commit, e.g.
     * <code>
     *     def bibPersistor = new BibPersistor(...)
     *     def holdingsPersistor = new HoldingsPersistor(...)
     *     bibPersistor.addCommitListener( holdingsPersistor.commitListener )
     * </code>
     * <p>If bibs and holdings are generated at the same time, this ensures that all bib IDs referenced by the holdings
     * in this object's cache have already been saved to the OLE databse before writing the holdings.
     * </p>
     */
    public Closure commitListener = {
        Object source, Object event ->
            this.persistCache()
    }

    /**
     * Constructor with configuration loader.
     * @param loader a loader for the configuration used by this class.
     * @param autoInit if <code>true</code>, call the <code>init()</code> method in the constructor,
     * if <code>false</code> requires the caller to manually invoke
     * <code>init()</code>
     * @see #init()
     **/
    public HoldingsPersistor(ConfigLoader loader, boolean autoInit = true) {
        this.loader = loader;
        _cache = new RecordCache(1200, false)
        if (autoInit) {
            init()
        }
    }
    /**
     * Adds multiple new records to be persisted.
     * @param records a list of holdings records.
     * @return a list of booleans indicating, for each record, whether it was successfully
     * placed into the cache.
     */
    def leftShift(List<Map<String, ?>> records) {
        records.collect {
                Map<String,?> rec ->
                    addRecord(rec)
        }
    }

    // mutates record in place, setting default values
    private void setDefaults(Map<String, ?> record) {
        record.staff_only = record.containsKey('staff_only') ? record.staff_only : 'N'
        record.unique_id_prefix = record.unique_id_prefix ?: 'who'
        record.location_level = record.location_level ?: "SHELVING"
    }

    protected boolean addRecord(Map<String, ?> record) {
        record.holdings_id = record.holdings_id ?: counters.holdings()
        def hid = record.holdings_id
        def locationCode = record.remove("location_code")
        record.location_id = record.location_id ?: refData.locations[locationCode]
        if ( record.location_id == null && locationCode != null) {
            // this is a problem, so report it, but keep on chugging
            // first, write out the value into the "location" property.
            // this gives us a shot at fixing things directly in the database.
            record.location = locationCode
            if ( ! ( locationCode in unknownLocationCodes ) ) {
                log.warn("Encountered unknown location code '${locationCode}' for holdings w/id ${record.holdings_id} [bib: ${record.bib_id}]")
                unknownLocationCodes.add(locationCode)
            }
        }


        setDefaults(record)


        record.items.each() { item ->
            item.holdings_id = hid
            item.item_id = item.item_id ?: counters.items()
            item.item_type_id = item.item_type_id ?: refData.itemTypes[item.remove("item_type_code")]

            // scrub location info from items unless they're different from the ones on the parent holdings.
            if ( item.location_code == locationCode || item.location_id == record.location_id ) {
                item.location = ""
                item.location_id = null
                item.location_level = null
            } else {
                item.location_id = item.location_id ?: refData.locations[item.remove("location_code")]
            }

            if ( item.containsKey('stat_search_codes') ) {
                item.stat_search_codes = item.stat_search_codes.collect {
                    code ->
                        if (refData.itemStatSearchCodes[code]) {
                            [
                                    item_stat_search_id : counters.item_stat_search_codes(),
                                    item_id             : item.item_id,
                                    stat_search_code_id : refData.itemStatSearchCodes[code]
                            ]
                        }
                }.grep()
            }

        }

        if (record.uri) {
            record.uri.holdings_id = record.uri.holdings_id ?: hid
            record.uri.holdings_uri_id = record.uri.holdings_uri_id ?: counters.holdings_uris()
        }

        if ( record.containsKey('stat_search_codes' ) && record.stat_search_codes) {
            record.stat_search_codes = record.stat_search_codes.collect {
                code ->
                   if ( refData.itemStatSearchCodes[code] ) {
                        [
                                holdings_stat_search_id: counters.holdings_stat_search_codes(),
                                holdings_id        : record.holdings_id,
                                stat_search_code_id: refData.itemStatSearchCodes[code]
                        ]
                   }
            }.grep()
        }

        def extOrds = [:].withDefault { String key -> 0 }
        record.ext_ownership?.each {
            Map ext ->
                def type = ext.remove("type")
                if (!type) {
                    type = "bbu"
                }
                ext.ext_ownership_id = ext.ext_ownership_id ?: counters.ext_ownerships()
                ext.holdings_id = hid
                ext.ext_ownership_type_id = EXT_OWNERSHIP_TYPE[type]
                ext.ord = ext.ord ?: ++extOrds[type]
                ext.notes?.each {
                    note ->
                        note.ext_ownership_id = ext.ext_ownership_id
                        note.ext_ownership_note_id = note.ext_ownership_note_id ?: counters.ext_ownership_notes()
                }
        }

        if (_cache.atCapacity) {
            persistCache()
        }
        return _cache.offer(record)


    }

    /**
     * Adds a single record to be persisted.
     * @param record a holdings record.
     * @return <code>true</code> if the record is added to the internal cache,
     * <code>false</code> otherwise.
     **/
    def leftShift(Map<String, ?> record) {
        addRecord(record)

    }

    /**
     * Adds a new listener for "commit" messages from this object.  Commit messages are fired after the
     * internal cache is flushed to output.
     * @param listener a closure that receives two parameters, a "source" object (this), and an event, which is itself
     * a map with the keys <code>recordType</code> (name of the table containing persisted data), and <code>records</code> (a list
     * of the records that were persisted).
     */
    void addCommitListener(Closure listener) {
        listeners.commit.add(listener)
    }

    /**
     * Fires a comment event; the event should have the keys <code>recordType</code> and <code>records</code>
     * @param event
     * @see #addCommitListener(Closure)
     */
    protected void fireCommitEvent(event) {
        listeners.commit.each { it(this, event) }
    }

    /**
     * Adds a listener for the shutdown event.  THis allows calling code to access metadata generated by this
     * class during runtime.   Shutown events consist of a single map whose keys are "source" (<code>this</code>) and
     * "data", which will be equal to <code>persistables.values</code>
     * @param listener a closure that listens for shutdown events (must take a Map as a parameter).
     * @see #persistables
     */
    void addShutdownListener(Closure listener) {
        listeners.shutdown.add( listener )
    }

    /**
     * Persists the internal cache to output.  This may result in a number of "commit" events being generated.
     * Under normal circumstances, clients do not need to call this method, as it will generally get called when
     * the cache is full or on invocation of the <code>commitListener</code> closure
     * @see #commitListener
     */
    public def persistCache() {
        doPersist(_cache.readCache())
    }

    private void doPersist(List<Map<String, ?>> holdings) {
        // extract the items and URIs (and extents of ownership, when we get 'em)
        def items = holdings.collect { it.remove('items') }.grep().flatten()
        def holdings_sscs = holdings.collect { it.remove("stat_search_codes") }.grep().flatten()

        def item_sscs = items.collect { it.remove('stat_search_codes') }.grep().flatten()

        def uris = holdings.collect { it.remove('uri') }.grep().flatten()
        def ext_ownerships = holdings.collect { it.remove('ext_ownership') }.grep().flatten()
        def eo_notes = ext_ownerships.collect { it.remove('notes') }.grep().flatten()

        // p- and e- holdings have different keys, and so need to be added in different batches
        ['holdings': holdings, holdings_stat_search_codes: holdings_sscs, items: items, item_stat_search_codes: item_sscs, uris: uris, ext_ownerships: ext_ownerships, ext_ownership_notes: eo_notes].each { key, records ->
            def handler = persistables[key]
            records.each { Map row ->
                handler.stream << handler.mapper(row).values().join(',') + "\n"
                handler.recordCount.incrementAndGet()
            }
            fireCommitEvent([recordType: key, records: records])
        }
        return
    }

    /**
     * Creates a "counter" closure that increments from a seed value.
     * @param start the seed value.
     * @return a closure that, each time it is invoked, will increment an intenral counter and recturn the
     * counter's value.
     */
    static def makeCounter(long start = 1L) {
        AtomicLong current = new AtomicLong(start)
        return { current.incrementAndGet() }
    }

    /**
     * Create the counters (closures that mimic the internal database sequences) used to create new IDs
     * for inserted entities.
     * @see #init()
     */
    def initCounters() {
        log.trace "Queries: {}", queries

        def oq = queries.ole
        counters.with {
            holdings = makeCounter(this.oleSql.firstRow(oq.holdings.count)[0])
            holdings_stat_search_codes = makeCounter(this.oleSql.firstRow(oq.holdings.stat_search_codes)[0])
            items = makeCounter(this.oleSql.firstRow(oq.items.count)[0])
            item_stat_search_codes = makeCounter(this.oleSql.firstRow(oq.items.stat_search_codes)[0])
            holdings_uris = makeCounter(this.oleSql.firstRow(oq.holdings_uri.count)[0])
            ext_ownerships = makeCounter(this.oleSql.firstRow(oq.ext_ownership.count)[0])
            ext_ownership_notes = makeCounter(this.oleSql.firstRow(oq.ext_ownership_notes.count)[0])
        }
    }

    /**
     * Create the mapping from item type codes to their IDs in the OLE database.
     * @see #init()
     **/
    def initItemTypeCache() {
        oleSql.eachRow(queries.ole.item_types.select) {
            refData.itemTypes[it.code] = it.id
        }
        log.info("Loaded ${refData.itemTypes.size()} item types")
    }

    /**
     * Create the mapping from location codes to IDs in the OLE database.
     * @see #init()

     **/
    def initLocationCache() {
        oleSql.eachRow(queries.ole.locations.select) {
            refData.locations[it.code] = it.id
        }
        log.info("Loaded ${refData.locations.size()} locations")
    }

    /**
     * Create the mapping from statistical search codes to IDs in the OLE database.
     * @see #init()
     **/
    def initStatSearchCodeCache() {
        oleSql.eachRow("SELECT stat_srch_cd, stat_srch_cd_id FROM ole_cat_stat_srch_cd_t") {
            row ->
                refData.itemStatSearchCodes[row.stat_srch_cd] = row.stat_srch_cd_id
        }
        log.info "Loaded ${ refData.itemStatSearchCodes.size()}  stat search codes"
    }
    /**
     * Create mappings for referential data (textual codes to IDs)
     */
    def initCaches() {
        initLocationCache()
        initItemTypeCache()
        initStatSearchCodeCache()
    }

    /**
     * Initialize this object, including its data source(s) and any caches and other internal data sources.
     * <p>
     *      Some clients may wish to customize this object before calling this method; normally this method
     *      is called by the constructor, but if <code>autoInit</code> parameter is set to false, client
     *      code must do this first.  This is an advanced feature, however.
     *  </p>
     */
    public void init() {
        queries = loader.config.queries
        oleSql = new Sql(loader.getDataSource("ole"))
        initCaches()
        //holdingsTable = oleSql.dataSet("ole_ds_holdings_t")
        //itemTable = oleSql.dataSet("ole_ds_item_t")
        //uriTable = oleSql.dataSet("ole_ds_holdings_uri_t")
        //extOwnershipTable = oleSql.dataSet("ole_ds_ext_ownership_t")
        batchSize = loader.config.performance.batchSize ?: 100
        def analyzer = new TableAnalyzer(sql: oleSql)
        def outputDir = new File( loader.config.persistor.outputDir ?: "/tmp" )
        persistables.each {
            String pType, Map data ->
                def file = new File(outputDir, "ole-${pType}.csv")
                data.file = file
                data.mapper = analyzer.getLoaderColumnMapper(data.table)
                data.loaderQuery = analyzer.getDataLoadTemplate(file.name, data.table)
                log.info "Creating new output file ${file.absolutePath} for ${pType}"
                data.stream = new AgentWriter(file.newWriter("utf-8"))
                data.recordCount = new AtomicLong(0)
        }
        initCounters()
    }

    def getRecordCounts() {
        persistables.collectEntries { String pType, Map data ->
            [pType: data.recordCount.get()]
        }
    }

    /**
     * Shuts down this object, including persisting any records remaining in internal caches, closing all
     * output streams and JDBC connections.  Fires a "shutdown" event.
     * @return
     **/
    def shutdown() {
        log.info("Shutting down persistor, writing remaining records")
        doPersist(_cache.readCache())
        def scriptFile = new File(loader.config.persistor.outputDir ?: "/tmp", "ole-loader.loader")
        scriptFile.withWriter {
            sw ->
                persistables.each {
                    String pType, Map data ->
                        log.info "Shutting down output stream for ${pType}"
                        if (data.stream) {
                            data.stream.flush();
                            data.stream.close()
                        }
                        sw << data.loaderQuery
                }
        }
        persistables << [data: [file: scriptFile]]
        def source = this
        log.info "Notifying shutdown listeners"
        listeners.shutdown.each { listener -> listener([source: source, data: persistables.values()]) }
        oleSql.close()
        log.info("Shutdown.")
    }
}
