# Couchbase Beans, aka Couchbeans
(POC, Under development)

Distributed reactive jvm environment with couchbase backend that turns couchbase buckets into self-mutating graph databases using java class structures as graph definitions.
Loads jvm bytecode and its metadata onto couchbase cluster and executes it by listening to DCP events.

# Examples
## Echo server
Check out the `echo-server` example in the `examples` directory: https://github.com/couchbaselabs/couchbeans/blob/main/examples/echo-server/lib/src/main/java/cbb/servers/EchoServer.java

When uploaded to a CBB cluster, the `EchoServer` global bean will be deployed on all cluster nodes (except for nodes configured to ignore it), where it will wait for its `running` field to be set to `true` and then it will open an ICMP echo server on port `port` which will register all recieved requests as `EchoRequest` beans linked to the `EchoServer` bean.
## Chat server
Under development
## Grapql server
Graphql server example is not uploadable at the moment, but intended to operate similarly to echo server example.

# (Very) Loose descripion of how this would work:
## Document mapping
Couchbeans maps documents onto java beans using collection names, for example a bean of class `com.example.Example` will be stored as a json document into collection `com-example-Example`. 

## Bean ownership 
Depending on node type, bean scope and node and bean tags (described below), a node can either "own" the bean or load it as a "foreign" bean. Specific differences between "owned" and "foreign" beans are described below.

### Field setters and value handlers
Couchbeans will deffer setter calls made on foreign beans so that any associated bean call is executed only on the node that owns the bean. 
When processing a DCP message:
- couchbeans will uses setters that match these patterns (in the order of preference):
 - `<fieldname>(<fieldtype>)`
 - `set<Fieldname>(<fieldtype>)`
- depending on the field value type, couchbase will also invoke value handler methods that match these rules:
 - For boolean:
   - when `true`: `when<Field>()`
   - when `false`: `whenNot<Field>()`
  - for any other type: `when<Field>Is<Value>()` (i.g.: `whenCounterIs0()`)

> would be nice to implement more complicated matching like `whenCounterIsAbove10`, `whenCounterIsNot0`, etc...


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
Use `CBB_NODE_TAGS` environment variable when starting a node to provide a comma-separated list of tags for the node.
Use `@TargetNodes` annotation on a bean class to specify tags that a node should have in order to own the bean.
Use `CBB_NODE_IGNORE_PACKAGES` to provide a comma-separated list of package names that should never be owned by the node.

## Bean scopes
| Scope | Description | Example |
| -- | -- | -- |
| MEMORY | Memory beans belong only to the node on which they were created and are never stored on the bucket but still can be linked with other beans | An API request that is being processed |
| BUCKET | Just your regular friendly neighborhood graph vertice stored on the couchbase bucket | Any data bean |
| NODE | `NODE` beans are created for each node connected to the bucket and owned by that node | NodeInfo beans |
| GLOBAL | `GLOBAL` beans are created when their types are uploaded onto the cluster | Application configuration and status bean |

By default, beans belong to the `BUCKET` scope.
Bean scopes are mutually exclusive.
Use `@Scope` annotation to set bean scope at a class level. 
To set bean scope dynamically, define `public BeanScope scope()` method.

### Foreign beans
Foreign bean is a bean object that is loaded on a node other than internal node that owns bean's vbucket.

#### Setter instrumentation (implemented)
To avoid running setter logic on foreign beans, all bean setters are instrumented so that, when invoked on a foreign bean, they just set the field value without running actual method code (the code will be executed later on the owning node).

## Indexes
![Screenshot from 2022-08-26 12-59-54](https://user-images.githubusercontent.com/807041/186955549-8e2b7f95-4284-4fdb-885e-b545c77576fe.png)

Couchbeans creates primary indexes automatically.
To create secondary index on bean fields, use `@Index` annotation.
