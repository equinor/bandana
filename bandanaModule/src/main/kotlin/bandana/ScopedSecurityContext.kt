package bandana

import org.apache.jena.fuseki.access.DataAccessCtl
import org.apache.jena.fuseki.access.DatasetGraphAccessControl
import org.apache.jena.fuseki.access.SecurityContext
import org.apache.jena.graph.Graph
import org.apache.jena.graph.Node
import org.apache.jena.graph.NodeFactory
import org.apache.jena.graph.compose.Union
import org.apache.jena.irix.IRIs
import org.apache.jena.query.Query
import org.apache.jena.query.QueryExecution
import org.apache.jena.query.QueryExecutionFactory
import org.apache.jena.sparql.ARQConstants
import org.apache.jena.sparql.core.DatasetGraph
import org.apache.jena.sparql.core.DatasetGraphMapLink
import org.apache.jena.sparql.core.DatasetGraphWrapper
import org.apache.jena.sparql.core.DynamicDatasets.DynamicDatasetGraph
import org.apache.jena.sparql.core.Quad
import org.apache.jena.sparql.graph.GraphOps
import org.apache.jena.sparql.graph.GraphUnionRead
import org.apache.jena.tdb2.DatabaseMgr
import org.apache.jena.tdb2.store.DatasetGraphSwitchable
import org.apache.jena.tdb2.sys.SystemTDB
import java.util.function.Predicate


fun dynamicDataset(defaultGraphs: Collection<Node?>, namedGraphs: Collection<Node?>, dsg: DatasetGraph): DatasetGraph {
    /**
     * Modified from {jena-arq/src/main/java/org/apache/jena/sparql/core/DynamicDatasets.java}
     * please refer to {apache-jena/dist/LICENSE} [Apache 2.0] for copyright/redistribution terms.
     */
    val dft: Graph
    if (defaultGraphs.contains(Quad.unionGraph)) {
        val baseUnion = dsg.unionGraph
        if (defaultGraphs.contains(Quad.defaultGraphIRI))
            dft = Union(baseUnion, dsg.defaultGraph)
        else  // Any other FROM graphs don't matter - they are in the union graph.
            dft = baseUnion
    } else dft = GraphUnionRead(dsg, defaultGraphs)
    var dsg2: DatasetGraph = DatasetGraphMapLink(dft)

    // The named graphs.
    for (gn in namedGraphs) {
        if (Quad.isUnionGraph(gn)) {
            // Special case - don't put an explicitly named union graph into the name
            // graphs because union is an operation over all named graphs ... which
            // includes itself.
            // In practical terms, it can lead to stack overflow in execution.
            continue
        }
        val g: Graph? = GraphOps.getGraph(dsg, gn)
        if (g != null) dsg2.addGraph(gn, g)
    }
    if (dsg.context != null) dsg2.context.putAll(dsg.context)
    dsg2 = DynamicDatasetGraph(dsg2, dsg)

    // Record what we've done.
    // c.f. "ARQConstants.sysDatasetDescription" which is used to pass in a DatasetDescription
    dsg2.getContext()[ARQConstants.symDatasetDefaultGraphs] = defaultGraphs
    dsg2.getContext()[ARQConstants.symDatasetNamedGraphs] = namedGraphs
    return dsg2
}

class ScopedSecurityContext(scopes: ScopeAccess, dsg: DatasetGraph) : SecurityContext {
    private val scopes = scopes
    private val dataset = dsg
    val scopeFilter = if (dsg.isWrappedTDB2()) ScopeFilterTDB2(dataset.unwrap(), scopes) else ScopeFilterQuad(dataset.unwrap(), scopes)

    override fun visableDefaultGraph(): Boolean {
        return false
    }

    override fun createQueryExecution(query: Query, dsg: DatasetGraph): QueryExecution {
        var dsgA: DatasetGraph
        var tdb = dsg.isWrappedTDB2()

        if (dsg is DynamicDatasetGraph) {
            dsgA = dynamicDataset(dsg.originalDefaultGraphs, dsg.originalNamedGraphs, FilteredUnionDataset(dsg.unwrap(), scopeFilter))
        }
        else if (!tdb) dsgA = DataAccessCtl.filteredDataset(dsg, this)
        else dsgA = dsg

        var qExec = QueryExecutionFactory.create(query, dsgA)
        if(tdb) filterTDB2(qExec)
        return qExec


    }

    override fun predicateQuad(): Predicate<Quad> = (scopeFilter as? ScopeFilterQuad)
            ?: ScopeFilterQuad(dataset, scopes)

    private val vGraphs = object : Collection<Node> {
        override val size: Int = -1

        override fun contains(element: Node): Boolean = when (element) {
            Quad.defaultGraphIRI -> false
            Quad.defaultGraphNodeGenerated -> false
            else -> scopeFilter.testGraph(element)
        }

        override fun containsAll(elements: Collection<Node>): Boolean = elements.all { contains(it) }

        override fun isEmpty(): Boolean = !iterator().hasNext()

        override fun iterator(): Iterator<Node> = scopeFilter.graphNodeIterator()

    }
    private val vGraphNames = object : Collection<String> {
        override val size
            get() =
                if (unionChecked && defaultChecked) 2
                else if (unionChecked) 1
                else -1


        override fun contains(element: String): Boolean {
            // This is a TERRIBLE hack to get around the fact that
            // Fuseki will attempt to enumerate all visible graphs
            // in a non-overridable way in the event that the Union
            // Graph is among the default graps ("FROM").
            if (element == Quad.unionGraph.uri) {
                unionChecked = true
            }
            if (element == Quad.defaultGraphIRI.uri) {
                defaultChecked = true
            }
            if (!IRIs.check(element)) return false
            return vGraphs.contains(NodeFactory.createURI(element))
        }

        private var unionChecked = false
        private var defaultChecked = false

        override fun containsAll(elements: Collection<String>): Boolean = elements.all { contains(it) }

        override fun isEmpty(): Boolean = iterator().hasNext()

        override fun iterator(): Iterator<String> = iterator {
            if (unionChecked) {
                yield(Quad.unionGraph.uri)
                if (defaultChecked)
                    yield(Quad.unionGraph.uri)
            } else for (n in vGraphs) yield(n.toString())
        }

    }

    override fun visibleGraphs(): Collection<Node> = vGraphs
    override fun visibleGraphNames(): Collection<String> = vGraphNames

    private fun filterTDB2(qExec: QueryExecution) {
        qExec.context.set(SystemTDB.symTupleFilter, scopeFilter as? ScopeFilterTDB2)
    }


    protected fun isAccessControlledTDB2(dsg: DatasetGraph): Boolean {
        var dsgBase = DatasetGraphAccessControl.unwrapOrNull(dsg)
        return dsgBase != null && DatabaseMgr.isTDB2(dsgBase)
    }

}

fun DatasetGraph.isWrappedTDB2(): Boolean = this.unwrap() is DatasetGraphSwitchable
fun DatasetGraph.unwrap(): DatasetGraph =
        when (this) {
            is DatasetGraphSwitchable -> this
            is DynamicDatasetGraph -> this.original.unwrap()
            is DatasetGraphWrapper -> this.wrapped.unwrap()
            else -> this
        }
