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
package org.kuali.ole.contrib.json

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * Streaming JSON reader that handles record-by-record input.  The
 * input stream format is assumed to a stream of objects
 * each of which corresponds to the structure described in the
 * <code>org.kuali.ole.contrib.persistor</code> API documentation.
 * 0
 * <p>
 *     The primary purpose of this class is to allow users who would
 *     prefer not to use Groovy or Java for the main logic of
 *     generating
 *     the records they wish to migrate into OLE.  Create JSON(-ish
 *     output) using
 *     any method you like, and use this class to bridge the
 *     gap from that to the persistors, e.g.
 *     <pre>
 *         <code>
 *         def bibPersistor = new BibRecordPersistor(...)
 *         def reader = new JsonRecordReader()
 *         [ "bibs1.json", "bibs2.json", "bibs3.json" ].each {
 *          def f = new File(it)
 *          reader.eachRecordInFile(f) { Map<String,?> rec ->
 *              bibPersistor << rec
 *          }
 *     </code>
 *     </pre>
 * <p>
 *     Input formatting tips:
 *     <ul>
        <li>do not wrap records in an array,/li>
        <li>Do not separate records with a comma or anything other
 than whitespace.</li>
        <li>Unless you want to do your own special configuration,
 date/timestamp fields follow <em><a href="http://wiki.fasterxml
 .com/JacksonFAQDateHandling">default Jackson format</a></em>.</li>
 * </p>
 * <p>
 *     To provide alternative JSON parsing configurations,
 *     instantiate this class, then build your
 *     own JsonFactory instances set the <code>factory</code> property
 *     on your instance, e.g.
 *     <pre>
 *     <code>
 *     ObjectMapepr myMapper = new ObjectMapper()
 *     ...
 *     JsonFactory myFActory = new JsonFactory(myMapper)
 *     readerInstance.mapper = mymapper
 *     // now use readerInstance as above.
 *     </code>
 *     </pre>
 * </p>
 *     <p>Instances of this class should be threadsafe, after initial
 *     configuration.</p>
 *     @see org.kuali.ole.contrib.persistor
 */
class JsonStreamReader {

    // possible future expansion; make this configurable; however for
    // simple cases the default setup should be just fine.
private JsonFactory factory = new JsonFactory(new
            ObjectMapper())

    private static final TypeReference<Map<String,?>> recordType = new
            TypeReference<Map<String,
            ?>>() {};

    /**
     * Applies a closure to every record found in a JSON stream.
     * @param stream JSON input.
     * @param c a closure that accepts <code>Map<String,?></code>
     * objects.
     * @return this.
     */
    public JsonStreamReader eachRecordInStream(InputStream stream,
                                               Closure c) {
        JsonParser p = factory.createParser(stream)
        while ( p.nextToken() != null ) {
            def rec = p.readValueAs(recordType)
            c.call(rec )
        }
        this
    }

    /**
     * Applies a closure to every record found in a JSON file.
     * @param stream JSON input.
     * @param c a closure that accepts <code>Map<String,?></code>
     * objects.
     * @return this.
     */
    public JsonStreamReader eachRecordInFile(File f, Closure c) {
        f.withInputStream { InputStream s ->
            eachRecordInStream(s,c)
        }
        this
    }


    public static void main(String[] args) {
        new JsonStreamReader().eachRecordInFile( new File
                ("/tmp/sample.json") ) {
            println it.bib_id
        }
    }

}
