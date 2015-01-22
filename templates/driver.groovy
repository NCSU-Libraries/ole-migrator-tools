/**
 * You can edit this to create your driver script.
 * This sample uses the 'loader' persistors and a dummy data set
 * to create ... dummy output in the /tmp directory.
 */
import org.kuali.ole.contrib.ConfigLoader
import groovyx.gpars.group.DefaultPGroup
import groovyx.gpars.GParsPool
import groovyx.gpars.dataflow.SyncDataflowQueue
import groovyx.gpars.dataflow.operator.PoisonPill

def configLoader = new ConfigLoader("development")
def config = configLoader.loadConfig()

println config.message

def threadConfig = config.performance.threads ?: [ reader: 1, writer: 19 ]

// special message to our processors that tells them to shut down
def shutdownPill = PoisonPill.instance

def readerThreads = threadConfig.reader
def writerThreads = threadConfig.writer

final transformerGroup = new DefaultPGroup(readerThreads)

final writerGroup = new DefaultPGroup(writerThreads)

println "Created a transformer with ${readerThreads} threads"

def files = [ "a.txt", "b.txt", "c.txt" ].collect {
    new File("src/test/resource", it)
}

// You can think of this as your "input queue"; where you add new things to 
// be persisted.  Use of SyncDataflowQueue means that the process that reads
// will not be overwhelmed by "fast readers" -- everything will be accumulated
// and processed as fast as it can, without 
def recordQueue = new SyncDataflowQueue()

// the outputQueue is where records go before they are sent to a persistor.
// note this sample does not have a persistor, but just prints the output.
def outputQueue = new SyncDataflowQueue()

// this is the "main logic", where whatever your inputs are are transformed
// into the proper data structures.
def t =  transformerGroup.operator(
     inputs:[recordQueue],
      outputs:[outputQueue],
      maxForks:readerThreads) {
    record ->
        // in this case 'record' will just be a line from the file
        bindOutput 0, [message: record.toUpperCase()]
}

def w = writerGroup.operator(
     inputs:[outputQueue],
      outputs:[],
      maxForks:writerThreads) {
    msg ->
        println "Receieved: " + msg 
}



/**
 * Start a (multithreaded) task that reads data from multiple sources,
 * in this case files; each line read from each of the files will be put
   on the input queue.
**/

GParsPool.withPool(readerThreads) {
    files.each {
        File input ->
            if ( input.exists() ) {
                println "--- opened ${ input.name } ---"
                input.eachLine { line ->
                    recordQueue << line
                }
                println "--- finished ${ input.name } ---"
            } else {
                println " >>> ${input.name} not found."
            }
    }
}    
recordQueue << shutdownPill

t.join()
w.join()


