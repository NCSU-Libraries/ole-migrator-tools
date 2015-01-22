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
import groovy.util.logging.Slf4j
import org.apache.commons.dbcp2.BasicDataSource

import javax.sql.DataSource


/**
 * Loads configurations and caches certain "expensive" objects such as datasources. This class was
 * conceived as a "practical singleton", as in: you should only need one instance per run of
 * the application, but nothing prevents you from having multiple instances.
 *
 * <p>
 * General usage is to instantiate the object with the name of your preferred environment (defaults to
 * <code>development</code> if not specified) and &emdash; if needed &emdash; the URL from which to load the
 * configuration.   Various framework classes expect an instance of this class as an argument in their
 * constructor, and most of those will look up a DataSource using the <code>getDataSource(dsName)</code> method.
 * <p>
 * <p>
 *     Configurations are stored the format defined by Groovy's ConfigSlurper class, and a sample should be
 *     provided in the distribution's top level directory (<code>config.groovy.example</code>).
 *     The sections that are required from the sample are
 *     <code>queries.ole</code> and at least one datasource definition for the <code>ole</code> database (at least
 *     with the SQL-based persistor strategies).
 * </p>

 * <p>After instantiating this object and calling Thereafter, the <code>config</code> property will return your configuration.
 *
 * <p>
 *     By default, configuration is loaded from, in order of preference:
 *     <ul>
 *         <li>A non-null custom location specified when the instance is created</code>
 *         <li><code>config.groovy</code> in the current working directory.</li>
 *         <li><code>config.groovy</code> in the default packaage on the classpath (e.g. in the
 *          top directory in a .jar file, or in the classes" directory)</li>
 *          <li><code>config.groovy<.code> on the classpath in the same package as this class.</code>.
 *     </ul>
 * </p>
 * @see groovy.util.ConfigSlurper
 *
 */
@Slf4j
class ConfigLoader {

    private ConfigObject config

    String environment

    URL location

    def locations = ["./config.groovy", "config.groovy"]

    Map<String,DataSource> dataSources = [:]

    /**
     * Constructor for a loader with a specified environment name and optional location.
     * @param environment the name of the environment to load.
     * @param location the location from which to load the configuration.  May be <code>null</code>
     * @see groovy.util.ConfigSlurper
     */
    public ConfigLoader(String environment = "development", URL location = null) {
        this.environment = environment
        this.location = location
    }

    /**
     * Gets the configuration associated with this loader.
     * @return
     * @throw ExceptionInInitializerError if the configuration cannot be looked up.
     */
    public ConfigObject getConfig() {
        return config?: loadConfig()
    }

    public ConfigObject loadConfig() {
        if ( config ) {return config}
        def tried = []

        log.trace("loading configuration for ${environment}")
        if (location) {
            log.debug("Loading configuration from ${location}")
            config = new ConfigSlurper(environment).parse(location)
        } else {
            for (String loc : locations) {
                File f = new File(loc)
                if (f.exists()) {
                    config = new ConfigSlurper(environment).parse(f.toURI().toURL())
                    log.debug("loaded config from ${f.absolutePath} (${config.getClass().name})")
                    return config
                }
                tried << f.absolutePath
            }
            for (String loc : locations) {
                URL locUrl = this.getClass().getResource(loc)
                if (locUrl) {
                    config = new ConfigSlurper(environment).parse(locUrl)
                    log.debug("loaded config from ${locUrl}")
                    return config
                }
                tried << "classpath:${loc}"
            }
        }
        if ( !config ) {
           log.error("Unable to load configuration; tried ${tried}")
           throw new ExceptionInInitializerError("Unable to load configuration")
        }
        config
    }

    /**
     * Gets or creates a data source.
     * @param databaseName the name of the datasource in the configuration file.
     * @return the named datasource.
     * @throws AssertionError if the datasource must be instantiated and a connection cannot be obtained from it.
     */
    DataSource getDataSource(String databaseName) {
        // ensure config is loaded
        getConfig()

        if ( !( databaseName in config ) ) {
            throw new IllegalArgumentException("${databaseName} not found in config")
        }

        if ( databaseName in dataSources ) {
            return dataSources[databaseName]
        }

        def dbConfig = config[databaseName]

        def poolSize = dbConfig.poolSize
        if ( poolSize instanceof ConfigObject ) {
            poolSize = 5
        }
        BasicDataSource ds = new BasicDataSource(
                driverClassName: dbConfig.driver,
                url: dbConfig.url,
                username: dbConfig.username,
                password: dbConfig.password,
                defaultAutoCommit: dbConfig.defaultAutoCommit,
                minIdle: 1,
                testOnCreate: true,
                maxTotal: poolSize,
                poolPreparedStatements: true,
                connectionInitSqls: dbConfig.connectionInitSqls ?: [],
                validationQuery: dbConfig.validationQuery ?: "SELECT 1 FROM DUAL", // mysql accepts this
                maxOpenPreparedStatements: 50,
        )

        if ( 'properties' in dbConfig && config.debug ) {
            dbConfig.properties.each { String propName, value ->
                log.trace "Adding connection property ${propName} => ${ value }"
                ds.addConnectionProperty( propName, String.valueOf(value) )
            }
        }

        log.info "Checking for initial connection SQL for ${databaseName}"
               ds.connectionInitSqls.each {
                   log.info it
               }

        log.info "Testing connection to (${dbConfig.url})"
        def conn = ds.getConnection()
        assert conn, "Couldn't get a connection for ${dbConfig.url}"
        conn.close()
        log.info "Got good connection for ${dbConfig.url} : ${ds}"
        dataSources[databaseName] = ds
        ds
    }

    /**
     * Shuts down this object, including any referenced datasources.
     * @return
     */
    public void shutdown() {
        log.trace "shutdown()"
        dataSources.each {
            name, ds ->
                if ( ds.respondsTo("close") ) {
                    log.debug "Shutting down datasource '${name}'"
                    ds.close()
                }
        }
        log.trace "shutdown() complete"
    }

}
