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
import org.apache.jena.fuseki.server.Endpoint;
import org.apache.jena.fuseki.servlets.*
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
import java.util.function.Function;


class BandanaModule : FusekiAutoModule {

    init {
    }

    override fun start() {
        println("The fuseki-plugin Bandana has started successfully")
    }

    override fun stop() {
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
            (service.getDataset() as? DatasetGraphAccessControl)?.let{ dataset ->
                (dataset.getAuthService() as? RoleRegistry)?.let{ registry ->
                    val writeRoles = registry.getWriteRoles();
                    service.forEachEndpoint {
                        modifyEndpointForAccessControl(it, writeRoles, ::getRoles)
                    }
                }
            }
        }
    }


    override fun name() = "Bandana"

    fun modifyEndpointForAccessControl(endpoint: Endpoint, writeRoles: Set<String>, determineUser: Function<HttpAction, String>) {
        val actionService = when (endpoint.getOperation()) {
            Operation.Query -> AccessCtl_SPARQL_QueryDataset(determineUser)
            Operation.GSP_R -> AccessCtl_GSP_R(determineUser)
            Operation.GSP_RW -> BandanaAccessCtl_RequireRole(GSP_RW(), writeRoles, determineUser);
            Operation.Update -> BandanaAccessCtl_RequireRole(SPARQL_Update(), writeRoles, determineUser);
            Operation.Upload -> BandanaAccessCtl_RequireRole(UploadRDF(), writeRoles, determineUser);
            else -> throw Exception("Access control not implemented for " + endpoint.getOperation())
        }
        endpoint.setProcessor(actionService);
    }


    fun getRoles(action: HttpAction): String {
        return action.getRequest().getAttribute("roles") as String
    }

    class BandanaAccessCtl_RequireRole(other: ActionService, allowedRoles: Set<String>, determineUser: Function<HttpAction, String>) : ActionService() {
        private val other = other;
        private val determineUser = determineUser;
        private val allowedRoles = allowedRoles;

        override fun validate(action: HttpAction) {
            val roles = determineUser.apply(action);
            val denied = roles.split('\n').intersect(allowedRoles).isEmpty();
            if (denied) {
                ServletOps.errorForbidden();
            } else {
                other.validate(action)
            }
        }

        override fun execute(action: HttpAction) {
            other.execute(action);
        }

        override fun execAny(method: String, action: HttpAction) {
            executeLifecycle(action)
        }
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

            req.setAttribute("roles", roleClaims.joinToString("\n"))
            ch.doFilter(req, res)
        }
    };

}