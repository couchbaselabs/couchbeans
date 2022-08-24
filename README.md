# couchbeans
(Under development)

Distributed reactive jvm environment with couchbase backend that turns a couchbase bucket into a self-mutating graph database using java class structures as graph definitions.
Loads jvm bytecode and its metadata onto couchbase cluster and executes it by listening to DCP events.

# (Very) Loose descripion of how this would work:
![Couchbeans graph events flow(4)](https://user-images.githubusercontent.com/807041/186480114-76e69b37-fd7c-45e8-a865-c994967397c3.png)

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
- run `gradle uploadCouchbeans` to upload classes from project jars onto a couchbeans bucket

## Launching a node
Use `com.couchbeans.DcpListener` main class to launch a node. 
Use same environment variables as with `com.couchbeans.BeanUploader` to configure couchbase connection.

## DCP doc creation event (implemented)
- find all constructors that accept doc type as argument
- create all beans where created doc would be the only argument
- Repeat until all beans created or unable to create any beans:
  - using previously created beans as arguments, create more beans
  - convert created beans to documents an store them on the cluster (which would trigger cascading doc creation events)
  - store links from argument beans to created from them beans

## DCP doc mutation event (implemented)
- if the bean is global, then get mutated bean and call any present setters for all changed fields 
- in parent beans, find all methods with names starting with "update" and doc type as argument, then call them, storing parent and returned beans into mutation context
- repeat for all parent and returned beans, propagating the event up the graph
- store all changed beans

## DCP doc deletion event
- call destructors on the doc bean and all linked to it beans (desctructors? in Java? what is that?!)
- delete the beans

## Bean link event
- Call methods that start with "linkTo" and accept the parent bean type 
- Handle the event by calling parent bean methods with names that start with "linkFrom" and accept the linked bean type
- Handle parent beans as changed and propagate the changes up the graph

## Bean unlink event
- Call methods that start with "unlinkTo" and accept the parent bean type
- Handle the event by calling this parent bean methods with names that start with "unlinkFrom" and accept the unlinked bean type
- Handle parent beans as changed and propagate the changes up the graph

## Long-running tasks
Applications should perform long-running tasks asynchronously. 
For example, `WebServer::setRunning(Boolean isRunning)` method that reacts to changes of `WebServer.running` boolean field, can start a new thread for web server's socker listener when the value is set to `true` and stop it otherwise.

## Node affinity
- the service should run on every node that runs data service
- DCP events should be processed on the node to which the document belongs

### Internal nodes
Internal nodes are nodes that run Couchbeans together with Couchbase data service.

### External nodes
External nodes are nodes that run Couchbeans without running Couchbase data service but still listen to DCP events to maintain global beans.
External nodes can be used to provide services outside of couchbase cluster, for example for load balncing or external system inegrations.
  
### Foreign nodes
Foreign nodes are applications that use Couchbeans as a library wihout starting DcpListener.
These nodes can only query beans, process local beans, and create or edit cluster beans, but not process their changes as they do not listen to DCP events.
Foreign nodes are intended to be used for:
- Collecting data 
- Graph editing

### Global beans
Beans marked with `@Global` annotation are processed differently:
- updates to singleton bean fields are processed on all nodes (although updates to linked beans are still processed on their corresponding nodes)
- singletons should be kept in memory at all times on all nodes.
- Global beans are always present in all bean update/creation contexts.

So, returning to the previous example, to launch a web-server on all nodes, mark `WebServer` bean with `@Singleton` and it will run on every node in the cluster that runs couchbeans.

### Local beans
- All beans under `java` package
- All beans marked with `@Local` annotation

Local beans are not stored on the cluster and are processed locally on the node.
Local beans can still be linked to other beans but these links will not appear on any other nodes.
Local beans are always present in all bean update/creation contexts that are being handled on the node.

### Internal beans
Internal beans are represented by protected classes and never handled or available on any other than internal nodes.

### External beans
Beans can be marked with `@External` annotation. 
External beans are global and processed only on external nodes.

### Foreign beans
Foreign bean is a bean object that is loaded on a node other than internal node that owns bean's vbucket.
Couchbeans bean uploader instruments the following bean methods, changing their behavior on nodes other than the node that should be handling bean DCP events:
- Setter methods are forced to only set the field value
- `linkTo` and `linkFrom` methods create links instead of processing them
- `update...` methods store provided beans onto the cluster and always return `null`
