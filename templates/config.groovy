/**
 * Configuration file sample for OLE loading.  
 * This uses Groovy's ConfigSlurper format: 
    http://groovy.codehaus.org/ConfigSlurper
 *
 * Not all sections and configuration values here are required.
 * Minimally, a configuration for the "ole" datasource (and probably
   one for your existing ILS' database) should be defined here,
   along with the queries.ole section in its entirety.  If you 
   are using the bundled "dead letter queue" feature you will want to keep
   the 'migration' queries as well.
 **/

message = "Hello, OLE migrator"
 
/**
 * "queries" lists SQL queries.
 */
queries {

    ole {
        holdings {
            count = "SELECT COALESCE(max(holdings_id),1) FROM ole_ds_holdings_t"
            insert = """INSERT INTO ole_ds_holdings_t (
                            holdings_id,
                            bib_id,
                            holdings_type,
                            former_holdings_id,
                            staff_only,
                            location_id,
                            call_number_type_id,
                            call_number,
                            shelving_order,
                            date_created,
                            date_updated,
                            updated_by,
                            unique_id_prefix
                            ) VALUES (
                              ?,
                              ?,
                              ?,
                              ?,
                              ?,
                              ( select locn_id FROM ole_locn_t WHERE locn_cd = ? ),
                              ?,
                              ?,
                              ?,
                              ?,
                              ?,
                              'olemigrator',
                              'wno'
                              )"""
            stat_search_codes = "SELECT COALESCE(max(holdings_stat_search_id),1) FROM ole_ds_holdings_stat_search_t"
        } // queries.ole.holdings

        holdings_uri {
            count = "select coalesce(max(holdings_uri_id),1) FROM ole_ds_holdings_uri_t"
        }

        ext_ownership {
            count = "select coalesce(max(ext_ownership_id),1) FROM ole_ds_ext_ownership_t"

        }

        ext_ownership_notes {
            count = "select coalesce(max(ext_ownership_note_id),1) FROM ole_ds_ext_ownership_note_t"
        }

        items {
            count = "select COALESCE(max(item_id),1) FROM ole_ds_item_t"
            insert = """INSERT INTO ole_ds_item_t
                  				( item_id,
                  				holdings_id,
                  				fast_add,
                  				staff_only,
                  				barcode,
                  				item_type_id,
                  				item_status_id,
                  			  location_id,
                  			  num_pieces,
                  			  price,
                  			  date_updated,
                  			  updated_by,
                  			  unique_id_prefix)
                  			VALUES (?,
                  			   ?,
                  			   'N',
                  			   ?,
                  			   ?,
                  			   ?,
                  			   1,
                  			   NULL,
                  			   ?,
                  			   ?,
                  			   ?,
                  			   'olemigrator',
                  			   'wio')""" // item.insert
            stat_search_codes = "SELECT COALESCE(max(item_stat_search_id),1) FROM ole_ds_item_stat_search_t"
        } // queries.ole.items

        item_types {
            select = """SELECT itm_typ_cd_id as "id", itm_typ_cd as "code" FROM ole_cat_itm_typ_t"""
        }

        locations {
            select = """select locn_id as "id", locn_cd as "code", locn_name as "name", parent_locn_id as "parent_id" from ole_locn_t"""
        }
    } // queries.ole
} // queries

// TODO : can we remove this?

valueDefaults {
    fast_add = 'N'
    last_updated_by = 'olemigrator'
}

/**
 * Number of "reader" and "writer" threads.  This has no direct effect on how many threads are used by the
 * migrator-core classes (Persistors, etc.) although any implementation of those should be threadsafe.   The example
 * driver shows how you might use these to tune the number of reader and writer threads.
 *
 * These values can be overridden in the "environments" section if you so desire, or can be computed based on the number
 * of CPU cores you have available, etc.

 * see sample driver.groovy for how you might use this.
 */
performance {
    threads {
        reader = 5
        writer = 1
    }
}

/**
 * Values for use in persistors; these configure where the database loader file and the driver script go.
 */
persistor {
    loader {
        outputDir = new File("/tmp").exists() ? "/tmp" : System.getProperty("java.io.tmpdir")
    }
}


// these entries set the defaults for the values specified under "environments"
// named properties should be settable on a dbcp2 BasicDataSource object
// the "properties" map can contain driver-specific properties set through
// the BDS' setProperty() method.
ole {
    defaultAutoCommit = false
}

migrator {
    defaultAutoCommit = false
}

// defaults common to all

[ole, migrator].each {
    it.properties = [:]
}

/**
 * Environment-specitic configuration (see ConfigSlurper documentation, link above)

 * The ConfigLoader in the migrator-base subproject uses data in here to
 * instantiate JDBC DataSources.  For available properties, see
 * http://commons.apache.org/proper/commons-dbcp/apidocs/org/apache/commons/dbcp2/BasicDataSource.html
 */
environments {
    development {
        source {
            driver = "org.h2.Driver"
            url = "jdbc:h2:mem"
            username = "sa"
            password = ""
            poolSize = performance.threads
        }

        /**
         * Database to cache values discovered during migration process, e.g. mappings of old
         * IDS to new IDs.
         */
        migration {
            driver = "org.h2.Driver"
            // would love to use System.getProperty("java.io.tmpdir") but OS X doesn't put that
            // in an easily discoverable place; so we assume /tmp unless Windows.
            def tmpDir = new File("/tmp")
            if ( !tmpDir.exists() ) {
                tmpDir = new File(System.getProperty('java.io.tmpdir') )
            }
            dbDir = new File(tmpDir, "ole-migration")
            url = "jdbc:h2:" + new File(dbDir, "oledata").absolutePath + ";AUTO_SERVER=TRUE"
            username = "sa"
            password = ""
            poolSize = performance.threads
        }

        ole {
            connectionInitSqls = ['SET autocommit = 0;', 'SET FOREIGN_KEY_CHECKS =0;', 'SET unique_checks =0;' ]

            //driver = "org.mariadb.jdbc.Driver"
            driver = "com.mysql.jdbc.Driver"
            // 'extra' properties help enable and speed up
            // JDBC batching.
            url = "jdbc:mysql://localhost:3306/ole?useServerPrepStmts=false&rewriteBatchedStatments=true"
            username = "oledatabaseusername"
            password = "oledatabasepassword"
            /** these are things you might tweak for better performance **/
            defaultAutoCommit = false
            /*properties = [useServerPrepStmts      : false,
                          rewriteBatchedStatements: true
            ] */
            poolSize = performance.threads
        }

    }
    // you could have other environments defined here, e.g. "production"
}
