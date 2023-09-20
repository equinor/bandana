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
    

        // Will select RSA keys marked for signature use only
/*JWKSelector selector = new JWKSelector(
    new JWKMatcher.Builder()
        .keyType(KeyType.RSA)
        .keyUse(KeyUse.SIGNATURE)
        .build());

// Some security context that may be required by the JOSE
// signature checking and JWT processing framework, may be
// null if not required
SecurityContext ctx = new SimpleSecurityContext();

// Create a new JWK source with rate limiting and refresh ahead
// caching, using sensible default settings
    .build();
*/
    // Now you can integrate the JWKSource into your JWT processor
    

    fun validate(token: String): JWTClaimsSet {
        // Process the token
        val ctx:SecurityContext? = null; // optional context parameter, not required here
        val claimsSet: JWTClaimsSet;
        claimsSet = jwtProcessor.process(token, ctx);
        println("CLAIMS: " + claimsSet);
        val roles = claimsSet.getStringArrayClaim("roles")
        println("Roles " + roles.joinToString(" "));
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