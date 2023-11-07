# Handover of Bandana
Completion of all the items on the list below and reviewing of a pull request indicates a successful and complete handover.

- [x] Overall introduction of `Bandana` has been given to `SSI`  
      Done by Eirik at meeting Oct 20 with Dag, Johannes, and Henrik
- [ ] Admin Role of Github repo transferred to `SSI`
  - [x] 1. `SSI` team promoted or added as Admin
  - [ ] 2. Dugtrio demoted / removed from team access list
- [x] Make SSI owners of Azure App Registration and Enterprise Application
      DHOVL and JOETE made owners on Fri, Oct 27. 2023
- [x] Transfer ownership of Snyk to `SSI`  
    No Snyk present, should be added
- [x] Introduction to fuseki's module system including service loader  
    Done by Martin at meeting Oct 20 with Dag, Johannes, and Henrik
- [x] Introduction to JOSE / JWS authentication on fuseki http server  
    Done by Eirik at meeting Oct 20 with Dag, Johannes, and Henrik
- [x] Introduction to relevant parts of Apache Jena (Fuseki) code base  
    Done Eirik at meeting Oct 20 with Dag, Johannes, and Henrik
- [x] Introduction to how bandana solves policy enforcement (PEP) on a triple store  
    Done Eirik at meeting Oct 20 with Dag, Johannes, and Henrik
- [ ] Dugtrio onboards `SSI` to their threat modelling if present
- [x] Dugtrio highlights smelly code to `SSI` if present
    In Fuseki's roleregistry the only parameter we have access to in order to get a SecurityContext is a string. We therefore include all roles as a newline separated string in this parameter. This should have been a less stringly typed datastructure. 
- [x] Dugtrio highlights their vision for further work to `SSI`
    Make a frontend team use bandana. This envolve communcation with frontteam and their needs + communcation with a authorization provider, for example Spine Auth, Fusion's AD-tree solutions or AccessIt. It is important to note that Bandana solve a different problem than the 3 authorization providers and it is made to work together with such a provider.
- [x] At least one contribution to the repository has been made by a member of the `SSI` team.
      Changes has been made here https://github.com/equinor/bandana/pull/5 . They are not merged, but sufficient to check this box
- [ ] At least one resource on the `SSI` has released a new version of `Bandana` and used it in spine splinter's dev environment using git submodule

