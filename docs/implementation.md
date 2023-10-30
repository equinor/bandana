# Implementation

## Running Fuseki with Bandana enabled
Run `Fuseki` with `Bandana` in the classpath and `Fuseki's` plugin-module will use the *java service loader*[^SL] to locate `Bandana's` plugin implementation and call its lifecycle hooks during server initialization.

## Library plugin
`Bandana` is a library plugin for `Fuseki`. It uses the extension interface `FusekiAutoModule`[^AM] in order to inject `Bandana` implementations into three main parts of the Fuseki Server lifecycle:
1. Server configuration
2. Http request routing
3. Query evaluation

### Server Configuration
`Fuseki` server configuration is split into a configuration stage and a build stage. 
###### Configuration Stage
During the configuration stage, `Bandana` extends configuration file parsing with directives for
- Authentication middleware in [AssemblerAuthFilter](/bandanaModule/src/main/kotlin/bandana/AssemblerAuthFilter.kt), including parameters for JWT/JWS auth.
  - Authentication can be configured server-wide, per datastore or per endpoint.
- Secured `DatasetGraphAccessControl`[^DAC] configuration in [AssemblerRoleRegistry](/bandanaModule/src/main/kotlin/bandana/AssemblerRoleRegistry.kt), which corresponds *role-claims* with *evaluation policy implementations*.

###### Build Stage
During the build stage, [BandanaModule](/bandanaModule/src/main/kotlin/bandana/BandanaModule.kt) will replace the `Action Processor` of each operation type (*query*, *update*, get / put / post) on endpoints that target a `DatasetGraphAccessControl`[^DAC] using `Bandana's` [RoleRegistry](/bandanaModule/src/main/kotlin/bandana/RoleRegistry.kt) as its `AuthorizationService`[^AS].

Due to this separation into stages, `Bandana` can be configured in Java code that references `Fuseki` and `Bandana` directly (as seen in [BandanaTest](/bandanaModule/bin/test/bandana/BandanaTest.kt)), as well as from a `Fuseki` configuration file passed as an argument to the `FusekiMain` command line utility[^cmd].

### Http Request Routing
> pad with words about JWT validation
### Query Evaluation
> pad with words about SparQL query evaluation terminating in index lookups filtered by ScopeFilter.kt


[^AM]: (*FusekiAutoModule.java*) https://github.com/apache/jena/blob/main/jena-fuseki2/jena-fuseki-main/src/main/java/org/apache/jena/fuseki/main/sys/FusekiAutoModule.java
[^SL]: (*Java service loader*) https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html
[^DAC]: (*DatasetGraphAccessControl.java*) https://github.com/apache/jena/blob/main/jena-fuseki2/jena-fuseki-access/src/main/java/org/apache/jena/fuseki/access/DatasetGraphAccessControl.java
[^AS]: (*AuthorizationService.java*) https://github.com/apache/jena/blob/main/jena-fuseki2/jena-fuseki-access/src/main/java/org/apache/jena/fuseki/access/AuthorizationService.java
[^cmd]: (*Fuseki command line utility*) https://jena.apache.org/documentation/fuseki2/fuseki-main