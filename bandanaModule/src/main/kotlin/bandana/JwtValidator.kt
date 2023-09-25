package bandana;

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jose.proc.SimpleSecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.JWSKeySelector
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jwt.proc.JWTProcessor
import java.net.URL

class JwtValidator private constructor() {
    private val jwtProcessor: ConfigurableJWTProcessor<SecurityContext>
    private final val jwksUrl = "https://login.microsoftonline.com/common/discovery/keys"

    init {
        jwtProcessor = DefaultJWTProcessor<SecurityContext>()

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
        val roles = claimsSet.getStringArrayClaim("roles")
        return claimsSet;
    }

    companion object {
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