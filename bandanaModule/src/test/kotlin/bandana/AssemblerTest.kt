package bandana

import org.apache.jena.fuseki.access.DatasetGraphAccessControl
import org.apache.jena.fuseki.main.FusekiServer
import org.apache.jena.fuseki.main.sys.FusekiModules
import org.apache.jena.fuseki.system.FusekiLogging
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.RDFLanguages
import org.apache.jena.riot.RDFParser
import org.apache.jena.riot.system.StreamRDFLib
import org.apache.jena.sys.JenaSystem
import org.eclipse.jetty.servlet.FilterHolder
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHandler
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.BeforeTest


class AssemblerTest {

    lateinit var builder: FusekiServer.Builder

    @BeforeTest
    fun init() {
        JenaSystem.init();
        FusekiLogging.setLogging();

        var module = BandanaModule();
        var fusekiModules = FusekiModules.create(module);


        // Create server.
        println("creating server builder ")
        builder =
                FusekiServer.create()
                        .port(0)
                        .fusekiModules(fusekiModules)
                        .verbose(true)
    }

    @ParameterizedTest
    @ValueSource(strings = [":service", ":server", ":sparql"])
    fun `config authentication location test`(loc: String) {
        val config = servicettl +
                """
            :service fuseki:dataset [ rdf:type ja:MemoryDataset ] .
            $loc bandana:authentication [rdf:type bandana:QueryAuthProvider ] .
            """.trimIndent()
        builder.parseConfigString(config)
        val server = builder.build()
        Assertions.assertTrue(server.jettyServer.handlers.any { sh ->
            sh is ServletContextHandler && sh.handlers.any { h ->
                h is ServletHandler && h.filters.any { f ->
                    f is FilterHolder && f.heldClass == QueryAuthProvider::class.java
                }
            }
        }, "Configured authorization provider should be in the list of configured HttpFilters.")
    }

    @Test
    fun `config authorization registry test`() {
        val config = servicettl +
                """
            :service fuseki:dataset
              [ a access:AccessControlledDataset ;
                access:dataset [ a ja:MemoryDataset ] ;
                access:registry 
                  [ a bandana:RoleRegistry ;
                    bandana:entry [ bandana:role "query" ; bandana:access bandana:ScopeAuthorization ] ;
                    bandana:alias ("role.example1" <https://example.com/someScope>) ] ] .
            """.trimIndent()
        builder.parseConfigString(config)
        val server = builder.build()
        var hasRoleRegistry = false
        server.dataAccessPointRegistry.forEach { _, dap ->
            (dap.dataService.dataset as? DatasetGraphAccessControl)?.apply {
                if (authService is RoleRegistry) hasRoleRegistry = true
            }
        }
        Assertions.assertTrue(hasRoleRegistry, "Authorization Service should be a RoleRegistry")
    }


}

private fun FusekiServer.Builder.parseConfigString(config: String) {
    val configModel = ModelFactory.createDefaultModel()
    RDFParser.fromString(config)
            .lang(RDFLanguages.TURTLE)
            .parse(StreamRDFLib.graph(configModel.graph))
    this.parseConfig(configModel)
}


internal const val servicettl =
        """
## Licensed under the terms of http://www.apache.org/licenses/LICENSE-2.0

@prefix :        <#> .
@prefix fuseki:  <http://jena.apache.org/fuseki#> .
@prefix rdf:     <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#> .
@prefix ja:      <http://jena.hpl.hp.com/2005/11/Assembler#> .
@prefix access:  <http://jena.apache.org/access#> .
@prefix bandana:  <http://rdf.equinor.com/ontology/bandana#> .
@prefix tdb2:    <http://jena.apache.org/2016/tdb#> .


[] ja:loadClass "bandana.AssemblerRoleRegistry", "bandana.AssemblerQueryAuthProvider".

:server rdf:type fuseki:Server ;
    fuseki:services (
        :service
    ) .
         
:service rdf:type fuseki:Service ;
    fuseki:name "ds" ;
   
    fuseki:endpoint :sparql ;
    fuseki:endpoint :update ;
    fuseki:endpoint :get ;
    fuseki:endpoint :data ;
    fuseki:endpoint :upload ;
    .

:sparql fuseki:operation fuseki:query ;
        fuseki:name "sparql" .

:update fuseki:operation fuseki:update ;
        fuseki:name "update" .
        
:get fuseki:operation fuseki:gsp-r ;
    fuseki:name "get" .
    
:data fuseki:operation fuseki:gsp-rw ;
      fuseki:name "data" .
      
:upload fuseki:operation fuseki:gsp-rw ;
        fuseki:name "data" .
"""
internal const val jwtfilterttl =
        """
:filter rdf:type bandana:JWTBearerFilter ;
    bandana:issuer "ISSUER HERE";
    bandana:audience "ISSUER HERE";
    bandana:jwkSetURL "JWKSetUrl HERE";
    bandana:requiredClaim "roles" .

"""

internal const val accessttl =
        """
:access_dataset  rdf:type access:AccessControlledDataset ;
    access:registry   :roleRegistry: ;
    access:dataset    :dataset ;
    .
"""
internal const val registryttl =
        """

:roleRegistry rdf:type bandana:RoleRegistry ;
    bandana:entry [ bandana:role "query"; bandana:access bandana:ScopeAuthorization ] ;
    bandana:entry [ bandana:role "write"; bandana:access bandana:WriteAuthorization ] ;

    bandana:alias ("role.example1" <https://example.com/someScope>) ;
    .
"""
internal const val tdbttl =
        """
:dataset rdf:type tdb2:DatasetTDB2 ;
    tdb2:location "tdb2" .

"""



