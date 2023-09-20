package bandana

import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.concurrent.ConcurrentHashMap


import org.apache.jena.system.Txn
import org.apache.jena.tdb2.store.NodeId;
import org.apache.jena.tdb2.sys.TDBInternal
import org.apache.jena.atlas.lib.tuple.Tuple;
import org.apache.jena.sparql.core.DatasetGraph
import org.apache.jena.sparql.core.DatasetGraphWrapper
import org.apache.jena.graph.Node
import org.apache.jena.graph.NodeFactory

var VERBOSE = false

class ScopeFilterTDB2(dsg: DatasetGraph, scopes: ScopeAccess) : Predicate<Tuple<NodeId>>{

	val rec = "https://rdf.equinor.com/ontology/record/"
	val pIsInScope =  NodeFactory.createURI(rec + "isInScope") ?: throw NullPointerException()
	val pIsSubRecordOf =  NodeFactory.createURI(rec + "isSubRecordOf") ?: throw NullPointerException()
	

	private val tdb = if(dsg is DatasetGraphWrapper) dsg.getBase() else dsg
	private val isInScope: NodeId
	private val isSubRecordOf: NodeId
	private val scopeIds: Array<Array<NodeId>>

	private val idmap = if(VERBOSE) HashMap<NodeId,Node>() else null
	
	
	init {
		val (i,ii, s) = Txn.calculateRead(tdb){
			val nt = TDBInternal.getDatasetGraphTDB(tdb).getQuadTable().getNodeTupleTable().getNodeTable()

			Triple(
				nt.getNodeIdForNode(pIsInScope), 
				nt.getNodeIdForNode(pIsSubRecordOf), 
				Array(scopes.size){i -> Array(scopes[i].size){j -> nt.getNodeIdForNode(scopes[i][j])}})
			}
		isInScope = i
		isSubRecordOf = ii
		scopeIds = s

		if(VERBOSE) for(a in scopes.indices) {
			val intnodes = scopes[a]
			val intids = scopeIds[a]

			println(intnodes.indices.joinToString(" ", "(", ")") {b -> "${intids[b]}:${intnodes[b].toString()}" })

			for(b in intnodes.indices) idmap?.put(intids[b], intnodes[b])
		}
	}

	private val resolved = ConcurrentHashMap<NodeId, Pair<Boolean, HashMap<NodeId, Boolean>>>()


    override fun test(t: Tuple<NodeId>): Boolean { 
		if(VERBOSE) println("resolving access to tuple $t")

		if(t.len() == 3) {
			if(VERBOSE) println("No secrets in the default graph")
			return true // No secrets in the default graph.
		}
		val g = t.get(0) ?: throw NullPointerException()
		val (access, _) = resolved.computeIfAbsent(g){id -> Txn.calculateRead(tdb) {resolve(id)}}

		if(VERBOSE) println("\t access resolved to $access")
		return access

	}

	private fun resolve(id: NodeId): Pair<Boolean, HashMap<NodeId, Boolean>>{
		val tt = TDBInternal.getDatasetGraphTDB(tdb).getQuadTable().getNodeTupleTable()
		val map = HashMap<NodeId, Boolean>()

		if(VERBOSE) println("resolving access to $id: ${idmap?.get(id)}")

		var parentAccess = false
		for(t in tt.find(id, id, isSubRecordOf, NodeId.NodeIdAny)){
			if(VERBOSE) println("\t $id isSubRecordOf ${t.get(3)}")

			val (pAccess, pMap) = resolved.computeIfAbsent(t.get(3), ::resolve)
			parentAccess = pAccess
			for((k, v) in pMap) if(v) map.put(k,v)
		}
		if(parentAccess) return Pair(true,map)


		for(intersection in scopeIds){
			if(VERBOSE) for(t in tt.find(id,id,isInScope, NodeId.NodeIdAny)){
				val (l, r) = if(intersection.contains(t.get(3))) Pair("[","]") else Pair("","")
				print("$l${t.get(3)}$r ")
			}
			if(VERBOSE) println()
			
			var access = false
			for(scope in intersection){
				if(map.computeIfAbsent(scope){tt.find(id, id, isInScope, it).hasNext()}){
					access = true
				}
				else
				{
					access = false
					break
				}
			}
			if(!access) continue
			return Pair(access, map)
		}
		return Pair(false, map)

	}

}