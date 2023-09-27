package bandana

import org.apache.jena.atlas.lib.Registry;
import org.apache.jena.fuseki.access.SecurityContext
import org.apache.jena.fuseki.access.AuthorizationService
import org.apache.jena.fuseki.access.SecurityContextAllowNone
import org.apache.jena.fuseki.access.DatasetGraphAccessControl
import org.apache.jena.fuseki.access.GraphFilter
import org.apache.jena.fuseki.access.DataAccessCtl
import org.apache.jena.irix.IRIs
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
import org.apache.jena.fuseki.servlets.ServletOps
import org.apache.jena.tdb2.sys.SystemTDB
import java.util.function.Function
import java.util.function.Predicate
import java.util.concurrent.ConcurrentHashMap
import java.util.Collections

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
	private val _readRoles = ConcurrentHashMap<String, ScopeContextFactory>()
	private val _writeRoles = HashSet<String>()
	private val _aliases = ConcurrentHashMap<String, Array<Node>>()

	private var dataset : DatasetGraph? = null
	fun setDataset(ds: DatasetGraph){
		dataset = ds
	}

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

class ScopedSecurityContext(scopes: ScopeAccess, dsg: DatasetGraph) : SecurityContext {
	val scopes = scopes
	val dataset = dsg
	val scopeFilter = if(isAccessControlledTDB2(dsg)) ScopeFilterTDB2(dataset, scopes) else ScopeFilterQuad(dataset, scopes)
	// val predQ = ScopeFilterQuad(dataset, scopes)
	// val predTDB = ScopeFilterTDB2(dataset, scopes)

    override fun visableDefaultGraph(): Boolean {
        return false
    }

    override fun createQueryExecution(query: Query, dsg: DatasetGraph): QueryExecution {
        if(isAccessControlledTDB2(dsg)){
			val qExec = QueryExecutionFactory.create(query,dsg)
			filterTDB2(qExec)
			return qExec
		}
		val dsgA = DataAccessCtl.filteredDataset(dsg, this)
		return QueryExecutionFactory.create(query, dsgA)
    }

    override fun predicateQuad(): Predicate<Quad>? = scopeFilter as? ScopeFilterQuad

	private val vGraphs = object:Collection<Node>{
		override val size: Int = -1

		override fun contains(element: Node): Boolean = when(element){
			Quad.defaultGraphIRI -> false
			Quad.defaultGraphNodeGenerated -> false
			else -> scopeFilter.testGraph(element)
		}

		override fun containsAll(elements: Collection<Node>): Boolean = elements.all{contains(it)}
	
		override fun isEmpty(): Boolean = !iterator().hasNext()
	
		override fun iterator(): Iterator<Node> = scopeFilter.graphNodeIterator()
		
	}
	private val vGraphNames = object:Collection<String>{
		override val size: Int = -1

		override fun contains(element: String): Boolean {
			if(!IRIs.check(element)) return false
			return vGraphs.contains(NodeFactory.createURI(element))
		}

		override fun containsAll(elements: Collection<String>): Boolean = elements.all{contains(it)}
	
		override fun isEmpty(): Boolean = iterator().hasNext()
	
		override fun iterator(): Iterator<String> = iterator {
			for(n in vGraphs) yield(n.toString())
		}
	}
	override fun visibleGraphs(): Collection<Node> = vGraphs
	override fun visibleGraphNames(): Collection<String> = vGraphNames

	fun filterTDB2(qExec:QueryExecution) {
		qExec.getContext().set(SystemTDB.symTupleFilter, scopeFilter as? ScopeFilterTDB2)
	}


	protected fun isAccessControlledTDB2(dsg: DatasetGraph) : Boolean {
		val dsgBase = DatasetGraphAccessControl.unwrapOrNull(dsg)
		if(dsgBase != null && DatabaseMgr.isTDB2(dsgBase))
			return true
		return false
	}
	
	
}

