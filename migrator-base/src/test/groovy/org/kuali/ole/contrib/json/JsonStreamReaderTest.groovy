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

import spock.lang.Specification

/**
 * Unit test(s) for JSON deserialization.
 */
class JsonStreamReaderTest extends Specification {
    def "simple sample deserializes correctly"() {
        setup:
            def r = new JsonStreamReader()
            def records = []
            r.eachRecordInStream( getClass().getResource("/sample.json").openStream() ) {
                records << it
            }
        expect:
        records.size() == 3

        records.each { Map<String, ?> rec ->
            rec.each { k, v ->
                if ("date" in k && v) {
                    v.class.isAssignableFrom(Date.class)
                }
            }
        }

    }
}
