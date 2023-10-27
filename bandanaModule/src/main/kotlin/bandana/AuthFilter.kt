package bandana

import bandana.vocab.*
import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.http.HttpFilter
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.apache.jena.atlas.web.AuthScheme

interface AuthenticationFilter : Filter {
    val authScheme: AuthScheme
}

interface AuthorizationProvider {
    var authAttrKey: String
}

class SpineAuthFilter(_scopeHeaderKey: String? = null) : HttpFilter(), AuthorizationProvider {

    private val scopeParamKey = _scopeHeaderKey ?: DEFAULT_SCOPE_KEY
    override var authAttrKey: String = DEFAULT_AUTH_ATTR_KEY


    override fun doFilter(req: HttpServletRequest, res: HttpServletResponse, ch: FilterChain) {
        //Get token
        // Get enough info form token to call spine-auth
        // Get upn scope from token

        val authheader = arrayOf("scope1", "scope2")

        // Add scopes to header
        // Newline-separated string of tab-separated scope-names (tsv)
        // First line is a tab-separated list of roles (read or write)
        // See also RoleRegistry.get
        req.setAttribute(authAttrKey, authheader.toList().joinToString("\n"))
        ch.doFilter(req, res)
    }
}


class JWTBearerFilter(_claimKey: String? = null) :
        HttpFilter(), AuthenticationFilter, AuthorizationProvider {
    private final val _BEARER = "Bearer "
    override val authScheme = AuthScheme.BEARER
    override var authAttrKey = DEFAULT_AUTH_ATTR_KEY
    private val claimKey = _claimKey ?: DEFAULT_SCOPE_CLAIM_KEY

    override fun doFilter(req: HttpServletRequest, res: HttpServletResponse, ch: FilterChain) {
        val validator = JwtValidator.getInstance()
        val autheader = req.getHeader("Authorization")

        if (!autheader.startsWith(_BEARER))
                throw IllegalArgumentException(
                        "Invalid Auth Scheme: " +
                                autheader.substring(
                                        0,
                                        autheader.indexOf(" ").let {
                                            if (it < 0) autheader.length else it
                                        }
                                )
                )

        val token = autheader.substring(_BEARER.length)
        val claims = validator.validate(token)
        val roleClaims = claims.getStringArrayClaim(claimKey)

        req.setAttribute(authAttrKey, roleClaims.joinToString("\n"))
        ch.doFilter(req, res)
    }
}
class QueryAuthProvider(_scopeHeaderKey: String? = null) : HttpFilter(), AuthorizationProvider {

    private val scopeParamKey = _scopeHeaderKey ?: DEFAULT_SCOPE_KEY
    override var authAttrKey: String = DEFAULT_AUTH_ATTR_KEY

    override fun doFilter(req: HttpServletRequest, res: HttpServletResponse, ch: FilterChain) {
        val autheader = req.getParameterValues(scopeParamKey)

        req.setAttribute(authAttrKey, autheader.toList().joinToString("\n"))
        ch.doFilter(req, res)
    }
}
class HeaderAuthProvider(_scopeHeaderKey: String? = null) : HttpFilter(), AuthorizationProvider {

    private val scopeHeaderKey = _scopeHeaderKey ?: DEFAULT_SCOPE_KEY
    override var authAttrKey: String = DEFAULT_AUTH_ATTR_KEY

    override fun doFilter(req: HttpServletRequest, res: HttpServletResponse, ch: FilterChain) {
        val autheader = req.getHeaders(scopeHeaderKey).asSequence().flatMap{it.split(Regex(",\\s*"))}.map{it.replace(Regex(";\\s*"), "\t")}

        req.setAttribute(authAttrKey, autheader.toList().joinToString("\n"))
        ch.doFilter(req, res)
    }
}
