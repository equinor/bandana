# Bandana
> *Access control for index-addressable datasets*

## Theory
Given a [datastore](#datastore) $\mathbb{S}$ of [datasets](#dataset) $D$ and a catalog of *terms* $I$, we say that $\mathbb{S}$ is *indexed* by $I$ iff
- each $D ∈ \mathbb{S}$ is annotated by one or more *terms*  $t_1,\dots, t_n ∈ I$ given by a *domain map* $d_\mathbb{S}: D ⟶ \wp(I)$
- *domain maps* $d_\mathbb{S}$ define an inverse *range function* $r_I:\wp(I)⟶D$ such that
$$
\begin{align}
D \subseteq r_I &∘ d_\mathbb{S}(D) \\
t_1,\dots,t_n \in d_\mathbb{S} &∘ r_I(\{t_1,\dots,t_n\}) \\
\end{align}
$$





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
       