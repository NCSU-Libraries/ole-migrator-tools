#!/usr/bin/env groovy


// updateOLESequences.groovy -- writes sensible values into all "sequence" tables as one might need to do after
// directly importing records in via SQL.

// usage -- groovy updateOLESequences.groovy [ole source root]

// scans provided directory (or current working directory if no argument is provided) for OJB configuration files
// and outputs SQL INSERTs for populating "sequence" tables found.  Output sent to STDOUT

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



import static groovy.io.FileType.FILES

// run in the root of the OLE source tree; finds all OJB files
// and then for each one, finds class descriptors that define a 
// sequence table; outputs SQL to update all of the sequence tables
// with the "maximum" value

def ojbFiles = []

def slurp = new XmlSlurper(false,false,false)

new File(args[0] ?: ".").eachFileRecurse(FILES) {
	if ( it.name.endsWith(".xml") && it.name.toLowerCase().startsWith('ojb-') ) {
		try {

		it.withInputStream { 
			stream ->
				def doc = new XmlSlurper().parse(stream)
				if ( doc.findAll { it.name == "descriptor-repository" } ) {
					ojbFiles << [ it, doc ]
				}
		}
		} catch( Exception x ) {
		//	println "Couldn't parse ${it.absolutePath}: ${x.message}"
		}
	}
}
 /*
 <field-descriptor name="batchProcessProfileId" column="BAT_PRCS_PRF_ID" jdbc-type="VARCHAR" primarykey="true" autoincrement="true" sequence-name="OLE_BAT_PRCS_PRF_S" />
*/

def tables = [:]

ojbFiles.each {
	file, doc ->
		doc.'class-descriptor'.each { 
			def tbl = it.@table
			if ( !tbl ) {
				println "\t\tHEY : ${ it.@class }"
				return
			}
			def pk = it.'field-descriptor'.findAll {
				fd ->
					fd.@primarykey == 'true'
			}
			if ( pk && pk.'@sequence-name' =~ /\S/ ) {
				tables[tbl] = [ seq: pk.'@sequence-name', col: pk.@column ]
		 	}
		}
}

tables.each {
	tbl, data ->
		println "INSERT INTO ${data.seq} (id) VALUES ((SELECT COALESCE(max(${data.col}), 0) +1 FROM ${tbl})) ON DUPLICATE KEY UPDATE id = id;"
}

