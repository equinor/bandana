package bandana

import kotlin.test.Test
import kotlin.test.assertTrue

import org.apache.jena.sys.JenaSystem
import org.apache.jena.fuseki.system.FusekiLogging
import org.apache.jena.fuseki.main.sys.FusekiModules
import org.apache.jena.fuseki.main.FusekiServer
import org.apache.jena.http.HttpEnv;
import org.apache.jena.rdfconnection.RDFConnection
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.URI
import java.io.File

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BandanaTest {

    var server : FusekiServer? = null
    fun server() = server?:throw NullPointerException()

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
        // Create server.
        println("creating server")
        server =
            FusekiServer.create()
                .port(0)
                .fusekiModules(fusekiModules)
                .parseConfigFile("config.ttl")
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
        val plainUrl = server().datasetURL("/plain/update");
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
        testQueryWithToken("CONSTRUCT {?s ?p ?o} FROM <https://host/g3> WHERE {?s ?p ?o} ")
    }
    private fun testQueryWithToken(query:String) {

        var port = server().getPort()


        // Client HTTP request: "PATCH /extra"
        println("reading token")
        val token = BandanaTest::class.java.classLoader.getResource("test.token").readText()
        
        println("got token")
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:"+port+"/secured/sparql"))
                .method("POST", BodyPublishers.ofString(query))
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/sparql-query")
                .header("Accept", "application/trig")
                .build();
        println("sending request")
        val res = HttpEnv.getDftHttpClient().send(request, BodyHandlers.ofString())
        println("RES: ${res.body().toString()}");


    }

    @AfterAll fun finish(){
        // cleanup, delete everything
        val checkurl = server().datasetURL("/plain/update")
        RDFConnection.connect(checkurl).use{
            it.update("DELETE WHERE {GRAPH ?g {?s ?p ?o}}")
        }
        server().stop();
    }
}


