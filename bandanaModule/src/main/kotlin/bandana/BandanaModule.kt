package bandana;

import org.apache.jena.fuseki.main.sys.FusekiModule
import org.apache.jena.fuseki.main.sys.FusekiAutoModule
import org.apache.jena.fuseki.main.FusekiServer
import org.apache.jena.fuseki.server.DataAccessPoint
import org.apache.jena.fuseki.server.Operation
import org.apache.jena.fuseki.server.DataAccessPointRegistry
import org.apache.jena.fuseki.access.DatasetGraphAccessControl
import org.apache.jena.fuseki.access.AccessCtl_SPARQL_QueryDataset
import org.apache.jena.fuseki.access.AccessCtl_GSP_R
import org.apache.jena.fuseki.access.AccessCtl_Deny
import org.apache.jena.fuseki.servlets.HttpAction
import org.apache.jena.fuseki.servlets.ActionProcessor
import org.apache.jena.fuseki.main.FusekiLib
import org.apache.jena.graph.Node
import org.apache.jena.graph.NodeFactory
import org.apache.jena.rdf.model.Model
import org.apache.jena.query.Dataset
import org.apache.jena.atlas.web.AuthScheme
import org.apache.jena.vocabulary.RDF
import org.apache.jena.vocabulary.RDFS
import org.apache.jena.sparql.graph.GraphFactory
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFFormat
import org.apache.jena.riot.RDFWriter
import org.apache.jena.web.HttpSC
import javax.servlet.Filter
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpFilter
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.FilterChain
import bandana.AssemblerRoleRegistry
import kotlin.text.StringBuilder
import kotlin.Pair
import java.io.StringWriter


class BandanaModule : FusekiAutoModule {

    init {
        println("INSIDE BANDANA CONSTRUCTOR! PLEASE SEE THIS")
    }

    override fun start() {
        println("Bandana module starting!")
    }

    override fun stop() {
        println("Bandana module stopping!")
    }
    override fun configured(builder: FusekiServer.Builder, dapRegistry: DataAccessPointRegistry, configModel: Model) {
        dapRegistry.accessPoints().forEach{ap ->
            val service = ap.getDataService()
            (service.getDataset() as? DatasetGraphAccessControl)?.let{
                (it.getAuthService() as? RoleRegistry)?.let{
                    builder.addFilter("${ap.name}/*", BandanaFilter(it))
                    builder.auth(AuthScheme.BEARER)
                }
            }
        }
    }

    override fun serverAfterStarting(server: FusekiServer) {
        val dapRegistry = DataAccessPointRegistry.get(server.servletContext)
        dapRegistry.accessPoints().forEach{ap ->
            val service = ap.getDataService()
            (service.getDataset() as? DatasetGraphAccessControl)?.let{
                (it.getAuthService() as? RoleRegistry)?.let{

                    service.forEachEndpoint {
                        FusekiLib.modifyForAccessCtl(it, ::getRoles)
                    }
                }
            }
        }
        println("Bandana module started!")
    }


    override fun name() = "Bandana"



    fun getRoles(action: HttpAction): String {
        println("GETROLES")
        return action.getRequest().getAttribute("roles") as String
    }

    class BandanaFilter(registry: RoleRegistry) : HttpFilter() {

        private val _BEARER = "Bearer "
        private val reg = registry
        
        override fun doFilter(req: HttpServletRequest, res: HttpServletResponse, ch: FilterChain) { 
            val validator = JwtValidator.getInstance()
            val autheader = req.getHeader("Authorization")

            if(!autheader.startsWith(_BEARER))
                throw IllegalArgumentException("Invalid Auth Scheme: " + autheader.substring(0, autheader.indexOf(" ").let{if(it < 0) autheader.length else it}))

            val token = autheader.substring(_BEARER.length)
            val claims = validator.validate(token)
            val roleClaims = claims.getStringArrayClaim("roles")

            var anyRoles = false
            for(claim in roleClaims){
                if(reg.isRole(claim)) anyRoles = true
            }
            if(!anyRoles){
                res.sendError(HttpSC.FORBIDDEN_403);
                return
            }

            req.setAttribute("roles", roleClaims.joinToString("\n"))

            ch.doFilter(req, res)

        }

    };

}