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

/**
 * Classes that output MySQL "Loader" files.
 *
 * <p>This is probably the fastest (but most brittle) loading strategy, as it bypasses any APIs OLE provides and
 * has to manage references between the records involved in bibliographic data loading, including the generation of
 * new IDs.
 * It is also faster than other methods, including direct JDBC inserts, at the cost of increased complexity.
 * </p>
 * <p>
 *     <em>Note</em> OLE's object-relational mapping scheme requires that each table with an incrementing primary
 *     key have a "sequence" table, which is usually but not always has the same name as the data table, except the
 *     final <code>_t</code> in the table name is replaced with <code>_s</code>.  If you don't ensure all the sequence tables
 *     contain at least one entry with the 'next' available ID value, you may run into problems when you use OLE.
 *     A script for updating the sequences is provided as a separate part of this distribution <code>updateOLESequences.groovy</code>;
 *     it must be run from the root directory of the OLE source code checked out from subversion.
 * </p>
 *
 * <p>
 *     Classes in this package output files in either the <code>/tmp</code> directory or the configuration parameter
 *     <code>persistors.loader.outputDir</code>.  Importing them into MySQL requires understanding how the MySQL
 *     LOAD DATA INFILE procedure works, but here is a rough overview.
 *
 *     <ul>
 *         <li>Unless you configure your MySQL server to allow it, <code>LOAD DATA INFILE</code> commands can only
 *            be executed on the host running the MySQL daemon.</li>
 *         <li>Output files must be in a directory that can be read by the <code>mysqld</code> process.</li>
 *         <li>Relative paths will be interpreted to lie under the <code>mysqld</code> process' home directory, which
 *         is usually <code>/var/lib/mysql</code></li>
 *         <li>The process will run even faster, but be slightly more dangerous, if you call <code>SET foreign_key_checks =0</code>
 *         at the top of your script.</li>
 *         <li>a <code>LOAD DATA INFILE</code> command accepts a number of parameters outlining column and row separators,
 *         and this is configurable.  Most of the tables are output as something that looks a lot like CSV (comma separated
 *         values), with the exception of the bibliographic records file, which uses <code>$$$$</code> as a column separator
 *         and <code>--\n--</code> as a line (row) separator.  These values were chosen as being unlikely to occur in a
 *         serialized MARC record.</li>
 *         <li>NULL database values are represented as literal <code>\N</code> in the column</li>
 *     </ul>
 * </p>
 * <p>Thus, the LOAD DATA INFILE command to read a CSV file into a table might look like this:
 * <code>
 *     LOAD DATA INFILE '/tmp/my-table.csv'
 *        INTO TABLE ole.my_table_t
 *        FIELDS TERMINATED BY ','
 *        OPTIONALLY ENCLOSED BY '"'
 *        LINES TERMINATED BY '\n';
 * </code>
 * And for the "bib" table output:
 * <code>
 *     LOAD DATA INFILE '/tmp/ole-bibs.txt'
     INTO TABLE ole_ds_bib_t
         CHARACTER SET utf8mb4
         FIELDS TERMINATED BY '$$$$'
         OPTIONALLY ENCLOSED BY '"'
         LINES TERMINATED BY '--\n--';
 * </code>
 * <p>
 *     The package contains a class called <code>LoaderPackager</code>, which can automatically gather up the data required
 *     to create a zip archive of the output files, along with an "SQL" script that will execute the LOAD DATA INFILE
 *     commands, with a little editing.  The resulting zip can then be copied to your database server.  If you unpack it in the server's <code>/tmp</code>
 *     directory, you should edit all the paths to be absolute, and then you can run it via something like
 *     <code>mysql -u root -p ole &lt; ole-loader.sql</code> (and enter the prompt for your password).  Alternately, you <em>could</em>
 *     unpack the file in <code>/var/lib/mysql</code> and execute it directly, but that might require root level access.
 * </p>
 *
 *
 * @see org.kuali.ole.contrib.ConfigLoader
 * @see org.kuali.ole.contrib.persistor.common.TableAnalyzer
 * @see <a href="http://dev.mysql.com/doc/refman/5.7/en/load-data.html">LOAD DATA INFILE Syntax</a> (MySQL manual).
 */
package org.kuali.ole.contrib.persistor.loader;