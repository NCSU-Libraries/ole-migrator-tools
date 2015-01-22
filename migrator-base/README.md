OLE Migrator Base
=================

This subproject is the heart of the OLE migrator toolkit.  It is intended to be
referenced as a gradle subproject so its many dependencies can be easily
incorporated.

Key components are the configuration loader, persistors, and various utilities.

As noted in the parent project's README, you should run `gradle groovydoc` in
the parent project and then browse through the generated output of that task
(generated in `build/docs/groovydoc/index.html` relative to this file).

ConfigLoader
------------

`org.kuali.ole.contrib.ConfigLoader` is used by other classes in the framework.  It provides a standard configuration file format and a facility for managing JDBC DataSources, primarily.  See [ConfigSlurper](http://groovy.codehaus.org/ConfigSlurper) for more information on the file format.

A basic sample of a config file is available in the `templates` directory in the project root, and will be put into the right location by running `gradle createMigration` in the root directory.  See project README for more info.

## Persistors

Package: `org.kuali.ole.contrib.persistors` and subpackages.

During the development of this toolkit, two approaches based on direct
insertion of data into the OLE database were tried.  The version that
was implemented first was found to work, albeit somewhat slowly (on the
order of 9+ hours for about 6 million bibliographic, item, and holdings
records, when using a number of 'tricks' such as JDBC batching).  As an
experiment, the strategy was changed to generate "loader" files which
the MySQL documentation suggests is significantly faster.  Although this
strategy is somewhat fussier than row-by-row insertions, it is
significantly faster, on the order of an 90 minutes to generate the
files and around 20 minutes to ingest them.  

So that's the version we have shipped.  The bones of the JDBC based
approach are still here, and available to be built upon.

There is also a placeholder for a "persistor" strategy that used the OLE
Docstore API.  Time constraints have meant this didn't get developed.

## Utilities

Currently a very basic "streaming JSON reader" is included should you
wish to handle the bulk of your migration using your programming
language of choice to handle the bulk of your data migration work.

The general idea is to create "streamed" JSON, which is not actually
JSON, but consists of a series of JSON objects in the formats expected by the persistors (read the groovydocs) that are separated by spaces rather than wrapped up into arrays.  That is, rather than generating true JSON files that look like:

```JSON

    [{ bib_id  1, ... }
     { bib_id: 2 ...},
    { bib_id: NNNN, ... }
    ]

```

You would generate a file that looks like

```JSON

{ .. record 1 }
{ ... record 2 }
{ ...record 3 }

```

And process that with `org.kuali.ole.contrib.json.JsonStreamReader`.
The motivation here is make it easy to both generate and process
extremely large datasets without having to load those datasets into
memory.  

Good Luck!

AC

