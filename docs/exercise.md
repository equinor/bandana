# An Exercise of (Access) Control
In order to get a deeper understanding of exactly how this code comes together, mere words might not suffice. Provided here is a small exercise intended to make the reader interact directly with the source code of `Bandana` to aid understanding.

### Primary Goal
The primary goal of this exercise is for the reader to implement their own access control logic on a `Fuseki` triplestore. The access control logic itself will take the form of a `Predicate` over `Quads` (i.e. a function that takes a `Quad` and returns a `Boolean`). This is intended to motivate the reader to wade through all the messy details of `Bandana`. Readers are encouraged to dream up their own access control logic, but those who prefer wading in messy details to chasing dreams may refer to this footnote[^PG].

### Implementation roadmap
1. Create example configuration file (start declarative!)
2. Start `Fuseki` from code using our config file (nothing happens, duh)
3. Implement config parsing
4. Register *credentail ⟶ policy evaluator* factory
5. Implement *policy evaluator* (Primary Goal goes in here!)
6. Marvel

[^PG]: PRIMARY GOOOOOAL PREDICATE CODE