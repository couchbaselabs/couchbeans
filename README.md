# couchbeans
(Under development)
Distributed reactive jvm environment with couchbase backend. 
Loads jvm bytecode and its metadata onto couchbase cluster and executes it by listening to DCP events.

# (Very) Loose descripion of how this would work:
## DCP doc creation event
- find all constructors that accept doc type as argument
- create all beans were created doc would be the only argument
- Repeat until all beans created or unable to create any beans:
  - using previously created beans as arguments, create more beans
  - convert created beans to documents an store them on the cluster (which would trigger cascading doc creation events)
  - store links from argument beans to created from them beans
- unload objects from memory

## DCP doc mutation event
- load mutated bean and call any present setters for all changed fields
- in linked to mutated doc beans, find all methods with doc type as argument, and call them, storing returned beans into mutation context
- store each returned bean and call all methods that use that bean or that bean and mutated doc.
- store all changed beans
- unload objects from memory

## DCP doc deletion event
- call destructors on the doc bean and all linked to it beans
- delete the beans

## Node affinity
- the service should run on every node that runs index service
- DCP events should be processed on the node to which the document belongs

## Long-running tasks
Applications should perform long-running tasks asynchronously. 
For example, `WebServer::setRunning(Boolean isRunning)` method that reacts to changes of `WebServer.running` boolean field, can start a new thread for web server's socker listener when the value is set to `true` and stop it otherwise.

## Singletons
Beans marked with `@Singleton` annotation are processed differently:
- updates to singleton bean fields are processed on all nodes (although updates to linked beans are still processed on their corresponding nodes)
- singletons should be kept in memory at all times on all nodes.

So, returning to the previous example, to launch a web-server on all nodes, mark `WebServer` bean with `@Singleton` and it will run on every node in the cluster that runs couchbeans.
