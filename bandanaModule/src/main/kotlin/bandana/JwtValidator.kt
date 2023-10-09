package bandana;

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import java.net.URL

/**
 * JWS validation of supplied JWT
 * 
 * ALWAYS validates JWS with keys resolved from `jwksUrl`.
 * ALWAYS validates `issuer` and `audience` against configured values (exact
 * match).
 * CONFIGURABLE set of required claims (claim must be present, content not
 * checked), may be ∅.
 * 
 * CAUTION: Implemented as SINGLETON Due to caching of resolved JWKs (and to
 * save time). Only 1 JWS/JWT configuration pr. running instance is therefore
 * supported.
 */
class JwtValidator private constructor() {

    private val jwtProcessor: ConfigurableJWTProcessor<SecurityContext>

    init {
        jwtProcessor = DefaultJWTProcessor<SecurityContext>()

        if(jwksUrl == null) throw IllegalStateException("`jwksUrl` not set!")
        if(audience == null) throw IllegalStateException("`audience` not set!")
        if(issuer == null) throw IllegalStateException("`issuer` not set!")
        if(requiredClaims == null) requiredClaims = HashSet<String>()

        val validClaims = JWTClaimsSet.Builder()
        .issuer(issuer)
        .audience(audience)
        .build()

        jwtProcessor.setJWTClaimsSetVerifier(DefaultJWTClaimsVerifier(validClaims, requiredClaims))
        
        val jwkSetURL = URL(jwksUrl)
        val jwkSource = JWKSourceBuilder
            .create<SecurityContext>(jwkSetURL)
            .retrying(true)
            .build();

        // The expected JWS algorithm of the access tokens (agreed out-of-band)
        val expectedJWSAlg = JWSAlgorithm.RS256;

        // Configure the JWT processor with a key selector to feed matching public
        // RSA keys sourced from the JWK set URL
        val keySelector = JWSVerificationKeySelector<SecurityContext>(
            expectedJWSAlg,
            jwkSource);
        jwtProcessor.setJWSKeySelector(keySelector);

    }
    
    fun validate(token: String): JWTClaimsSet {
        // Process the token
        val ctx:SecurityContext? = null; // optional context parameter, not required here
        val claimsSet: JWTClaimsSet;
        claimsSet = jwtProcessor.process(token, ctx);
        return claimsSet;
    }

    companion object {
        // var jwksUrl = "https://login.microsoftonline.com/common/discovery/keys"
        private var jwksUrl: String? = null
        fun setJwksUrl(url: String){
            if(url == jwksUrl) return
            if(INSTANCE != null || jwksUrl != null) throw IllegalStateException("jwksUrl may not be changed after configuration.")
            jwksUrl = url
        }

        private var issuer: String? = null
        fun setIssuer(url: String){
            if(url == issuer) return
            if(INSTANCE != null || issuer != null) throw IllegalStateException("issuer may not be changed after configuration.")
            issuer = url
        }

        private var audience: String? = null
        fun setAudience(url: String){
            if(url == audience) return
            if(INSTANCE != null || audience != null) throw IllegalStateException("audience may not be changed after configuration.")
            audience = url
        }

        private var requiredClaims: Set<String>? = null
        fun setRequiredClaims(set: Set<String>){
            if(set.equals(requiredClaims)) return
            if(INSTANCE != null || audience != null) throw IllegalStateException("requiredClaims may not be changed after configuration.")
            requiredClaims = set

        }

        @Volatile
        private var INSTANCE: JwtValidator? = null

        fun getInstance(): JwtValidator {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: JwtValidator().also {
                    INSTANCE = it
                }
            }
        }
    }
}