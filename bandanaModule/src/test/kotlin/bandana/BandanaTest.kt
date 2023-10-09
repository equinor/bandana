package bandana

import org.apache.jena.fuseki.access.DataAccessCtl
import org.apache.jena.fuseki.main.FusekiServer
import org.apache.jena.fuseki.main.sys.FusekiModules
import org.apache.jena.fuseki.server.DataService
import org.apache.jena.fuseki.server.Operation
import org.apache.jena.fuseki.system.FusekiLogging
import org.apache.jena.http.HttpEnv
import org.apache.jena.rdfconnection.RDFConnection
import org.apache.jena.sparql.core.DatasetGraph
import org.apache.jena.sparql.core.DatasetGraphFactory
import org.apache.jena.sys.JenaSystem
import org.apache.jena.tdb2.sys.TDBInternal
import org.apache.jena.tdb2.DatabaseMgr
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import java.lang.IllegalStateException
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import kotlin.io.path.createTempDirectory
import kotlin.io.path.pathString
import kotlin.test.Test

const val TEST_READ_ROLE = "#"
const val TEST_WRITE_ROLE = "*"
const val TEST_DATASERVICE_NAME = "ds"


class InMemoryTests : BandanaTestBase() {
    override fun getDataset(): DatasetGraph = DatasetGraphFactory.createTxnMem()
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TDB2Tests : BandanaTestBase() {
    //        companion object {
//
//
//        @field:TempDir
//        lateinit var location: Path
//    }
//    @field:TempDir
    lateinit var location: Path
    lateinit var deletable: Path

    @BeforeAll
    override fun init() {
        location = createTempDirectory("BandanaTest_${System.currentTimeMillis() / 1000}")
        deletable = kotlin.io.path.createTempFile("deletable", ".test")
        super.init()
    }

    @AfterAll
    override fun finish() {
        super.finish()
        if (!deletable.toFile().delete()) throw IllegalStateException("Could not even delete temp file")
        TDBInternal.expel(ds, true)
        Thread.sleep(1000)
        deleteDirectory(location)
    }

    fun deleteDirectory(directory: Path) {
        Files.walk(directory)
                .sorted(Comparator.reverseOrder())
                .map { it.toFile() }
                .forEach {
                    it.delete()

                }
    }

    override fun getDataset(): DatasetGraph = DatabaseMgr.connectDatasetGraph(location.toString())
}


//class BandanaTest {
abstract class BandanaTestBase {
    protected var server: FusekiServer? = null
    protected fun server() = server ?: throw NullPointerException()

    lateinit var ds: DatasetGraph
    abstract fun getDataset(): DatasetGraph
    //var getDataset: () -> DatasetGraph

    @BeforeAll
    open fun init() {
        JenaSystem.init()
        FusekiLogging.setLogging()

        // Normally done with ServiceLoader
        // A file /META-INF/services/org.apache.jena.fuseki.main.sys.FusekiModule
        // in the jar file with contents:
        //    org.apache.jena.fuseki.main.examples.ExampleModule
        //
        // The file is typically put into the jar by having
        //   src/main/resources/META-INF/services/org.apache.jena.fuseki.main.sys.FusekiModule

        // For this example, we add the module directly.
        var module = BandanaModule();
        var fusekiModules = FusekiModules.create(module);

        // create authorization configuration
        val reg = RoleRegistry().also {
            it.addReadRole(TEST_READ_ROLE, ScopedSecurity)
            it.addWriteRole(TEST_WRITE_ROLE)
        }

        // create access controlled dataset
        ds = getDataset()
        val access_ds = DataAccessCtl.controlledDataset(ds, reg)

        // Create server.
        println("creating server")
        server =
                FusekiServer.create()
                        .port(0)
                        .fusekiModules(fusekiModules)
                        .add(TEST_DATASERVICE_NAME,
                                DataService.newBuilder(access_ds)
                                        .addEndpoint(Operation.Query, "query")
                                        .addEndpoint(Operation.Update, "update")
                        )
                        .addFilter("/$TEST_DATASERVICE_NAME/query/*", HeaderAuthProvider())
                        .addFilter("/$TEST_DATASERVICE_NAME/update/*", QueryAuthProvider())
                        .verbose(true)
                        .build()
                        .start();

        println("test server started")

        // Add some data to the database
        val insert =
                """
BASE <https://rdf.equinor.com/test/>
PREFIX : <https://host/>
PREFIX rec: <https://rdf.equinor.com/ontology/record/>
INSERT DATA {
    GRAPH :g5 {
        :g5 rec:isSubRecordOf :g4 .
        _:a :in :g5 .
    }
    GRAPH :g4 {
        :g4 rec:isSubRecordOf :g3 .
        _:a :in :g4 .
    }
    GRAPH :g3 {
        :g3 rec:isSubRecordOf :g2 ;
            rec:isInScope <contract/1234567890> .
        _:a :in :g3 .
        }
    GRAPH :g2 {
        :g2 rec:isSubRecordOf :g1 .
        _:a :in :g3 .
    }
    GRAPH :g1 {
        :g1 rec:isInScope <facility/dugtrio> .
        _:a :in :g1 .
    }
}
"""
        val plainUrl = server().datasetURL("$TEST_DATASERVICE_NAME/update?Scope=$TEST_WRITE_ROLE")
        RDFConnection.connect(plainUrl).use {
            println("inserting data:\n$insert")
            try {
                it.update(insert);

            } catch (e: Exception) {
                println("EXCEPTION: $e")
            }
        }
        println("inserted data")
    }


    @AfterAll
    open fun finish() {
        println("Cleanup after tests")
        // cleanup, delete everything
        val checkurl = server().datasetURL("$TEST_DATASERVICE_NAME/update?Scope=$TEST_WRITE_ROLE")
        RDFConnection.connect(checkurl).use {
            it.update("DELETE WHERE {GRAPH ?g {?s ?p ?o}}")
        }
        println("closing ds")
        ds.close()
        println("stopping server")
        server().stop();
    }


    // @Test fun testScopeAccess() {
    //     testQueryWithToken("CONSTRUCT {GRAPH ?g {?s ?p ?o} } WHERE { GRAPH ?g {?s ?p ?o}}")
    // }

    @Test
    fun testSelectFrom() {
        queryWithScopes("CONSTRUCT {?s ?p ?o} FROM <https://host/g3> WHERE {?s ?p ?o}", "#", "https://rdf.equinor.com/test/facility/dugtrio")
    }

    private fun queryWithScopes(query: String, vararg scopes: String) {

        var url = server().datasetURL("$TEST_DATASERVICE_NAME/query")


        // Client HTTP request: "PATCH /extra"
        println("reading token")

        println("got token")
        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .method("POST", BodyPublishers.ofString(query))
                .header("Content-Type", "application/sparql-query")
                .header("Accept", "text/turtle")
                .headers(*sequence {
                    for (s in scopes) {
                        yieldAll(listOf("Scope", s))
                    }
                }.toList().toTypedArray())
                .build();
        println("sending request")
        val res = HttpEnv.getDftHttpClient().send(request, BodyHandlers.ofString())
        println("RES: ${res.body().toString()}");


    }

}


