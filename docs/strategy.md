# Strategy

<div style="background:white;">

![Very drawing!](scope_access_evaluation.svg)

</div>

### Authentication
PoC a minimal cryptographic authentication of requests using JWT/JWS

### Technology choices
- Use Fuseki triplestore
  - free
  - extensible
    - in particular, has extensibility API's for access control
  - already used in splinter
  - Query rewriting complex in comparison to base api 
