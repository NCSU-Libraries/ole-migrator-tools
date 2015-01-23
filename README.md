#OLE Migration Toolkit

A toolkit to assist in the initial migration of bibliographic (MARC
records, holdings, and items) data into Kuali OLE (hereafter, "OLE")
from an existing ILS.

##Overview

OLE's current facilities for bibliographic data import do not perform
well.  In the future, need for this sort of toolkit may be obviated by
performance improvements to OLE's APIs, but you only need to migrate
your data once, and "direct" methods will always be faster than ones
that go through layers of software.  Until that day, you might try this.

The aim of this project is to provide tools that perform well and handle
at least the "last mile" of ensuring your bibliographic data is
persisted into the OLE database, while easing some of the difficulties
presented by migrating from different ILSes.

What this toolkit does *not* do is help map your current data into OLE's
data model.  It defines target data structures and does other useful
things, such as managing foreign key relationships between "new"
database rows.

## Prerequisites

**Note: currently the toolkit only supports MySQL/MariaDB as a target
OLE database.**

This toolkit assumes that you have already built OLE and created its
database, see [OLE Developer Installation
Guide](https://wiki.kuali.org/display/OLE/Developer+Installation+Guide)
for details.  It further assumes that you have already loaded your local
item types, locations, and statistical search codes.  Some information
about how to do this is available at [Impex, Bootstrap, Demo, and Local
Data](https://wiki.kuali.org/display/OLE/Impex,+Bootstrap,+Demo+and+Local+Data)
in the Kuali wiki.  As the tables where this data is stored are
relatively straightforward, it is also possible to load this data via
scripting.

There's currently no way around this: you're going to have to write some
Groovy or Java to use this toolkit.  The "persistors" that do most of
the work accept Java Map objects.  See the "Further Development" section
below for an experimental JSON-based option.

### Requirements

JDK 1.7+
[Gradle](https://www.gradle.org) 2.1+
(recommended) [Groovy](http://groovy.codehaus.org) 2.3+

If you intend to run this on a 'nix system (recommended), you might want
to use [GVM](http://gvmtool.net) to manage the installation and easy
upgrade of Gradle and Groovy.

Use of [GPars](https://gpars.codehaus.org) (concurrency features for
Groovy/Java) is suggested to improve performance where desired, but
not required.  Sample drivers that show you how to use GPars are
provided.

## How To Use This Toolkit

1. Install required software
1. Clone this repository
1. `gradle groovydoc`
1. read the groovydocs!
1. `gradle createMigration` to create the `migration` subproject directory and starter files.  
1. edit `config.groovy` to allow you to connect to your OLE database.
1. write some code (`driver.groovy` and any support classes you might need).

Other sample drivers are in the `templates` directory.

The primary job of the driver script is to feed records into
"persistors" (`org.kuali.ole.contrib.persistors` package and its
subpackages).  These classes handle the "last mile" to the OLE database.  

**Note: currently, the only persistor "strategy" supported is the
'loader' strategy, which generates files suitable for use in MySQL LOAD
DATA INFILE commands**

Check the documentation on classes in the `loaders` subpackage for
information about the data structure formats (generally, Maps) these
Persistors expect.

Execute your driver script with `gradle run`. 

### Logging Configuration

Logging is provided by Groovy's Logging AST transformation (@Slf4j
annotations on classes).  The default log level is INFO and all output
is sent to STDOUT. This is configurable by editing
`migration/src/main/resources/logback.groovy`, documentation for which
is available in the [logback manual](http://logback.qos.ch/manual/groovy.html).

### Viewing Groovydoc

`gradle groovydoc` creates documentation in
`$PROJECT_ROOT/migrator-base/build/docs/groovydoc/index.html`.  Pay
particular attention to the
`org.kuali.ole.contrib.persistors.common.BibPersistorSQLBase` and
`org.kuali.ole.contrib.loaders.HoldingsPersistor` class comments, as
these document the basic usage and input formats.

## Future Development

This project contains stubs, half-finished ideas, and other non-working
code.  Feedback and especially pull requests/contributions are certainly
welcome.

Licensing issues prevent us from providing working examples of an ILS
migration.

The HTTP persistor stub was intended to be used with the existing
Docstore API, which reportedly has performance issues with large record
sets, so this implementation exists as a mere placeholder.

The JDBC persistor contains parts that have been tested and found to
work properly, but which were also much slower than the current "loader
file generator" strategy.   

For those who wish to implement the bulk of their data migration logic
in something other than Groovy or Java, there is a basic JSON parser
class which can churn a stream of JSON objects into source for Groovy
data structures suitable for use with the Persistor classes.  See
`org.kuali.ole.contrib.json.JsonStreamReader` and the README in the 
`migrator-base` subproject for more info.

## Notes

Despite the package names, this software is not an official part of the Kuali
OLE project, and all copyrights are retained by North Carolina State
University.  The software is provided under the GNU GPL v3 license.
