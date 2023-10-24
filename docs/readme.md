# Bandana
*Access control for index-addressable datasets*

## Theory
Given a [datastore](#datastore) $\mathbb{S}$ of [datasets](#dataset) $D$ and a catalog of *terms* $I$, we say that $\mathbb{S}$ is *indexed* by $I$ iff
- each $x\mathrel{:}D ∈ \mathbb{S}$ is annotated by one or more *terms*  $t_1,\dots, t_n ∈ I$ given by a *domain map* $d_\mathbb{S}\colon D ⟶ \wp(I)$
- *domain maps* $d_\mathbb{S}$ define an inverse *range function* $r_I\colon\wp(I)⟶D$ such that
$$
\begin{align}
x \subseteq r_I &∘ d_\mathbb{S}(x) \\
t_1,\dots,t_n \in d_\mathbb{S} &∘ r_I(\{t_1,\dots,t_n\}) \\
\end{align}
$$

For access-control usecases we are mainly interested in 2 kinds of *range functions*:
For a given set of *terms* $t_1,\dots,t_n ∈ A$ and a corresponding *domain map* $\overleftrightarrow{d_\mathbb{S}}$
- an *injective* range function $\overrightarrow{r_I}$ maps to those datasets $x\in\mathbb{S}$ that are annotated with *all* terms $t∈ A$.  
  More formally:
  $$ \overrightarrow{r_I}(A) = \bigcup_{x\in\mathbb{S}}{∀t \mathrel{∈} A \mathrel{.} t∈ \overrightarrow{d_\mathbb{S}}(x)} $$
  - If $\overrightarrow{d_\mathbb{S}}$ maps to terms of $I$ that are (essentially) *business topics* then $\overrightarrow{r_I}$ ranges over datasets *at the intersection* of a given set of topics, i.e. datasets that are simultaneously *about* several topics (*terms*).
  This of interest to the problem of access-control for two main reasons:
    1. Datasets are usually (and in the case of `bandana`) topically organized/annotated as a result of business requirements that precede the requirement for fine-grained access-control (such as regular business operations)
    2. Many common access-control usecases essentially boil down to granting certain users access to information at the intersection of two or more topics, for e.g. "plumbing at facility X" or "finances of project Y contractor Z".

- a *surjective* range function $\overleftarrow{r_I}$ maps the datasets $x\in\mathbb{S}$ where *every* annotated term $t∈ d_\mathbb{S}(x)$ is in the given set of terms, $t∈ A$
  More formally:
  $$ \overleftarrow{r_I}(A) = \bigcup_{x\in\mathbb{S}}{∀t∈ \overleftarrow{d_\mathbb{S}}(x) \mathrel{.} t \mathrel{∈} A} $$
  - If $\overleftarrow{d_\mathbb{S}}$ maps to terms that correspond to *priveleged access roles* (such as "securtity clearance") then $\overleftarrow{r_I}$ ranges over datasets $x ∈ \mathbb{S}$ where a user has the required set of access priveleges (*terms*).




## Strategy

### Authentication
PoC a minimal cryptographic authentication of requests using JWT/JWS

### Technology choices
- Use Fuseki triplestore
  - free
  - extensible
    - in particular, has extensibility API's for access control
  - already used in splinter
  - Query rewriting complex in comparison to base api 

## Implementation
### As a Fuseki plugin
- in the form of a .jar
- with configuration file syntax for stuff
### As a HTTP middleware

### As a DATASET API implementation/middleware

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

 [^1]: Note that the inverse is not stipulated, i.e. 
       