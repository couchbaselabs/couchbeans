# couchbeans
(Under development)

Distributed reactive jvm environment with couchbase backend. 
Loads jvm bytecode and its metadata onto couchbase cluster and executes it by listening to DCP events.

# (Very) Loose descripion of how this would work:
## Uploading bean definitions
### Via CLI
This project provides `com.couchbeans.BeanUploader` main class that accepts paths to directories, jars and class files and recursively uploads class definitions and metadata as bean definitions onto couchbase cluster. Use environment to configure bean destinations:
```
CBB_CLUSTER="localhost"
CBB_USERNAME="Administrator"
CBB_PASSWORD="password"
CBB_BUCKET="default"
CBB_SCOPE="_default"
```
### Via gradle task
- Publish provided gradle plugin to local maven repository by running `gradle publishPluginMavenPublicationToMavenLocal`
- add maven local as plugin management repository in your `settings.gradle` (see `example/settings.gradle`)
- add plugin with id `couchbeans` and version `0.0.1` onto your `build.gradle` (see `example/build.gradle`)
- configure couchbase connection parameters using `cluster`, `username`, `password`, `bucket` and `scope` parameters of `couchbase` project extension (see `example/build.gradle`)
- run `gradle uploadCouchbeans` to 
## DCP doc creation event (implemented)
- find all constructors that accept doc type as argument
- create all beans where created doc would be the only argument
- Repeat until all beans created or unable to create any beans:
  - using previously created beans as arguments, create more beans
  - convert created beans to documents an store them on the cluster (which would trigger cascading doc creation events)
  - store links from argument beans to created from them beans

## DCP doc mutation event (implemented)
- load mutated bean and call any present setters for all changed fields 
- in parent beans, find all methods with doc type as argument, and call them, storing returned beans into mutation context
- repeat for all parent beans, propagating the event up the graph
- store all changed beans

## DCP doc deletion event
- call destructors on the doc bean and all linked to it beans (desctructors? in Java? what is that?!)
- delete the beans

## Node affinity
- the service should run on every node that runs data service
- DCP events should be processed on the node to which the document belongs

## Long-running tasks
Applications should perform long-running tasks asynchronously. 
For example, `WebServer::setRunning(Boolean isRunning)` method that reacts to changes of `WebServer.running` boolean field, can start a new thread for web server's socker listener when the value is set to `true` and stop it otherwise.

## Singletons
Beans marked with `@Singleton` annotation are processed differently:
- updates to singleton bean fields are processed on all nodes (although updates to linked beans are still processed on their corresponding nodes)
- singletons should be kept in memory at all times on all nodes.

So, returning to the previous example, to launch a web-server on all nodes, mark `WebServer` bean with `@Singleton` and it will run on every node in the cluster that runs couchbeans.
