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

import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Handles assembling generated LOAD DATA INFILE files, along with a driver script, into a zip archive.  Use of this class
 * is optional.
 * <h3>Usage</h3>
 *
 *     <pre>
 *     <code>
 *         def packager = LoaderPackager()
 *
 *         def hp = new HoldingsPersistor(...)
 *         hp.addShutdownListener( packager )
 *
 *
 *         // ... wait for all persistors to complete; as each finishes and its shutdown() method is called,
 *         // it will send a list of map to the "shutdownListener"
 *         //
 *         def zipArchive = packager.run()
 *    </code>
 *   </pre>
 *   @see HoldingsPersistor#addShutdownListener
 */
@Slf4j
class LoaderPackager {


    File outputFile

    def archiveFiles = []

    def shutdownListener = {
        Map persistables ->
            persistables.each { p ->
                archiveFiles << p.file
            }
    }


    def run() {
        if ( outputFile.exists() ) {
            def nextOutput = new File(outputFile.absolutePath + ".bak")
            log.info "Backing up previous output file to ${ nextOutput.absolutePath }"
            outputFile.renameTo(nextOutput)
        }
        outputFile.withOutputStream {
            out ->
                def zos = new ZipOutputStream(out)
                zos.setLevel(9) // max compression
                zos.comment = "OLE migration MySQL loader files"

                archiveFiles.each {
                    File entryFile ->
                        def entry = new ZipEntry(entryFile.name)
                        zos.putNextEntry(entry)
                        entryFile.withInputStream { input ->
                            zos << input
                        }
                        zos.closeEntry()
                }
                zos.close()
        }

        log.info "SQL Loader files stored in ${ outputFile.absolutePath }"
        return outputFile
    }

}
