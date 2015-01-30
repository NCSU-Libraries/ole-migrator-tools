#!/usr/bin/env groovy

/**
 * You can edit this to create your driver script.
 * This sample doesn't actually do much, but it does illustrate
 * some usage patterns.
 */
import org.kuali.ole.contrib.ConfigLoader

import org.kuali.ole.contrib.json.JsonStreamReader

import org.kuali.ole.contrib.persistor.loader.BibRecordPersistor

// loads config.groovy and uses the "development" 
// environment from it
def configLoader = new ConfigLoader("development")
def config = configLoader.loadConfig()

println config.message

def reader = new JsonStreamReader()

// this will fail until you edit config.groovy
// so it can connect with your database.
def persistor = new BibRecordPersistor(configLoader)

// sample data file is available on the classpath;
// this is just used here to illustrate that your
// job is to feed Map-based records into your persistor(s).
def recordStream = JsonStreamReader.getClass().getResourceAsStream("/data.json")


reader.eachRecordInStream(recordStream) {
    Map<String,?> rec -> 
        persistor <<  rec
}

// make sure the persistor has done all the work it needs to do and
// cleans up
persistor.join()

// your output will now be in /tmp/ole-bib.txt
