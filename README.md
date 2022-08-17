# couchbeans
Distributed reactive jvm environment with couchbase backend. 
Loads jvm bytecode and its metadata onto couchbase cluster and executes it by listening to DCP events.


# DCP doc creation event
- find all constructors that accept doc type as argument
- create all beans were created doc would be the only argument
- Repeat until all beans created or unable to create any beans:
  - using previously created beans as arguments, create more beans
  - store links from argument beans to created from them beans

# DCP doc mutation event
- in linked to mutated doc beans, find all methods with doc type as argument, and call them, storing returned beans into mutation context
- for each returned bean, call all methods that use that bean or that bean and mutated doc.

# DCP doc deletion event
- call destructors on the doc bean and all linked to it beans
- delete the beans
 
