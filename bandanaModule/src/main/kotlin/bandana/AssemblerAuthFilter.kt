package bandana

import bandana.HeaderAuthProvider
import bandana.JWTBearerFilter
import bandana.JwtValidator
import bandana.QueryAuthProvider
import bandana.vocab.*
import javax.servlet.http.HttpFilter
import org.apache.jena.assembler.Assembler
import org.apache.jena.assembler.Mode
import org.apache.jena.assembler.assemblers.AssemblerBase
import org.apache.jena.atlas.iterator.Iter
import org.apache.jena.atlas.web.AuthScheme
import org.apache.jena.fuseki.server.FusekiVocab
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.Resource
import org.apache.jena.sparql.util.MappingRegistry
import org.apache.jena.vocabulary.RDF

fun getServer(configModel: Model): Resource? {
    val servers = configModel.listResourcesWithProperty(RDF.type, FusekiVocab.tServer)
    if (!servers.hasNext()) return null
    return servers.nextResource()
}

fun getService(name: String, server: Resource?, configModel: Model): Resource? {
    var services =
            iterator i@{
                if (server == null) return@i
                props@ for (p in
                        server.listProperties(FusekiVocab.pServices).mapWith { it.getObject() }) {
                    if (p !is Resource) continue@props
                    try {
                        var list = server.getModel().getList(p)
                        while (!list.isEmpty()) {
                            val service = list.getHead() as Resource?
                            if (service != null) yield(service)
                            list = list.getTail()
                        }
                    } catch (e: Throwable) {
                        yield(p)
                    }
                }
            }
    if (!services.hasNext()) {
        services = configModel.listResourcesWithProperty(RDF.type, FusekiVocab.fusekiService)
    }
    services = Iter.filter(services) { s -> s.hasProperty(FusekiVocab.pServiceName, name) }
    if (!services.hasNext()) return null
    return services.next()
}

fun getEndpoint(name: String, service: Resource): Resource? {
    val endpoints =
            service.listProperties(FusekiVocab.pEndpoint)
                    .mapWith { it.getObject() as? Resource }
                    .filterKeep { it?.hasProperty(FusekiVocab.pEndpointName, name) ?: false }
    if (!endpoints.hasNext()) return null
    return endpoints.next()
}

fun getAuth(point: Resource): HttpFilter? {
    val auths = point.listProperties(pAuthentication)
    if (!auths.hasNext()) return null
    val auth = auths.nextStatement().getObject()
    return when (auth) {
        is Resource -> Assembler.general.open(auth) as HttpFilter
        else -> null
    }
    // return Assembler.general.open(auths.nextStatement().getObject() as? Resource) as HttpFilter
}

class AssemblerJWTBearerFilter() : AssemblerBase() {
    companion object {
        init {
            MappingRegistry.addPrefixMapping("bandana", ns)
            Assembler.general.implementWith(tJWTBearerFilter, AssemblerJWTBearerFilter())
        }
    }

    val authScheme = AuthScheme.BEARER

    override fun open(a: Assembler, root: Resource, mode: Mode): HttpFilter {
        val jwkSetURL =
                root.getRequiredProperty(pJwkSetURL)?.`object`?.asLiteral()?.getString()
                        ?: throw NullPointerException()
        JwtValidator.setJwksUrl(jwkSetURL)
        val audience =
                root.getRequiredProperty(pAudience)?.`object`?.asLiteral()?.getString()
                        ?: throw NullPointerException()
        JwtValidator.setAudience(audience)
        val issuer =
                root.getRequiredProperty(pIssuer)?.`object`?.asLiteral()?.getString()
                        ?: throw NullPointerException()
        JwtValidator.setIssuer(issuer)

        val requiredClaims = HashSet<String>()
        for (claim in
                root.listProperties(pRequiredClaim).mapWith {
                    it.`object`.asLiteral().getString()
                }) {
            requiredClaims.add(claim)
        }
        JwtValidator.setRequiredClaims(requiredClaims)

        val claimKey = root.getProperty(pScopeClaimKey)?.`object`?.asLiteral()?.getString()

        return JWTBearerFilter(claimKey)
    }
}

class AssemblerHeaderAuthProvider() : AssemblerBase() {
    companion object {
        init {
            MappingRegistry.addPrefixMapping("bandana", ns)
            Assembler.general.implementWith(tHeaderAuthProvider, AssemblerHeaderAuthProvider())
        }
    }

    override fun open(a: Assembler, root: Resource, mode: Mode): HttpFilter {
        val scopeHeaderKey = root.getProperty(pScopeKey)?.`object`?.asLiteral()?.getString()
        return HeaderAuthProvider(scopeHeaderKey)
    }
}
class AssemblerQueryAuthProvider() : AssemblerBase() {
    companion object {
        init {
            MappingRegistry.addPrefixMapping("bandana", ns)
            Assembler.general.implementWith(tQueryAuthProvider, AssemblerQueryAuthProvider())
        }
    }

    override fun open(a: Assembler, root: Resource, mode: Mode): HttpFilter {
        val scopeHeaderKey = root.getProperty(pScopeKey)?.`object`?.asLiteral()?.getString()
        return QueryAuthProvider(scopeHeaderKey)
    }
}
