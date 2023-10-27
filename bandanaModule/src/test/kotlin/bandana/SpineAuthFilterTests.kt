package bandana

import org.apache.jena.fuseki.access.DatasetGraphAccessControl
import org.apache.jena.fuseki.main.FusekiServer
import org.apache.jena.fuseki.main.sys.FusekiModules
import org.apache.jena.fuseki.server.DataService
import org.apache.jena.fuseki.server.Operation
import org.apache.jena.fuseki.system.FusekiLogging
import org.apache.jena.http.HttpEnv
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdfconnection.RDFConnection
import org.apache.jena.riot.RDFLanguages
import org.apache.jena.riot.RDFParser
import org.apache.jena.riot.system.StreamRDFLib
import org.apache.jena.sparql.core.DatasetGraph
import org.apache.jena.sparql.exec.http.QueryExecHTTP
import org.apache.jena.sys.JenaSystem
import org.apache.jena.tdb2.DatabaseMgr
import org.eclipse.jetty.servlet.FilterHolder
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHandler
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.io.path.createTempDirectory
import kotlin.test.BeforeTest

class SpineAuthFilterTests {

    @Test
    fun testSelectFrom() {
        var location = createTempDirectory("BandanaTest_${System.currentTimeMillis() / 1000}")
        val ds = DatabaseMgr.connectDatasetGraph(location.toString())
        val server =
                FusekiServer.create()
                        .port(0) // eller hva du vil
                        // .fusekiModules(fusekiModules) // skakke teste dette enda, hehe
                        .add(FUSEKI_SERVICE_NAME,
                                DataService.newBuilder(ds)
                                        .addEndpoint(Operation.Query, "query")
                                        .addEndpoint(Operation.Update, "update"))
                        .addFilter("/$FUSEKI_SERVICE_NAME/query/*", SpineAuthFilter())
                        //.addFilter("/$FUSEKI_SERVICE_NAME/query/*", HeaderAuthProvider())
                        //.addFilter("/$BANDANA_SERVICE_NAME/update/*", QueryAuthProvider())
                        .verbose(true)
                        .build()
                        .start();

        // Legg inn noe data
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
        val plainUrl = server.datasetURL("$FUSEKI_SERVICE_NAME/update")
        RDFConnection.connect(plainUrl).use {
            println("inserting data:\n$insert")
            try {
                it.update(insert);

            } catch (e: Exception) {
                println("EXCEPTION: $e")
            }
        }
        // Qyery noe data

        val construct_query_string = StringBuilder().apply{
            append("CONSTRUCT {?s ?p ?o} ")
            append(" WHERE ")
            append("{ GRAPH ?g {?s ?p ?o} }")
        }.toString()
        QueryExecHTTP.newBuilder()
                .endpoint(server.datasetURL("$FUSEKI_SERVICE_NAME/query"))
                .queryString(construct_query_string)
                //.httpHeader("Scope", scopes.joinToString(",") ) //trenger ikke dette enda
                .postQuery()
                .build()
                .use {
                    val rdf = it.construct()
                    println("Returned $rdf")
                }



    }

}