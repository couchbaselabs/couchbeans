# couchbeans
Distributed reactive spring boot environment with couchbase backend. 
Loads jvm bytecode and its metadata onto couchbase cluster and executes it by listening to DCP events.


# DCP doc creation event
- find all constructors that accept doc type as argument
- create all beans were created doc would be the only argument
- Repeat until all beans created or unable to create any beans:
-- using previously created beans as arguments, create more beans
-- store links from argument beans to created from them beam

# DCP doc mutation event
- in linked to mutated doc beans, find all methods with doc type, and call them, storing returned beans into mutation context
- for each returned bean, call all 
 
