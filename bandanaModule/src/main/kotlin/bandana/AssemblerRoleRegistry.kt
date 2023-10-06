package bandana

import java.util.function.BiFunction
import org.apache.jena.assembler.Assembler
import org.apache.jena.assembler.Mode
import org.apache.jena.assembler.assemblers.AssemblerBase
import org.apache.jena.assembler.exceptions.AssemblerException
import org.apache.jena.fuseki.access.AuthorizationService
import org.apache.jena.fuseki.access.SecurityContext
import org.apache.jena.fuseki.access.SecurityContextAllowAll
import org.apache.jena.graph.Node
import org.apache.jena.graph.Node_URI
import org.apache.jena.query.Query
import org.apache.jena.query.QueryExecution
import org.apache.jena.rdf.model.Literal
import org.apache.jena.rdf.model.Resource
import org.apache.jena.rdf.model.ResourceFactory
import org.apache.jena.rdf.model.impl.Util
import org.apache.jena.riot.out.NodeFmtLib
import org.apache.jena.sparql.core.DatasetGraph
import org.apache.jena.sparql.core.Quad
import org.apache.jena.sparql.util.MappingRegistry
import org.apache.jena.sparql.util.graph.GNode
import org.apache.jena.sparql.util.graph.GraphList

import bandana.vocab.*

class AssemblerRoleRegistry() : AssemblerBase() {
    companion object {
    
        init {
            MappingRegistry.addPrefixMapping("bandana", ns)
            Assembler.general.implementWith(tRoleRegistry, AssemblerRoleRegistry())
        }
    }
    override fun open(a: Assembler, root: Resource, mode: Mode): AuthorizationService {
        val registry = RoleRegistry()
        val sIter = root.listProperties(pEntry)
        if (!sIter.hasNext()) 
            throw AssemblerException(root, "No role access entries")
        sIter.forEachRemaining {
            val o = it.getObject() as? Resource
            if(o is Resource){

                val role = it.getProperty(pRole)
                           ?.let{it.getObject() as? Literal}
                           ?: throw AssemblerException(root,"Found bandana:entry but bandana:role was either missing or not a literal: " + it)

                val access = it.getProperty(pAccess)
                ?.let{it.getObject() as? Resource}
                ?: throw AssemblerException(root,"Found bandana:entry but bandana:access was either missing or not a resource: " +it)
                
                if (access == tWriteAuthorization) {
                    registry.addWriteRole(role.getString())
                } else {
                    val sCtx = when(access) {
                            tScopeAuthorization -> ScopedSecurity
                            tNoAuthorization -> NoSecurity
                            else -> throw AssemblerException(root,"Found bandana:entry but bandana:access was either missing or not a resource: " +it)
                        }
                    registry.addReadRole(role.getString(), sCtx)
                }
            }else throw AssemblerException(root, "Found bandana:entry with non-resource")
        }
        root.listProperties(pAlias).forEachRemaining {
            var n = it.getObject()
            if (n !is Resource) throw AssemblerException(root, "Found bandana:alias with non-list")
            var entry = GNode(root.getModel().getGraph(), n.asNode())
            if (GraphList.isListNode(entry)) {
                // Format:: bandana:alias ("roleAlias" <http://host/graphname1> <http://host/graphname2> );
                val members = GraphList.members(entry)
                if (members.size < 2 ) throw AssemblerException(root, "Found bandana:alias with an empty list")
                val role = members.get(0)?.let {
                        if (Util.isSimpleString(it)) it.getLiteralLexicalForm()
                        else throw AssemblerException(root,"Role name is not a string: " +NodeFmtLib.strTTL(members.get(0))) 
                } ?: throw AssemblerException(root, "Role name is null ")
                
                val scopes = Array(members.size - 1) { 
                        i -> members.get(i + 1)?.let{
                                if(it.isURI()) it 
                                else null
                        } ?: throw AssemblerException(root,"Non-resource scope in bandana:alias")
                }
                registry.addAlias(role, scopes)
            } else throw AssemblerException(root, "Found bandana:alias with non-list")
        }

        return registry
    }
}

typealias ScopeContextFactory = BiFunction<ScopeAccess,DatasetGraph, SecurityContext>
typealias ScopeAccess = Array<Array<Node>>

val ScopedSecurity = object : ScopeContextFactory  {

    override fun apply(scopes: ScopeAccess, dsg: DatasetGraph): SecurityContext = ScopedSecurityContext(scopes, dsg)
}
val NoSecurity = object : ScopeContextFactory {

    override fun apply(scopes: ScopeAccess, dsg: DatasetGraph): SecurityContext = SecurityContextAllowAll()
}
