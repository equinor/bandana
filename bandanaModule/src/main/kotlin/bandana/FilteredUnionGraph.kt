package bandana

import org.apache.jena.atlas.iterator.Iter
import org.apache.jena.atlas.lib.tuple.Tuple
import org.apache.jena.atlas.lib.tuple.TupleFactory
import org.apache.jena.graph.Graph
import org.apache.jena.graph.Node
import org.apache.jena.graph.Triple
import org.apache.jena.riot.other.G
import org.apache.jena.sparql.core.*
import org.apache.jena.tdb2.lib.TupleLib
import org.apache.jena.tdb2.store.DatasetGraphSwitchable
import org.apache.jena.tdb2.store.GraphTDB
import org.apache.jena.tdb2.store.GraphViewSwitchable
import org.apache.jena.tdb2.store.NodeId
import org.apache.jena.util.iterator.ExtendedIterator
import org.apache.jena.util.iterator.NiceIterator
import org.apache.jena.util.iterator.WrappedIterator
import java.util.function.Predicate


class FilteredUnionDataset(dsg: DatasetGraph, private val filter: ScopeFilter<*,*>) : DatasetGraphReadOnly(dsg), DatasetGraphWrapperView {
    override fun getGraph(graphNode: Node?): Graph {
        if(Quad.isUnionGraph(graphNode)) return unionGraph
        return super.getGraph(graphNode)
    }

    override fun getUnionGraph(): Graph  = when {
        get().isWrappedTDB2() && filter is ScopeFilterTDB2 -> FilteredUnionGraphSwitchable(get().unwrap() as DatasetGraphSwitchable, filter)
        filter is ScopeFilterQuad -> FilteredUnionGraph(get().unwrap(), filter)
        else -> throw java.lang.IllegalStateException("filter and dataset type must line up!!")
    }

    override fun find(quad: Quad?): Iterator<Quad> = find(quad?.graph, quad?.subject, quad?.predicate, quad?.`object`)

    private fun isWildcard(g: Node?): Boolean {
        return g == null || g === Node.ANY
    }
    override fun findNG(g: Node?, s: Node?, p: Node?, o: Node?): Iterator<Quad> {
        if(Quad.isUnionGraph(g)) {
            return unionGraph.find(s, p, o).let { G.triples2quads(Quad.unionGraph, it) }
        }
        return r.findNG(g, s, p, o)
    }

    override fun find(g: Node?, s: Node?, p: Node?, o: Node?): Iterator<Quad> {
        if(Quad.isDefaultGraph(g) || isWildcard(g)) return r.find(g,s,p,o)
        return findNG(g,s,p,o)
    }

}


class FilteredUnionGraph(dsg: DatasetGraph, private val filter: Predicate<Quad>) : GraphView(dsg, Quad.unionGraph) {
    override fun graphUnionFind(s: Node?, p: Node?, o: Node?): ExtendedIterator<Triple> {
        val iterQuads = this.dataset.find(Node.ANY, s, p, o)
                .let { Iter.filter(it, filter) }
                .let { G.quads2triples(it) }
                .let { Iter.distinct(it) }
        return WrappedIterator.createNoRemove(iterQuads)
    }
}

class FilteredUnionGraphSwitchable(dsg: DatasetGraphSwitchable, private val filter: Predicate<Tuple<NodeId>>) : GraphViewSwitchable(dsg, Quad.unionGraph) {
    override fun graphBaseFind(s: Node, p: Node, o: Node): ExtendedIterator<Triple> {

        /**
         * Algorithm for querying triples from the union graph ported from:
         *
         * jena-tdb2/src/main/java/org/apache/jena/tdb2/solver/SolverLibTDB.java
         * jena-tdb2/src/main/java/org/apache/jena/tdb2/solver/PatternMatchTDB2.java
         *
         * which works on _bindings_, whereas this implementation converts directly
         * from/to _triples_
         */

        val graphDSG = baseGraph as? GraphTDB ?: throw IllegalArgumentException("Not a TDB2 graph!?!?")
        val nodeTupleTable = graphDSG.nodeTupleTable ?: throw IllegalArgumentException("No nodeTupleTable")
        val tupleLen = nodeTupleTable?.tupleTable?.tupleLen ?: throw IllegalArgumentException("No tupleTable")
        if (tupleLen != 4) throw IllegalArgumentException("Not a quad table!")
        val patternTuple = TupleFactory.create4(Node.ANY, s, p, o);
        val nodeTable = nodeTupleTable.nodeTable ?: throw IllegalStateException("No nodeTable!")

        val ids = Array<NodeId>(patternTuple.len()) { i -> nodeTable.getNodeIdForNode(patternTuple.get(i)) }

        if (ids.any(NodeId::isDoesNotExist)) return NiceIterator.emptyIterator()

        val matches = nodeTupleTable.find(TupleFactory.create(ids))
                .let { m -> Iter.filter(m, filter) }
                .let { f -> Iter.map(f) { t -> TupleFactory.create3(t.get(1), t.get(2), t.get(3)) } }
                .let { t -> Iter.distinctAdjacent(t) }
                .let { d -> TupleLib.convertToTriples(nodeTable, d) }
        return WrappedIterator.createNoRemove(matches)
    }
}