package bandana.vocab

import org.apache.jena.rdf.model.ResourceFactory

// Package RDF-namespace
const val ns = "http://rdf.equinor.com/ontology/bandana#"

/**
 * Authentication filter configuration terms
 * {@link JWTBearerFilter}
 * {@link HttpFilter}
 */
val pAuthentication = ResourceFactory.createProperty(ns + "authentication") ?: throw NullPointerException()

// JWT/JWS auth implementation type
val tJWTBearerFilter = ResourceFactory.createResource(ns + "JWTBearerFilter") ?: throw NullPointerException()

// JWS validation parameters
val pAudience = ResourceFactory.createProperty(ns + "audience") ?: throw NullPointerException()
val pIssuer = ResourceFactory.createProperty(ns + "issuer") ?: throw NullPointerException()
val pJwkSetURL = ResourceFactory.createProperty(ns + "jwkSetURL") ?: throw NullPointerException()
val pRequiredClaim = ResourceFactory.createProperty(ns + "requiredClaim") ?: throw NullPointerException()

// Which claim (key) in the JWT to extract and pass along to authorization
// Is stored in {@link HttpServletRequest.attributes} map under key configured
// by `bandana:authAttrKey` in the `bandana:RoleRegistry` config-section.
const val DEFAULT_SCOPE_CLAIM_KEY = "roles"
val pScopeClaimKey = ResourceFactory.createProperty(ns + "scopeClaimKey") ?: throw NullPointerException()

// Implementation type of a non-authenticating filter that extracts 
// authorization parameters from a header configured by `bandana:scopeHeaderKey`.
// CAUTION: Performs NO VALIDATION NOR AUTHENTICATION. 
// ONLY USE TOGETHER WITH OTHER REQUEST-AUTHENTICATION METHODS AND ONLY FOR
// REQUEST FROM A TRUSTED SOURCE (since the authorization parameters themselves
// are not validated). Use for testing is fine.
val tHeaderAuthProvider = ResourceFactory.createResource(ns + "HeaderAuthProvider") ?: throw NullPointerException()
val tQueryAuthProvider = ResourceFactory.createResource(ns + "QueryAuthProvider") ?: throw NullPointerException()
const val DEFAULT_SCOPE_KEY = "Scope"
val pScopeKey = ResourceFactory.createProperty(ns + "scopeKey") ?: throw NullPointerException()

/**
 * Authorization registry configuration terms
 * {@link RoleRegistry}
 */
val tRoleRegistry = ResourceFactory.createResource(ns + "RoleRegistry") ?: throw NullPointerException()
const val DEFAULT_AUTH_ATTR_KEY = DEFAULT_SCOPE_CLAIM_KEY
val pAuthAttrKey = ResourceFactory.createProperty(ns + "authAttrKey") ?: throw NullPointerException()
val pEntry = ResourceFactory.createProperty(ns + "entry") ?: throw NullPointerException()
val pAlias = ResourceFactory.createProperty(ns + "alias") ?: throw NullPointerException()
val pRole = ResourceFactory.createProperty(ns + "role") ?: throw NullPointerException()
val pAccess = ResourceFactory.createProperty(ns + "access") ?: throw NullPointerException()
val tScopeAuthorization = ResourceFactory.createResource(ns + "ScopeAuthorization") ?: throw NullPointerException()
val tNoAuthorization = ResourceFactory.createResource(ns + "NoAuthorization") ?: throw NullPointerException()
val tWriteAuthorization = ResourceFactory.createResource(ns + "WriteAuthorization") ?: throw NullPointerException()

// Access-eval
// val tAuthorization = NodeFactory.createURI(ns + "Authorization") ?: throw NullPointerException()
// val tScopeAcess = NodeFactory.createURI(ns + "ScopeAccess") ?: throw NullPointerException()
// val tRole = NodeFactory.createURI(ns + "Role") ?: throw NullPointerException()
// val pKey = NodeFactory.createURI(ns + "key") ?: throw NullPointerException()
// val pAccess = NodeFactory.createURI(ns + "access") ?: throw NullPointerException()