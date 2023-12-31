package bandana

import org.apache.jena.fuseki.access.DataAccessCtl
import org.apache.jena.fuseki.main.FusekiServer
import org.apache.jena.fuseki.main.sys.FusekiModules
import org.apache.jena.fuseki.server.DataService
import org.apache.jena.fuseki.server.Operation
import org.apache.jena.fuseki.system.FusekiLogging
import org.apache.jena.graph.Graph
import org.apache.jena.http.HttpEnv
import org.apache.jena.rdfconnection.RDFConnection
import org.apache.jena.sparql.core.DatasetGraph
import org.apache.jena.sparql.core.DatasetGraphFactory
import org.apache.jena.sparql.exec.http.QueryExecHTTP
import org.apache.jena.sys.JenaSystem
import org.apache.jena.tdb2.sys.TDBInternal
import org.apache.jena.tdb2.DatabaseMgr
import org.junit.Assert
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.lang.IllegalStateException
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test

const val READ_ROLE = "#"
const val WRITE_ROLE = "*"
const val BANDANA_SERVICE_NAME = "bandana"
const val FUSEKI_SERVICE_NAME = "fuseki"


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
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


private const val s1 = "https://rdf.equinor.com/test/facility/dugtrio"

private const val s2 = "CONSTRUCT {?s ?p ?o} FROM <https://host/g3> WHERE {?s ?p ?o}"

private const val s3 = "CONSTRUCT {?s ?p ?o} WHERE {GRAPH <urn:x-arg:UnionGraph> {?s ?p ?o}}"

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
            it.addReadRole(READ_ROLE, ScopedSecurity)
            it.addWriteRole(WRITE_ROLE)
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
                        .add(BANDANA_SERVICE_NAME,
                                DataService.newBuilder(access_ds)
                                        .addEndpoint(Operation.Query, "query")
                                        .addEndpoint(Operation.Update, "update")
                        )
                        .add(FUSEKI_SERVICE_NAME,
                                DataService.newBuilder(ds)
                                        .addEndpoint(Operation.Query, "query"))
                        .addFilter("/$BANDANA_SERVICE_NAME/query/*", HeaderAuthProvider())
                        .addFilter("/$BANDANA_SERVICE_NAME/update/*", QueryAuthProvider())
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
        :g4 rec:isSubRecordOf :g3 ;
            rec:isInScope <discipline/IT> .
        _:a :in :g4 .
    }
    GRAPH :g3 {
        :g3 rec:isSubRecordOf :g2 ;
            rec:isInScope <contract/1234567890> .
        _:a :in :g3 .
        }
    GRAPH :g2 {
        :g2 rec:isSubRecordOf :g1 ;
        rec:isInScope <discipline/admin> .
        _:a :in :g2 .
    }
    GRAPH :g1 {
        :g1 rec:isInScope <facility/dugtrio> .
        _:a :in :g1 .
    }
}
"""
        val plainUrl = server().datasetURL("$BANDANA_SERVICE_NAME/update?Scope=$WRITE_ROLE")
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
        val checkurl = server().datasetURL("$BANDANA_SERVICE_NAME/update?Scope=$WRITE_ROLE")
        RDFConnection.connect(checkurl).use {
            it.update("DELETE WHERE {GRAPH ?g {?s ?p ?o}}")
        }
        println("closing ds")
        ds.close()
        println("stopping server")
        server().stop();
    }


    @ParameterizedTest(name= "{index} => count {0} {1} expect {2}")
    @CsvSource(delimiter=',',textBlock="""
                                 , GRAPH <urn:x-arq:UnionGraph>     , 13
                                 , GRAPH ?var                       , 13
FROM <urn:x-arq:UnionGraph>      ,                                  , 13
FROM <urn:x-arq:UnionGraph>      , GRAPH <urn:x-arq:UnionGraph>     , 0
FROM <urn:x-arq:UnionGraph>      , GRAPH ?var                       , 0
FROM <urn:x-arq:UnionGraph>      , GRAPH <urn:x-arq:DefaultGraph>   , 13
FROM NAMED <urn:x-arq:UnionGraph>,                                  , 0
FROM NAMED <urn:x-arq:UnionGraph>, GRAPH <urn:x-arq:UnionGraph>     , 0
FROM NAMED <urn:x-arq:UnionGraph>, GRAPH ?var                       , 0
                                 , GRAPH <https://host/g3>          , 3
FROM <urn:x-arq:UnionGraph>      , GRAPH <https://host/g3>          , 0
FROM NAMED <urn:x-arq:UnionGraph>, GRAPH <https://host/g3>          , 0
FROM <https://host/g3>           ,                                  , 3
FROM <https://host/g3>           , GRAPH <urn:x-arq:UnionGraph>     , 0
FROM <https://host/g3>           , GRAPH <urn:x-arq:DefaultGraph>   , 3
FROM <https://host/g3>           , GRAPH ?var                       , 0
FROM <https://host/g3>           , GRAPH <https://host/g3>          , 0
FROM NAMED <https://host/g3>     ,                                  , 0
FROM NAMED <https://host/g3>     , GRAPH <urn:x-arq:UnionGraph>     , 3
FROM NAMED <https://host/g3>     , GRAPH ?var                       , 3
FROM NAMED <https://host/g3>     , GRAPH <https://host/g3>          , 3
""")
    fun `Unsecured FROM UnionGraph behavior`(from:String?, graph: String?, count: Int) {
        val query = StringBuilder().apply{
            append("CONSTRUCT {?s ?p ?o} ")
            if(from != null) append(from)
            append(" WHERE ")
            if(graph != null) append(" { $graph")
            append("{ ?s ?p ?o }")
            if(graph != null) append(" }")
        }.toString()
        val g = constructQueryGraph(FUSEKI_SERVICE_NAME,query);
        println("$from $graph: ${g.size()}")
        Assert.assertEquals(count, g.size())
    }
    @ParameterizedTest(name= "<{2} {3}> count {0} {1} expect {4}")
    @CsvSource(delimiter=',',textBlock="""
                                 , GRAPH <urn:x-arq:UnionGraph>     , facility/dugtrio      ,   , 13
                                 , GRAPH ?var                       , facility/dugtrio      ,   , 13
FROM <urn:x-arq:UnionGraph>      ,                                  , facility/dugtrio      ,   , 13
FROM <urn:x-arq:UnionGraph>      , GRAPH <urn:x-arq:DefaultGraph>   , facility/dugtrio      ,   , 13
                                 , GRAPH <https://host/g3>          , facility/dugtrio      ,   , 3
FROM <https://host/g3>           ,                                  , facility/dugtrio      ,   , 3
FROM <https://host/g3>           , GRAPH <urn:x-arq:DefaultGraph>   , facility/dugtrio      ,   , 3
FROM NAMED <https://host/g3>     , GRAPH <urn:x-arq:UnionGraph>     , facility/dugtrio      ,   , 3
FROM NAMED <https://host/g3>     , GRAPH ?var                       , facility/dugtrio      ,   , 3
FROM NAMED <https://host/g3>     , GRAPH <https://host/g3>          , facility/dugtrio      ,   , 3
                                 , GRAPH <urn:x-arq:UnionGraph>     , contract/1234567890   ,   , 8
                                 , GRAPH ?var                       , contract/1234567890   ,   , 8
FROM <urn:x-arq:UnionGraph>      ,                                  , contract/1234567890   ,   , 8
FROM <urn:x-arq:UnionGraph>      , GRAPH <urn:x-arq:DefaultGraph>   , contract/1234567890   ,   , 8
                                 , GRAPH <https://host/g3>          , contract/1234567890   ,   , 3
FROM <https://host/g3>           ,                                  , contract/1234567890   ,   , 3
FROM <https://host/g3>           , GRAPH <urn:x-arq:DefaultGraph>   , contract/1234567890   ,   , 3
FROM NAMED <https://host/g3>     , GRAPH <urn:x-arq:UnionGraph>     , contract/1234567890   ,   , 3
FROM NAMED <https://host/g3>     , GRAPH ?var                       , contract/1234567890   ,   , 3
FROM NAMED <https://host/g3>     , GRAPH <https://host/g3>          , contract/1234567890   ,   , 3
                                 , GRAPH <https://host/g2>          , contract/1234567890   ,   , 0
FROM <https://host/g2>           ,                                  , contract/1234567890   ,   , 0
FROM <https://host/g2>           , GRAPH <urn:x-arq:DefaultGraph>   , contract/1234567890   ,   , 0
FROM NAMED <https://host/g2>     , GRAPH <urn:x-arq:UnionGraph>     , contract/1234567890   ,   , 0
FROM NAMED <https://host/g2>     , GRAPH ?var                       , contract/1234567890   ,   , 0
FROM NAMED <https://host/g2>     , GRAPH <https://host/g2>          , contract/1234567890   ,   , 0
                                 , GRAPH <urn:x-arq:UnionGraph>     , facility/dugtrio      , discipline/IT  , 5
                                 , GRAPH ?var                       , facility/dugtrio      , discipline/IT  , 5
FROM <urn:x-arq:UnionGraph>      ,                                  , facility/dugtrio      , discipline/IT  , 5
FROM <urn:x-arq:UnionGraph>      , GRAPH <urn:x-arq:DefaultGraph>   , facility/dugtrio      , discipline/IT  , 5
                                 , GRAPH <https://host/g3>          , facility/dugtrio      , discipline/IT  , 0
FROM <https://host/g3>           ,                                  , facility/dugtrio      , discipline/IT  , 0
FROM <https://host/g3>           , GRAPH <urn:x-arq:DefaultGraph>   , facility/dugtrio      , discipline/IT  , 0
FROM NAMED <https://host/g3>     , GRAPH <urn:x-arq:UnionGraph>     , facility/dugtrio      , discipline/IT  , 0
FROM NAMED <https://host/g3>     , GRAPH ?var                       , facility/dugtrio      , discipline/IT  , 0
FROM NAMED <https://host/g3>     , GRAPH <https://host/g3>          , facility/dugtrio      , discipline/IT  , 0
                                 , GRAPH <urn:x-arq:UnionGraph>     , contract/1234567890   , discipline/IT  , 5
                                 , GRAPH ?var                       , contract/1234567890   , discipline/IT  , 5
FROM <urn:x-arq:UnionGraph>      ,                                  , contract/1234567890   , discipline/IT  , 5
FROM <urn:x-arq:UnionGraph>      , GRAPH <urn:x-arq:DefaultGraph>   , contract/1234567890   , discipline/IT  , 5
                                 , GRAPH <https://host/g3>          , contract/1234567890   , discipline/IT  , 0
FROM <https://host/g3>           ,                                  , contract/1234567890   , discipline/IT  , 0
FROM <https://host/g3>           , GRAPH <urn:x-arq:DefaultGraph>   , contract/1234567890   , discipline/IT  , 0
FROM NAMED <https://host/g3>     , GRAPH <urn:x-arq:UnionGraph>     , contract/1234567890   , discipline/IT  , 0
FROM NAMED <https://host/g3>     , GRAPH ?var                       , contract/1234567890   , discipline/IT  , 0
FROM NAMED <https://host/g3>     , GRAPH <https://host/g3>          , contract/1234567890   , discipline/IT  , 0
                                 , GRAPH <https://host/g2>          , contract/1234567890   , discipline/IT  , 0
FROM <https://host/g2>           ,                                  , contract/1234567890   , discipline/IT  , 0
FROM <https://host/g2>           , GRAPH <urn:x-arq:DefaultGraph>   , contract/1234567890   , discipline/IT  , 0
FROM NAMED <https://host/g2>     , GRAPH <urn:x-arq:UnionGraph>     , contract/1234567890   , discipline/IT  , 0
FROM NAMED <https://host/g2>     , GRAPH ?var                       , contract/1234567890   , discipline/IT  , 0
FROM NAMED <https://host/g2>     , GRAPH <https://host/g2>          , contract/1234567890   , discipline/IT  , 0
""")
    fun `Secured FROM UnionGraph behavior`(from:String?, graph: String?, scope1: String, scope2: String?, count: Int) {
        val query = StringBuilder().apply{
            append("CONSTRUCT {?s ?p ?o} ")
            if(from != null) append(from)
            append(" WHERE ")
            if(graph != null) append(" { $graph")
            append("{ ?s ?p ?o }")
            if(graph != null) append(" }")
        }.toString()
        val g = if(scope2 == null) constructQueryGraph(BANDANA_SERVICE_NAME,query, READ_ROLE, "https://rdf.equinor.com/test/$scope1")
                else constructQueryGraph(BANDANA_SERVICE_NAME,query, READ_ROLE, "https://rdf.equinor.com/test/$scope1;https://rdf.equinor.com/test/$scope2")
        println("<$scope1 ${scope2 ?: ""}>${from ?: ""} ${graph?:""}: ${g.size()}")
        Assert.assertEquals(count, g.size())
    }
    @Test
    fun testSelectFrom() {
        queryWithScopes("CONSTRUCT {?s ?p ?o} FROM <https://host/g3> WHERE {?s ?p ?o}", "#", "https://rdf.equinor.com/test/facility/dugtrio")
    }

    @Test
    fun testSelectFromUnion() {
        queryWithScopes("CONSTRUCT {?s ?p ?o} FROM <urn:x-arq:UnionGraph> WHERE {?s ?p ?o}", "#", "https://rdf.equinor.com/test/contract/1234567890")
    }

    @Test
    fun testSelectFromNamedUnion() {
        queryWithScopes("CONSTRUCT {?s ?p ?o} FROM NAMED <urn:x-arq:UnionGraph> WHERE {GRAPH <urn:x-arq:UnionGraph> {?s ?p ?o} }", "#", "https://rdf.equinor.com/test/contract/1234567890")
    }

    @Test
    fun testSelectFromGraphUnion() {
        queryWithScopes("CONSTRUCT {?s ?p ?o} WHERE {GRAPH <urn:x-arq:UnionGraph> {?s ?p ?o} }", "#", "https://rdf.equinor.com/test/contract/1234567890")
    }

    @Test
    fun testSelectFromG2NoAccess() {
        queryWithScopes("CONSTRUCT {?s ?p ?o} FROM <https://host/g2> WHERE {?s ?p ?o}", "#", "https://rdf.equinor.com/test/contract/1234567890")
    }

    @Test
    fun testSelectFromG2WithAccess() {
        queryWithScopes("CONSTRUCT {?s ?p ?o} FROM <https://host/g2> WHERE {?s ?p ?o}", "#", "https://rdf.equinor.com/test/facility/dugtrio")
    }

    private fun constructQueryGraph(ds: String, query: String, vararg scopes: String): Graph {
        QueryExecHTTP.newBuilder()
                .endpoint(server().datasetURL("$ds/query"))
                .queryString(query)
                .httpHeader("Scope", scopes.joinToString(",") )
                .postQuery()
                .build()
                .use {
                    return it.construct()
                }
    }

    private fun queryWithScopes(query: String, vararg scopes: String) {

        var url = server().datasetURL("$BANDANA_SERVICE_NAME/query")


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


