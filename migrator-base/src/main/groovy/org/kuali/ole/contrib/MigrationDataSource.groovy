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

package org.kuali.ole.contrib
import groovy.sql.DataSet
import groovy.sql.Sql
import groovy.util.logging.Slf4j
/**
 * Service for accessing an intermediate database used to transfer data between your current ILS and OLE.
 * This includes storing mappings from old IDs to new IDs, a "dead letter queue" to hold records that could not
 * be migrated along with the encountered error.
 *
 * <p>The general expectation for this class is that it will be used with a purpose-built database that can be re-initialized
 * at will.  An embedded pure-java database such as <a href="https://h2database.com">h2</a> is suggested, but you can
 * certainly use it with a more traditional RDBMS such as MySQL.
 * </p>
 *
 * @author adjam, $LastChangedBy$
 * @version $LastChangedRevision$
 **/
@Slf4j
class MigrationDataSource {

	private ConfigObject config
	
	private ConfigObject sqlConfig

	private Sql sql
	
	def initialize = false
	
	private DataSet deadLetter

	public MigrationDataSource(ConfigLoader loader, boolean initialize = false) {
        sql = new Sql( loader.getDataSource("migration") )
		this.config = loader.config
		this.sqlConfig = config.sql.intermediate
		if ( initialize ) {
            initializeDatabase()
        }
	}

    def initializeDatabase() {
        log.info "Initializing integration database"
        sql.execute """DROP TABLE IF EXISTS identifier_map"""
        sql.execute """DROP TABLE IF EXISTS dead_letter"""
        sql.execute """DROP INDEX IF EXISTS ib_id_index"""
        sql.execute """CREATE TABLE identifier_map(id INTEGER PRIMARY KEY AUTO_INCREMENT, src_id_type VARCHAR(32), dest_id_type VARCHAR(32), src_id varchar(64), dest_id varchar(64))"""
        sql.execute """CREATE INDEX id_index ON identifier_map(src_id_type, src_id)"""
        sql.execute """CREATE TABLE dead_letter (id INTEGER PRIMARY KEY AUTO_INCREMENT, bib_id varchar(12) NOT NULL, content CLOB, reason CLOB)"""
        sql.execute """CREATE INDEX bib_id_index ON dead_letter(bib_id)"""
        log.info "Initialized integration database"
    }

    def getSql() {
        sql;
    }

    /**
     * Stores a record in the "dead letter" queue in the migration database.
     * @param bib_id
     * @param content the content of the record, regardless of form
     * @param reason a human-readable reason or the serialized exception stack trace.
     * @return
     */
    def storeError(String bib_id, String content, String reason) {
        deadLetter.add([ bib_id: bib_id, content: content, reason: reason ])
    }


}