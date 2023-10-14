package bandana

import bandana.vocab.*
import org.apache.jena.fuseki.access.*
import org.apache.jena.fuseki.servlets.ServletOps
import org.apache.jena.graph.Node
import org.apache.jena.graph.NodeFactory
import org.apache.jena.irix.IRIs
import org.apache.jena.sparql.core.DatasetGraph
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class RoleRegistry() :  AuthorizationService {
	
	private val _readRoles = ConcurrentHashMap<String, ScopeContextFactory>()
	private val _writeRoles = HashSet<String>()
	private val _aliases = ConcurrentHashMap<String, Array<Node>>()

	private var dataset : DatasetGraph? = null
	fun setDataset(ds: DatasetGraph){
		dataset = ds
	}

	var authAttrKey = DEFAULT_AUTH_ATTR_KEY

    override fun get(access: String?): SecurityContext {

		if(access == null) return SecurityContextAllowNone()
		val claims = access.split('\n')

		val readRoles = ArrayList<String>()
		val aliases = ArrayList<String>()
		val unknown = ArrayList<String>()
		for(claim in claims){
			when{
				isReadRole(claim) -> readRoles.add(claim)
				isAlias(claim) -> aliases.add(claim)
				else -> unknown.add(claim)
			}
		}

		if (readRoles.size == 0) ServletOps.errorForbidden("No read roles")
		if (readRoles.size > 1) println("More than 1 read role, picking ${readRoles[0]} at random")

		val factory = _readRoles.get(readRoles[0]) ?: throw NullPointerException("key ${readRoles[0]} not a registered ScopeContextFactory")
		val scopes = ArrayList<Array<Node>>(aliases.size)//{i -> getAlias(aliases[i])}
		for(a in aliases) scopes.add(getAlias(a))
		for(u in unknown){
			var intersection = ArrayList<Node>()
			for(e in u.split("\t")) when{
				isAlias(e) -> for(n in getAlias(e)) intersection.add(n)
				IRIs.check(e) -> intersection.add(NodeFactory.createURI(e))
			}
			if(intersection.size > 0) scopes.add(intersection.toTypedArray())
		}
		println("SCOPES: ${scopes.joinToString(" ", "(", ")")}")
		return factory.apply(scopes.toTypedArray(), dataset?:throw KotlinNullPointerException())

	}

	fun addWriteRole(role:String) = _writeRoles.add(role)
	fun addReadRole(role:String, factory:ScopeContextFactory) = _readRoles.put(role, factory)

	fun isReadRole(readRole:String):Boolean = _readRoles.containsKey(readRole)


	fun getWriteRoles(): Set<String> = Collections.unmodifiableSet(_writeRoles);
	fun addAlias(role:String, scopes:Array<Node>) = _aliases.put(role, scopes)

	fun isAlias(role:String):Boolean = _aliases.containsKey(role)

	fun getAlias(role:String) : Array<Node> = _aliases.get(role)?.let(Array<Node>::copyOf)?:throw NullPointerException()


}

