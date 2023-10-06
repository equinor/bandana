package bandana

import org.apache.jena.fuseki.access.DataAccessCtl
import org.apache.jena.fuseki.main.FusekiServer
import org.apache.jena.fuseki.main.sys.FusekiModules
import org.apache.jena.fuseki.server.DataService
import org.apache.jena.fuseki.server.Operation
import org.apache.jena.fuseki.system.FusekiLogging
import org.apache.jena.http.HttpEnv
import org.apache.jena.rdfconnection.RDFConnection
import org.apache.jena.sparql.core.DatasetGraphFactory
import org.apache.jena.sys.JenaSystem
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import kotlin.test.Test

// class InMemoryTests : BandanaTestBase(){
//     override fun getDataset() = DatasetGraphFactory.createTxnMem()
// }
const val TEST_READ_ROLE = "#"
const val TEST_WRITE_ROLE = "*"
const val TEST_DATASERVICE_NAME = "ds"
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BandanaTest {
// abstract class BandanaTestBase {

    protected var server : FusekiServer? = null
    protected fun server() = server?:throw NullPointerException()

    fun getDataset() = DatasetGraphFactory.createTxnMem()
    // abstract fun getDataset() : DatasetGraph

    @BeforeAll fun init(){
        JenaSystem.init();
        FusekiLogging.setLogging();
    
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
        val reg = RoleRegistry().also{
            it.addReadRole(TEST_READ_ROLE, ScopedSecurity)
            it.addWriteRole(TEST_WRITE_ROLE)
        }

        // create access controlled dataset
        val ds = getDataset()
        val access_ds = DataAccessCtl.controlledDataset(ds,reg)

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
        RDFConnection.connect(plainUrl).use{
            println("inserting data:\n$insert")
            try{
                it.update(insert);

            }catch(e:Exception){
                println("EXCEPTION: $e")
            }
        }
        println("inserted data")
    }

    
    // @Test fun testScopeAccess() {
    //     testQueryWithToken("CONSTRUCT {GRAPH ?g {?s ?p ?o} } WHERE { GRAPH ?g {?s ?p ?o}}")
    // }

    @Test fun testSelectFrom() {
        queryWithScopes("CONSTRUCT {?s ?p ?o} FROM <https://host/g3> WHERE {?s ?p ?o}","#", "https://rdf.equinor.com/test/facility/dugtrio" )
    }
    private fun queryWithScopes(query:String, vararg scopes: String) {

        var url = server().datasetURL("$TEST_DATASERVICE_NAME/query")


        // Client HTTP request: "PATCH /extra"
        println("reading token")
        
        println("got token")
        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .method("POST", BodyPublishers.ofString(query))
                .header("Content-Type", "application/sparql-query")
                .header("Accept", "text/turtle")
                .headers(*sequence{for(s in scopes){yieldAll(listOf("Scope", s))}}.toList().toTypedArray())
                .build();
        println("sending request")
        val res = HttpEnv.getDftHttpClient().send(request, BodyHandlers.ofString())
        println("RES: ${res.body().toString()}");


    }

    @AfterAll fun finish(){
        // cleanup, delete everything
        val checkurl = server().datasetURL("$TEST_DATASERVICE_NAME/update?Scope=$TEST_WRITE_ROLE")
        RDFConnection.connect(checkurl).use{
            it.update("DELETE WHERE {GRAPH ?g {?s ?p ?o}}")
        }
        server().stop();
    }
}


