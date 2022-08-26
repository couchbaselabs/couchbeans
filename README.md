# couchbeans
(POC, Under development)

Distributed reactive jvm environment with couchbase backend that turns couchbase buckets into self-mutating graph databases using java class structures as graph definitions.
Loads jvm bytecode and its metadata onto couchbase cluster and executes it by listening to DCP events.

# Example graph application structure
![Couchbeans graph events flow(6)](https://user-images.githubusercontent.com/807041/186948772-f986e6a7-3f86-4544-9dee-a92fa9fb3492.png)


# (Very) Loose descripion of how this would work:
## Document mapping
Couchbeans maps documents onto java beans using collection names, for example a bean of class `com.example.Example` will be stored as a json document into collection `com-example-Example`. 

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

## Events
### Event propagation (implemented)
Events are propagated on all paths that include event source bean.
There is no guaranteed order in which paths are processed,
 but inside every path events propagate from the topmost bean in the path.
Events that are coming from child nodes and events coming from parent nodes mapped on different bean methods.
Some of the events may also be mapped onto the source bean as if it was a child node.

### Graph events
| Event name | Child node handler method name pattern | parent node handler method name pattern | mapped to source? |
|---|---|---|---|
| node updated | updateParent% | updateChild% | no |
| node linked | linkTo% | linkChild% | yes |
| node unlinked | unlinkFrom% | unlinkChild% | yes |

## Method matching (implemented)
Couchbeans matches methods and constructors against graph paths using method argument lists as path templates.
A single method argument matches all paths to beans of the argument type with length equals to 1.
Multiple arguments match paths literally, for example method with arguments `TypeA` and `TypeB` will match all paths with length 2 that first go through a `TypeA` bean and then end at a `TypeB` bean.
`Object` arguments can be used to match paths through beans of any type.
Array arguments can be used to match paths that go through several beans of the same type, i.g.: `(Folder[] path, File file)`; thus, `Object[]` parameters will match any paths of any length.

## Long-running tasks
Applications should perform long-running tasks asynchronously. 
For example, `WebServer::setRunning(Boolean isRunning)` method that reacts to changes of `WebServer.running` boolean field, can start a new thread for web server's socker listener when the value is set to `true` and stop it otherwise.

## Node affinity
### Node types 
| Node Type | Description | Example |
| -- | -- | -- |
| Internal | Internal nodes are nodes that run Couchbeans together with Couchbase data service | A node that is a part of a signal processing cluster |
| External | External nodes are nodes that run Couchbeans without running Couchbase data service but still listen to DCP events to maintain global beans | A node that provides GraphQL service |
| Source | Source nodes are applications that use Couchbeans as an sdk library wihout starting DcpListener | Sensor data collection agent that stores the data as graph vertices |

### Bean scopes
| Scope | Description | Example |
| -- | -- | -- |
| Global | Global beans are "owned" by all nodes connected to the bucket | Application configuration and status bean |
| External | External beans are processed only on External nodes | GraphQL server configuration and status bean |
| Normal | Just your regular friendly neighborhood graph vertice | Any data bean |
| Local | Local beans belong only to the node on which they were created and are never stored on the bucket | An API request that is being processed |

By default, beans belong to the `Normal` scope.
Bean scopes are mutually exclusive.
Use `@Scope` annotation to set bean scope. 

### Foreign beans
Foreign bean is a bean object that is loaded on a node other than internal node that owns bean's vbucket.

#### Setter instrumentation (implemented)
To avoid running setter logic on foreign beans, all bean setters are instrumented so that, when invoked on a foreign bean, they just set the field value without running actual method code (the code will be executed later on the owning node).

## Indexes
![Screenshot from 2022-08-26 12-59-54](https://user-images.githubusercontent.com/807041/186955549-8e2b7f95-4284-4fdb-885e-b545c77576fe.png)

Couchbeans creates primary indexes automatically.
To create secondary index on bean fields, use `@Index` annotation.
