<span style="text-align:center;"> 

## Theory of Access Control for <br /> Index-Catalogued Datastores

</span>

Given a [datastore](#datastore) $\mathbb{S}$ containing [datasets](#dataset) of type $D$ and a catalog of *terms* $I$, we say that $\mathbb{S}$ is *indexed over catalog* $I$ iff
- each $x\mathrel{:}D ∈ \mathbb{S}$ is annotated by one or more *terms*  $t_1,\dots, t_n ∈ I$ given by a *domain map* $d_\mathbb{S}\colon D ⟶ \wp(I)$
- *domain maps* $d_\mathbb{S}$ define an inverse *range function* $r_I\colon\wp(I)⟶D$ such that
```math
\begin{align}
x \subseteq r_I &∘ d_\mathbb{S}(x) \\
t_1,\dots,t_n \in d_\mathbb{S} &∘ r_I(\{t_1,\dots,t_n\}) \\
\end{align}
```

###### Range functions

For access-control usecases we are mainly interested in 2 kinds of *range functions*:
For a given set of *terms* $t_1,\dots,t_n ∈ A$ and a corresponding *domain map* $\overleftrightarrow{d_\mathbb{S}}$
- The *injective* range function $\overrightarrow{r_I}$ maps to those datasets $x\in\mathbb{S}$ that are annotated with *all* terms $t∈ A$.  
  More formally:
```math
\overrightarrow{r_I}(A) = \bigcup_{x\in\mathbb{S}}{∀t \mathrel{∈} A \mathrel{.} t∈ \overrightarrow{d_\mathbb{S}}(x)}
```
  - If $\overrightarrow{d_\mathbb{S}}$ maps to terms of $I$ that are (essentially) *business topics* then $\overrightarrow{r_I}$ ranges over datasets *at the intersection* of a given set of topics, i.e. datasets that are simultaneously *about* several topics (*terms*).
  This of interest to the problem of access-control for two main reasons:
    1. Datasets are usually (and in the case of `bandana`) topically organized/annotated as a result of business requirements that precede the requirement for fine-grained access-control (such as regular business operations)
    2. Many common access-control usecases essentially boil down to granting certain users access to information at the intersection of two or more topics, for e.g. "plumbing at facility X" or "finances of project Y contractor Z".

- The *surjective* range function $\overleftarrow{r_I}$ maps the datasets $x\in\mathbb{S}$ where *every* annotated term $t∈ \overleftarrow{d_\mathbb{S}}(x)$ is in the given set of terms, $t∈ A$
  More formally:
```math
\overleftarrow{r_I}(A) = \bigcup_{x\in\mathbb{S}}{∀t∈ \overleftarrow{d_\mathbb{S}}(x) \mathrel{.} t \mathrel{∈} A} 
```
  - If $\overleftarrow{d_\mathbb{S}}$ maps to terms that correspond to *privileged access roles* (such as "securtity clearance") then $\overleftarrow{r_I}$ ranges over datasets $x ∈ \mathbb{S}$ where a user has the required set of access privileges (*terms*).

### Definition
The purpose of defining *indexed-catalogued datastores* in terms of *domain maps* (from individual datasets to collections of terms [^1]) and *range functions* (from collections of *index catalog terms* to some defined subset of all the datasets in the datastore) is to make explicit that access-control ultimately depends on making *distinctions* between collections of information (datasets), that this disctinction has to be made over *properties* of the datasets (given by $d_\mathbb{S}$), and that how we make the distinction ($r_I$) is a function of the *distinguishing properties*.

A definiton of access-control in terms of *index catalog*, *domain map* and *range function* then becomes a portable specification to be implemented accross different underlying datastores.

### Specification and usecase
`Bandana` defines access-control over the *Record Ontology*[^RO] using the *scope*[^SCO] of each *record* as its *domain map* $d_\mathbb{S}$. Scopes are used by [^RO] to place the dataset content of a *record* in context with the *business concepts* to which it pertains. *"Scope"* being a *topical index*, `bandana` uses the *injective range function* $\overrightarrow{r_I}$ to support usecases of granting access to specific topics of information, or information at the intersection of two or more topics.

Specifically, `Bandana` takes a datastore $\mathbb{S}$ and a *set of scopes*[^SCO] (or *"access policy"*) $A$, each a set of business concepts/topics, and exposes a datastore $\mathbb{S}_A$ that *contains* (or "*grants access to*") the datasets *in range* of any of the *scope intersections* $s∈A$.
More formally:
```math
\mathbb{S}_A = \bigcup_{s\in A}{\overrightarrow{r_I}(s)}
```

#### Extensions
In order to extend policy semantics to support additional use-cases of restricted access within a business topic, we can extend our index-catalogued datastore to include an additional *domain map* (maybe extending [^RO] with some with some new access-privilege predicate), and extend our evaluation to include the *surjective range function* thus:
```math
\mathbb{S}_A = \bigcup_{s\in A}{\overrightarrow{r_I}(s)\land\overleftarrow{r_I}(s)}
```
> Note: *While the example above uses the same set of scopes (i.e. set of sets of terms) as input to the two range functions, the corresponding domain maps are different. If they used the same domain map, this example would define the bijective range function. Why this is not terribly interesting to `Bandana` is left as an exercise to the reader.* 

Alternatively, we can separate the access-policy into a *scope* part and a *privileged access role* part:
```math
\mathbb{S}_{A,R} = \bigcup_{\substack{s∈ A \\ p ∈ R}}{\overrightarrow{r_I}(s)\land\overleftarrow{r_I}(p)}
```


---

# Glossary
###### *Authorization*
  - Tildeling av en eller annen POLICY til en bruker eller gruppe (PDP)
  - Håndheving av POLICY når bruker/tjeneste ber om tilgang til værnet ressurs (PEP)
###### *Authenticated user*
###### *Authorization data*
attributes of a user that intends 
###### *Role*
###### *Datastore*
- A collection of *datasets*
- A point of retreival for *datasets*
###### *Dataset*
- Any structured collection of *property-* or *attribute* *statements* of one or more *subjects*
- A list of *statements*
###### *Statement*
- The expression of a single *property* or *attribute* consisting of three parts:
  1. a *subject*, the thing which has a property or attribute
  2. a *predicate* naming the specific *property* or *attribute*
  3. an *object* representing the value of the *property* or *attribute*
###### *Subject*

 [^1]: In this exposition, *domain maps* have the type *D⟶ ℘(I)*, i.e. map from datasets to sets of terms in the index catalog. This is a concrete realization of how one might wish to index a datastore. This document could have chosen to abstract this to some index type parameter *θ* such that domain maps and range functions would have types *dₛ : D ⟶ θ* and *rᵢ : θ ⟶ D*, leaving the concrete realization of what constitutes *indexing* to specific implementations. `Bandana` is opinionated about what kind of indexing is appropriate for access-control use cases: namely the kind of indexing that annotates *archival artefacts* (i.e. objects that live in the *archival domain* such as pieces of paper, filing drawers, document binders or **datasets**) with *business concepts* or *references* to **objects in the business *domain***.

[^RO]:*Record Ontology* https://github.com/equinor/records

[^SCO]: `https://rdf.equinor.com/ontology/record/isInScope` (RDF predicate). A "Scope" is a set of (references to) business concepts/topics. As a topic it is interpreted as the *intersection* of  the referenced topics. 
[^FUSEKI]: *Apache Jena Fuseki* https://jena.apache.org/documentation/fuseki2/
[^NG]: *RDF Datasets* https://www.w3.org/TR/rdf11-concepts/#h2_section-dataset
[^GN]: See https://www.w3.org/TR/rdf11-datasets/#the-graph-name-denotes-the-named-graph-or-the-graph for some discussion about the semantic implication of this. In particular, the last paragraph of section 3.3.3
[^rdfGRAPH]: https://www.w3.org/TR/rdf-interfaces/#graphs (`Graph` as a *W3C Recommendation*)
[^jenaGRAPH]: https://jena.apache.org/documentation/javadoc/jena/org.apache.jena.core/org/apache/jena/graph/Graph.html (`Graph` as implemented by `Jena`)
[^rdfjsDATASET]: https://rdf.js.org/dataset-spec/#datasetcore-interface  (`Dataset` as defined by *RDFJS*)
[^jenaDATASET]: https://jena.apache.org/documentation/javadoc/arq/org.apache.jena.arq/org/apache/jena/sparql/core/DatasetGraph.html (`Dataset` as implemented by `Jena`)
[^rdfMATCH]: https://www.w3.org/TR/rdf-interfaces/#widl-Graph-match-Graph-any-subject-any-predicate-any-object-unsigned-long-limit (`match` as a *W3C Recommendation*)
[^rdfjsMATCH]: https://rdf.js.org/dataset-spec/#dom-datasetcore-match (`match` as defined by *RDFJS*)
[^jenaMATCH]: https://jena.apache.org/documentation/javadoc/arq/org.apache.jena.arq/org/apache/jena/sparql/core/DatasetGraph.html#find(org.apache.jena.graph.Node,org.apache.jena.graph.Node,org.apache.jena.graph.Node,org.apache.jena.graph.Node) (`match` as implemented by `Jena`)
[^ALG]: https://www.w3.org/TR/sparql11-query/#sparqlAlgebra (*SparQL Algebra*)
[^GSP]: https://www.w3.org/TR/sparql11-http-rdf-update/ (*Graph Store HTTP Protocol*)
[^SQP]: https://www.w3.org/TR/sparql11-protocol/ (*SPARQL Query Protocol)