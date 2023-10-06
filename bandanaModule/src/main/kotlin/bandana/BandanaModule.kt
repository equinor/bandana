package bandana

import bandana.assembler.*
import java.util.function.Function
import org.apache.jena.fuseki.access.AccessCtl_GSP_R
import org.apache.jena.fuseki.access.AccessCtl_SPARQL_QueryDataset
import org.apache.jena.fuseki.access.DatasetGraphAccessControl
import org.apache.jena.fuseki.main.FusekiServer
import org.apache.jena.fuseki.main.sys.FusekiAutoModule
import org.apache.jena.fuseki.server.DataAccessPointRegistry
import org.apache.jena.fuseki.server.Endpoint
import org.apache.jena.fuseki.server.Operation
import org.apache.jena.fuseki.servlets.*
import org.apache.jena.rdf.model.Model

class BandanaModule : FusekiAutoModule {

    init {}

    override fun start() {
        println("The fuseki-plugin Bandana has started successfully")
    }

    override fun stop() {}
    override fun configured(
            builder: FusekiServer.Builder,
            dapRegistry: DataAccessPointRegistry,
            configModel: Model?
    ) {
        // Find fuseki:Server node of config.ttl (might be null)
        val serverNode = configModel?.let{getServer(configModel)}
        // Apply server-wide auth-filter if configured
        val serverFilter =
                serverNode?.let(::getAuth)?.also {
                    builder.addFilter("*", it)
                    if (it is AuthenticationFilter) builder.auth(it.authScheme)
                }

        dapRegistry.accessPoints().forEach { accessPoint ->
            // Find fuseki:Service node with fuseki:name = ap.name
            val serviceNode = configModel?.let{getService(accessPoint.name, serverNode, configModel)}
            // Apply service-wide auth-filter if configured
            val serviceFilter =
                    serviceNode?.let(::getAuth)?.also {
                        builder.addFilter("${accessPoint.name}/*", it)
                        if (it is AuthenticationFilter) builder.auth(it.authScheme)
                    }

            val service = accessPoint.getDataService()
            var authAttrKey: String? = null
            (service.getDataset() as? DatasetGraphAccessControl)?.let { dataset ->
                (dataset.getAuthService() as? RoleRegistry)?.let { roleRegistry ->
                    // Inject dataset, not directly doable from AssemblerRole-
                    // Registry due to service injection
                    roleRegistry.setDataset(dataset)
                    // pass configured claimsPath to any serviceFilter/serverFilter
                    (serverFilter as? AuthorizationProvider)?.apply {
                        authAttrKey = roleRegistry.authAttrKey
                    }
                    (serviceFilter as? AuthorizationProvider)?.apply {
                        authAttrKey = roleRegistry.authAttrKey
                    }

                    // pass configured claimsPath along to lifecycle hook
                    authAttrKey = roleRegistry.authAttrKey
                }
            }
            service.forEachEndpoint { endpoint ->
                // Find fuseki:Endpoint node with fuseki:name = ep.name and
                // resolve any bandana:authentication configuration
                val endpointFilter =
                        serviceNode?.let { getEndpoint(endpoint.name, it) }?.let(::getAuth)
                endpointFilter?.also {
                    builder.addFilter("${accessPoint.name}/${endpoint.name}/*", it)
                }

                // inject claimsPath from authorization config {@link RoleRegistry}
                // into authentication filter if it is an {@link AuthorizationProvider}.
                // This controls
                if (authAttrKey != null && endpointFilter is AuthorizationProvider) {
                    endpointFilter.authAttrKey = authAttrKey ?: throw IllegalStateException()
                }
            }
        }
    }

    override fun serverAfterStarting(server: FusekiServer) {

        // This configuration would ideally be called in the previous lifecycle
        // hook {@link configured}, but the implementation has a non-overridable
        // call to {@link FusekiLib.modifyForAccessCtl} which runs _after_
        // {@link configured} in the event that {@link DataService.dataset} is a
        // {@link DatasetGraphAccessControl} (which Bandana relies on!).
        val dapRegistry = DataAccessPointRegistry.get(server.servletContext)
        dapRegistry.accessPoints().forEach { accessPoint ->
            val service = accessPoint.getDataService()
            (service.getDataset() as? DatasetGraphAccessControl)?.let { dataset ->
                (dataset.getAuthService() as? RoleRegistry)?.let { roleRegistry ->
                    val path = roleRegistry.authAttrKey
                    val getRoles = { a: HttpAction -> a.getRequest().getAttribute(path) as String }
                    val writeRoles = roleRegistry.getWriteRoles()
                    service.forEachEndpoint { endpoint ->
                        modifyEndpointForAccessControl(endpoint, writeRoles, getRoles)
                    }
                }
            }
        }
    }

    override fun name() = "Bandana"
}

class BandanaAccessCtl_RequireRole(
        other: ActionService,
        allowedRoles: Set<String>,
        determineUser: Function<HttpAction, String>
) : ActionService() {
    private val other = other
    private val determineUser = determineUser
    private val allowedRoles = allowedRoles

    override fun validate(action: HttpAction) {
        val roles = determineUser.apply(action)
        val denied = roles.split('\n').intersect(allowedRoles).isEmpty()
        if (denied) {
            ServletOps.errorForbidden()
        } else {
            other.validate(action)
        }
    }

    override fun execute(action: HttpAction) {
        other.execute(action)
    }

    override fun execAny(method: String, action: HttpAction) {
        executeLifecycle(action)
    }
}

fun modifyEndpointForAccessControl(
        endpoint: Endpoint,
        writeRoles: Set<String>,
        determineUser: Function<HttpAction, String>
) {
    val actionService =
            when (endpoint.getOperation()) {
                Operation.Query -> AccessCtl_SPARQL_QueryDataset(determineUser)
                Operation.GSP_R -> AccessCtl_GSP_R(determineUser)
                Operation.GSP_RW ->
                        BandanaAccessCtl_RequireRole(GSP_RW(), writeRoles, determineUser)
                Operation.Update ->
                        BandanaAccessCtl_RequireRole(SPARQL_Update(), writeRoles, determineUser)
                Operation.Upload ->
                        BandanaAccessCtl_RequireRole(UploadRDF(), writeRoles, determineUser)
                else ->
                        throw Exception(
                                "Access control not implemented for " + endpoint.getOperation()
                        )
            }
    endpoint.setProcessor(actionService)
}
