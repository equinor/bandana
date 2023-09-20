package bandana

import org.apache.jena.atlas.lib.Registry;
import org.apache.jena.fuseki.access.SecurityContext
import org.apache.jena.fuseki.access.AuthorizationService
import org.apache.jena.fuseki.access.SecurityContextAllowNone
import org.apache.jena.fuseki.access.DatasetGraphAccessControl
import org.apache.jena.fuseki.access.GraphFilter
import org.apache.jena.graph.Node
import org.apache.jena.graph.NodeFactory
import org.apache.jena.graph.Node_Literal
import org.apache.jena.sparql.core.Quad
import org.apache.jena.sparql.core.DatasetGraph
import org.apache.jena.sparql.util.graph.GraphContainerUtils
import org.apache.jena.query.Query
import org.apache.jena.query.QueryExecution
import org.apache.jena.query.QueryExecutionFactory
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFParser
import org.apache.jena.riot.RDFLanguages
import org.apache.jena.vocabulary.RDF
import org.apache.jena.vocabulary.RDFS
import org.apache.jena.tdb2.DatabaseMgr
import org.apache.jena.tdb2.sys.SystemTDB
import java.util.function.Function
import java.util.function.Predicate
import java.util.concurrent.ConcurrentHashMap

class RoleRegistry() :  AuthorizationService {
	companion object {
		@JvmStatic val a  = RDF.type.asNode()?: throw NullPointerException()
		@JvmStatic val BAG = RDF.Bag.asNode()?: throw NullPointerException()
		@JvmStatic val ALT = RDF.Alt.asNode()?: throw NullPointerException()
		@JvmStatic val member = RDFS.member.asNode()?: throw NullPointerException()
		@JvmStatic val label = RDFS.label.asNode()?: throw NullPointerException()
		@JvmStatic val SEQ = RDF.Seq.asNode()?: throw NullPointerException()

		@JvmStatic val tAuthorization = NodeFactory.createURI(ns + "Authorization") ?: throw NullPointerException()
		@JvmStatic val tScopeAcess = NodeFactory.createURI(ns + "ScopeAccess") ?: throw NullPointerException()
		@JvmStatic val tRole = NodeFactory.createURI(ns + "Role") ?: throw NullPointerException()
		@JvmStatic val pKey = NodeFactory.createURI(ns + "key") ?: throw NullPointerException()
		@JvmStatic val pAccess = NodeFactory.createURI(ns + "access") ?: throw NullPointerException()
	}
	private val _roles = ConcurrentHashMap<String, ScopeContextFactory>()
	private val _aliases = ConcurrentHashMap<String, Array<Node>>()

    override fun get(access: String?): SecurityContext {

		if(access == null) return SecurityContextAllowNone()
		val claims = access.split('\n')

		val roles = ArrayList<String>()
		val aliases = ArrayList<String>()
		val unknown = ArrayList<String>()
		for(claim in claims){
			when{
				isRole(claim) -> roles.add(claim)
				isAlias(claim) -> aliases.add(claim)
				else -> unknown.add(claim)
			}
		}
		if(roles.size > 1)
			println("Multiple configured roles. Picking ${roles[0]} at random.")
		val factory = _roles.get(roles[0]) ?: throw NullPointerException("key ${roles[0]} not a registered ScopeContextFactory")
		val scopes = Array(aliases.size){i -> getAlias(aliases[i])}
		println("SCOPES: ${scopes.joinToString(" ", "(", ")")}")
		return factory.apply(scopes)

	}

	fun addRole(role:String, factory:ScopeContextFactory) = _roles.put(role, factory)

	fun isRole(role:String):Boolean = _roles.containsKey(role)

	fun addAlias(role:String, scopes:Array<Node>) = _aliases.put(role, scopes)

	fun isAlias(role:String):Boolean = _aliases.containsKey(role)

	fun getAlias(role:String) : Array<Node> = _aliases.get(role)?.let(Array<Node>::copyOf)?:throw NullPointerException()


}
class ScopedSecurityContext(scopes: ScopeAccess) : SecurityContext {
	val scopes = scopes

    override fun visableDefaultGraph(): Boolean {
        return false
    }

    override fun createQueryExecution(query: Query, dsg: DatasetGraph): QueryExecution {
        if(isAccessControlledTDB2(dsg)){
			val qExec = QueryExecutionFactory.create(query,dsg)
			filterTDB2(dsg, qExec)
			return qExec
		}
		throw NotImplementedError("Currently, only TDB2 database is supported.")
    }

    override fun predicateQuad(): Predicate<Quad> {
        return Predicate<Quad> { true }
    }

    override fun visibleGraphs(): Collection<Node> {
        throw NotImplementedError()
    }

	fun filterTDB2(dsg:DatasetGraph, qExec:QueryExecution) {
		val predicate = ScopeFilterTDB2(dsg, scopes)
		qExec.getContext().set(SystemTDB.symTupleFilter, predicate)
	}


	protected fun isAccessControlledTDB2(dsg: DatasetGraph) : Boolean {
		val dsgBase = DatasetGraphAccessControl.unwrapOrNull(dsg)
		if(dsgBase != null && DatabaseMgr.isTDB2(dsgBase))
			return true
		return false
	}
	
	
}


