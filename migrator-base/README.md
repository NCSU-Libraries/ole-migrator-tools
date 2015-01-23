# OLE Migrator Base

This subproject is the heart of the OLE migrator toolkit.  It is intended to be referenced as a gradle subproject so its many dependencies can be easily
incorporated.

As noted in the parent project's README, you should run `gradle groovydoc` in
the parent project and then browse through its output at 
`build/docs/groovydoc/index.html` relative to this file.

## ConfigLoader

`org.kuali.ole.contrib.ConfigLoader` is used by other classes in the framework.
It provides a standard configuration file format and a facility for managing
JDBC DataSources, primarily.  See
[ConfigSlurper](http://groovy.codehaus.org/ConfigSlurper) for more information
on the file format.

A basic sample of a config file is available in the `templates` directory in
the project root, and will be put into the right location by running `gradle
createMigration` in the root directory.  See the project README for more info.

## Persistors

Package: `org.kuali.ole.contrib.persistors` and subpackages.

During the development of this toolkit, we tried two approaches based on direct
insertion of data into the OLE database.  Version one worked, using
(essentially) direct execution of SQL INSERTs via JDBC.

This took 9+ hours for about 6 million bibliographic, item, and holdings
records, when using a number of 'tricks' such as JDBC batching.  

Initially an experiment, we generated "loader" files which
the MySQL documentation suggests is significantly faster than SQL INSERTs.

Although the loader strategy is definitely more work for the end user than
row-by-row insertions, it is significantly faster.  From the same starting
data, it now takes on the order of an 90 minutes to generate the files and
around 20 minutes to ingest them.  

(N.B. the absolute runtimes here reported should not be used in any other
comparisons =)

So that's the version we have shipped.  The bones of the JDBC based
approach are still here, and available to be built upon and improved.

There is also a placeholder for a "persistor" strategy that uses the OLE
Docstore API.  Time constraints have meant this didn't get developed.

### More on the "loader" Persistor

By default, the output "loader" files will be put into `/tmp` or, if no such
directory exists, your system's temporary dir (the value of `java.io.tmpdir`
system property).  All created files will start with `ole-` and most will be in
CSV format; bibliographic records will be in a file named `ole-bibs.txt` and use
'$$$$' as a field separator and `--\n--` (two minus signs on either side of a
UNIX newline) as a record separator.  
 
See the package-level groovydoc for `org.kuali.ole.contrib.persistor.loader`
for more on how to load these files into your MySQL database.

## Utilities

Currently a very basic "streaming JSON reader"
(`org.kuali.ole.contrib.json.JsonStreamReader`) is included, should you wish to
handle the bulk of your migration using your programming language of choice to
generate "ingestable" files.

To use it you create "streamed" JSON, which is not actually
JSON, but consists of a series of JSON objects in the formats expected
by the persistors (read the groovydocs) that are separated by spaces
rather than wrapped up into arrays.

That is, rather than generating true JSON files that look like:

```JSON

    [{ "bib_id" : 1, ... },
     { "bib_id" : 2 ...},
     ...
     { "bib_id" : NNNN, ... }
    ]
```

You would generate a file that looks like

```Javascript

{ "bib_id" : 1 }
{ "bib_id" : 2 }
 ...
{ "bib_id" : NNNN }

```

See the groovydoc on `JsonStreamReader` for more information.

Good Luck!

AC
