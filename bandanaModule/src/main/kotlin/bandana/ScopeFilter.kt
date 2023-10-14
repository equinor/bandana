package bandana

import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.concurrent.ConcurrentHashMap


import org.apache.jena.system.Txn
import org.apache.jena.tdb2.store.NodeId;
import org.apache.jena.tdb2.store.nodetupletable.NodeTupleTable
import org.apache.jena.tdb2.sys.TDBInternal
import org.apache.jena.atlas.lib.tuple.Tuple;
import org.apache.jena.sparql.core.DatasetGraph
import org.apache.jena.sparql.core.DatasetGraphWrapper
import org.apache.jena.sparql.core.Quad
import org.apache.jena.graph.Node
import org.apache.jena.graph.NodeFactory

var VERBOSE = false

val rec = "https://rdf.equinor.com/ontology/record/"
val pIsInScope = NodeFactory.createURI(rec + "isInScope") ?: throw NullPointerException()
val pIsSubRecordOf = NodeFactory.createURI(rec + "isSubRecordOf") ?: throw NullPointerException()

abstract class ScopeFilter<N, T>(scopes: ScopeAccess) : Predicate<T> {
    final val scopeNodes = scopes
    protected final val resolved = ConcurrentHashMap<N, Pair<Boolean, HashMap<N, Boolean>>>()

    abstract val dataset: DatasetGraph
    abstract val isInScope: N
    abstract val isSubRecordOf: N
    abstract val ANY: N
    abstract val scopeIds: Array<Array<N>>


    override fun test(t: T): Boolean {
        if (VERBOSE) println("resolving access to tuple $t")

        if (!isQuad(t)) {
            if (VERBOSE) println("No secrets in the default graph")
            return true // No secrets in the default graph.
        }
        val g = graph(t)
        val access = testN(g)

        if (VERBOSE) println("\t access resolved to $access")
        return access
    }

    protected fun testN(g: N): Boolean = resolved.computeIfAbsent(g) { id -> Txn.calculateRead(dataset) { resolve(id) } }.first

    private fun resolve(id: N): Pair<Boolean, HashMap<N, Boolean>> {
        val map = HashMap<N, Boolean>()

        if (VERBOSE) println("resolving access to $id")

        var parentAccess = false
        for (t in find(id, id, isSubRecordOf, ANY)) {
            if (VERBOSE) println("\t $id isSubRecordOf ${`object`(t)}")

            val (pAccess, pMap) = resolved.computeIfAbsent(`object`(t), ::resolve)
            parentAccess = pAccess
            for ((k, v) in pMap) if (v) map.put(k, v)
        }
        if (parentAccess) return Pair(true, map)


        for (intersection in scopeIds) {
            if (VERBOSE) for (t in find(id, id, isInScope, ANY)) {
                val (l, r) = if (intersection.contains(`object`(t))) Pair("[", "]") else Pair("", "")
                print("$l${`object`(t)}$r ")
            }
            if (VERBOSE) println()

            var access = false
            for (scope in intersection) {
                if (map.computeIfAbsent(scope) { find(id, id, isInScope, it).hasNext() }) {
                    access = true
                } else {
                    access = false
                    break
                }
            }
            if (!access) continue
            return Pair(access, map)
        }
        return Pair(false, map)
    }

    // private fun check(scopes:Array<N>,depth:Int, graphs:Iterator<N>) : Iterator<N> = iterator {
    private fun check(scopes: Array<N>, graph: N): Array<N> {
        var list = scopes;
        iloop@ for (i in scopes.indices) {
            val s = list[i]
            val itr = find(ANY, graph, isInScope, s)
            if (!itr.hasNext()) {
                continue@iloop
            }
            list = list.sliceArray(list.indices.filter { it != i })
        }
        return list
    }

    protected fun traverse(scopes: Array<N>, graph: N = ANY): Iterator<N> = iterator traverse@{

        // 1✗[𝑍]		Given an array of scopes 𝕊 = [𝑋, 𝑌, 𝑍] this iterator
        //  \			searches for named-graphs in scope 𝑋 then recurs
        //   \			to check if those graphs again are in 𝚝𝚊𝚒𝚕(𝕊) =
        //    2✗ 		[𝑌, Z].
        //   / \
        //  /   \		In the recursive case, if a graph is not in any
        // 3✗    4✗[𝑋]	𝑌 ∈ 𝕊 super-records are traversed to see if the
        //      / \		current graph inherits any scopes in 𝕊. If neither the
        //     /   \	current graph or any ancestor-graph is in any scope
        //    5✓[𝑌] 6✗	𝑌 ∈ 𝕊 then sub-records are exhaustively traversed and
        //   / \		checked against 𝕊 individually. If graph or subgraph 𝑔
        //  /   \		is found to be in a
        // 7✓    8✓
        //
        //				In the terminal case a graph 𝑔 is checked for member-
        //				ship in scopes 𝑋 ∈ ∅ which is trivially true and 𝑔 is
        //				yielded.
        if(scopes.isEmpty()) return@traverse
        firstScope@for (quad in find(ANY, graph, isInScope, scopes[0])) {
            val g = subject(quad)

            // First scope checked in outer loop `firstScope`, remove
            // and check termination criteria
            var remaining = scopes.sliceArray(1..scopes.size-1)
            if(remaining.isEmpty()){
                yieldAll(_sub(g))
                continue@firstScope
            }

            // Check if parent graphs are in scope
            val up = _super(g).also{it.next()} // Skip first, always same as argument
            while(up.hasNext()){
                remaining = check(remaining,up.next())
                if(remaining.isEmpty()){
                    yieldAll(_sub(g))
                    continue@firstScope
                }
            }

            // traverse sub-records (bfs)
            val s = ArrayDeque<Pair<N, Array<N>>>(listOf(Pair(graph, remaining)))
            while (!s.isEmpty()) {
                val (parent, scopes) = s.removeFirst()
                for (t in find(ANY, ANY, isSubRecordOf, parent)) {
                    val child = subject(t)
                    // Check sub-record for scopes
                    val remaining = check(scopes, child)

                    if (remaining.isEmpty()) {
                        // All scopes found, yield this and sub-records
                        // and terminate this branch of bfs.
                        yieldAll(_sub(child))
                    } else {
                        // Keep nesting into this branch
                        s.addLast(Pair(child, remaining))
                    }
                }
            }
        }

    }

    private fun _super(graph: N) = _dfs(graph, {s:N -> typedArray(ANY, s, isSubRecordOf, ANY) }, ::`object`)
    private fun _sub(graph: N) = _dfs(graph, {o:N -> typedArray(ANY, ANY, isSubRecordOf, o) }, ::subject)
    private fun _dfs(graph: N, pattern: (N) -> Array<N>, project: (T) -> N): Iterator<N> = iterator {
        val stack = ArrayDeque<N>(listOf(graph))
        while (!stack.isEmpty()) {
            val c = stack.removeLast()
            yield(c)
            val (g, s, p, o) = pattern(c)
            for (t in find(g, s, p, o)) {
                val p = project(t)
                stack.addLast(p)
            }
        }
    }


    abstract fun typedArray(g:N,s:N, p:N, o:N): Array<N>
    abstract fun find(g: N, s: N, p: N, o: N): Iterator<T>
    abstract fun graph(t: T): N
    abstract fun subject(t: T): N
    abstract fun predicate(t: T): N
    abstract fun `object`(t: T): N
    abstract fun isQuad(t: T): Boolean
    abstract fun graphNodeIterator(): Iterator<Node>
    abstract fun testGraph(graph: Node): Boolean
}

class ScopeFilterQuad(dsg: DatasetGraph, scopes: ScopeAccess) : ScopeFilter<Node, Quad>(scopes) {

    override val dataset: DatasetGraph = dsg

    override val isInScope: Node = pIsInScope

    override val isSubRecordOf: Node = pIsSubRecordOf

    override val ANY: Node = Node.ANY

    override val scopeIds: Array<Array<Node>> = super.scopeNodes

    override fun find(g: Node, s: Node, p: Node, o: Node): Iterator<Quad> = dataset.find(g, s, p, o)

    override fun graph(t: Quad): Node = t.graph

    override fun subject(t: Quad): Node = t.subject

    override fun predicate(t: Quad): Node = t.predicate

    override fun `object`(t: Quad): Node = t.`object`

    override fun isQuad(t: Quad): Boolean = true

    override fun typedArray(g:Node, s:Node, p:Node, o:Node): Array<Node> = arrayOf(g,s,p,o)


    override fun graphNodeIterator(): Iterator<Node> = iterator {
        for (access in scopeIds) {
            yieldAll(traverse(access))
        }
    }

    override fun testGraph(graph: Node) = testN(graph)


}

class ScopeFilterTDB2(dsg: DatasetGraph, scopes: ScopeAccess) : ScopeFilter<NodeId, Tuple<NodeId>>(scopes) {

    override val dataset = if (dsg is DatasetGraphWrapper) dsg.getBase() else dsg
    override val isInScope: NodeId
    override val isSubRecordOf: NodeId
    override val ANY = NodeId.NodeIdAny
    override val scopeIds: Array<Array<NodeId>>

    private val idmap = if (VERBOSE) HashMap<NodeId, Node>() else null


    init {
        val (i, ii, s) = Txn.calculateRead(dataset) {
            val nt = TDBInternal.getDatasetGraphTDB(dataset).getQuadTable().getNodeTupleTable().getNodeTable()

            Triple(
                    nt.getNodeIdForNode(pIsInScope),
                    nt.getNodeIdForNode(pIsSubRecordOf),
                    Array(scopes.size) { i -> Array(scopes[i].size) { j -> nt.getNodeIdForNode(scopes[i][j]) } })
        }
        isInScope = i
        isSubRecordOf = ii
        scopeIds = s

        if (VERBOSE) for (a in scopes.indices) {
            val intnodes = scopes[a]
            val intids = scopeIds[a]

            println(intnodes.indices.joinToString(" ", "(", ")") { b -> "${intids[b]}:${intnodes[b].toString()}" })

            for (b in intnodes.indices) idmap?.put(intids[b], intnodes[b])
        }
    }

    override fun find(g: NodeId, s: NodeId, p: NodeId, o: NodeId): Iterator<Tuple<NodeId>> {
        val tt = TDBInternal.getDatasetGraphTDB(dataset).getQuadTable().getNodeTupleTable()
        return tt.find(g, s, p, o)
    }

    override fun graph(t: Tuple<NodeId>): NodeId = t.get(0)

    override fun subject(t: Tuple<NodeId>): NodeId = t.get(1)

    override fun predicate(t: Tuple<NodeId>): NodeId = t.get(2)

    override fun `object`(t: Tuple<NodeId>): NodeId = t.get(3)

    override fun isQuad(t: Tuple<NodeId>): Boolean = t.len() > 3

    override fun typedArray(g: NodeId,s : NodeId, p: NodeId ,o: NodeId): Array<NodeId> = arrayOf(g,s,p,o)

    override fun graphNodeIterator(): Iterator<Node> = Txn.calculateRead(dataset) {
        iterator {
            val nt = TDBInternal.getDatasetGraphTDB(dataset).getQuadTable().getNodeTupleTable().getNodeTable()
            for (access in scopeIds) {
                for (graph in traverse(access)) {
                    yield(nt.getNodeForNodeId(graph))
                }
            }
        }
    }

    override fun testGraph(graph: Node) = Txn.calculateRead(dataset) { testN(TDBInternal.getDatasetGraphTDB(dataset).getQuadTable().getNodeTupleTable().getNodeTable().getNodeIdForNode(graph)) }


}