# couchbeans
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
- in linked to mutated doc beans, find all methods with doc type as argument, and call them, storing returned beans into mutation context
- store each returned bean and call all methods that use that bean or that bean and mutated doc.
- store all changed beans
- unload objects from memory

## DCP doc deletion event
- call destructors on the doc bean and all linked to it beans
- delete the beans
 
