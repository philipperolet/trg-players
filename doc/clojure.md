# Unsorted
- a change in a spec requires a new call to instrument to be taken into account
  - make sure there is actually a call to instrument somewhere (sometimes randomly instrumented nonetheless)
- a map is a coll
- thrown? is part of is
- ret specs are not checked by instrument, they are meant for doc & test.check
- partial -> oui comme tu penses et pas comme tu veux. sur le début.
- let values are evaluated when run; but if the value is a function, the variables in the function (e.g. derefing an atom) are not -- all the bound values are bound, not free ;).
- don't run a global-state-changing function in a namespace you'd import, it can have unexpected effects
- Macro ~ inline pour les parties de code à vraiment optimiser
- lein run & fails because of stdin, add </dev/null
- sometimes cider-ns-refresh fails -> try `lein clean`
- update-in / get-in work on vec but not on other colls

# Common knowledge
- vector is variadic. vec works with a coll
- How does one do a cartesian product in clojure?
  - ```doseq```

### functions, var, dispatch
- vars are stable references to values
- functions are "values", they can be stored in vars. 
- using defn, a function is stored in the var with the given name (automatically namespaced, so a fully qualified named although accessible without the full qualif in the current namespace)
- the var object is the reference. If you type the name of a var in the repl, it evaluates it into its value. However, the dispatch operator #'x, equivalent to (var x), returns the var object

### with-redef
TLDR-> to call the original function within a with-redef, do not use its qualified name--the var has been redefed, it refers to the redefed fn. Use an enclosing `let` or any other way to bind the function (the value) to another name before the with-redef.

- using with-redef, the var object will point to another function (given during with redef) within the with-redef scope. So, if the function given to with-redef (the "redefed" fn) uses said var object (global) in its definition, the call will be "recursive", it will call the redefed fn and not the original fn--usually not what we want unless the original function is recursive. 
- But if we put the with-redef within a `(let [new-name original-fn]` scope, then during evaluation, original-fn is evaluated to the funtion it refers, and new-name directly refers now to the original fn *value*. Thus a call to `new-name` in the redefed fn will correctly call the original fn, and not call recursively the redefed one.
- Now, trickyness, if we do `(let [new-name #'original-fn]`, thus letting new-name refer to the *var object*, not its value (which would be original-fn), then when we use with-redef, the value of the var is redefed, so if we use new-name in a function call inside the with-redef, *it will refer to the redefed fn*, not the original one.

- with-redefs does not impact recur calls

# Clojure design / function parameters

Pass only params needed -- not the full object
Use destructuring when you'd want a let -- not every time

### how to handle data common to multiple functions without parameter explosion
In OO, objects communicate, the parameter would become a field

In FP, functions (processes) operate on data, the parameter can be added to the relevant data structure:

- The data structure is passed along -- in the same way you have {this} or {self} in OO like python, so it's okay to have 1 more param;
- you can revisit the logic of data structures to regroup them and reduce the number of parameters (e.g. street + zip + city = address);
- another option is to have another arity for the function in which the default data is passed. This arity would not be idem potent (but can still be free of side effect).


# Speed and profiling
- st/instrument may slow down a lot
- parallel timing effect : when timing execution that consumes all cpu resources, individual parallel execution times may appear slower than sequential execution times while the overall time to perform everything is smaller or equal in the case of parallel processing (the subway sandwich effect)

# Specing & testing

Test things you're not sure of. Tests are a tool not an end.

## What to test
- public interface
- private functions that you think are hard and need testing

## What to spec
- spec pure ones hard to test (and gen test them)
- spec those who need good doc on input (and instr. them but not necessarily gen test them), often public interface

https://clojureverse.org/t/clojure-spec-instrumenting-functions/2619/13

## Generative vs case-based
The one with the best *effort/relevant-coverage* ratio.

## Testing only public vs also private
Case for only public -> to avoid test suite become impl detail dependant : "only test the public interface". Test the private functions via the public interface.

Case for also private -> it can get hard / less legible to test private functions via public int rather than directly.

## TDD
Mostly imagined for OO, since testing can be very complex in OO when not thought of a priori during design.
For FP, and FCIS design, it's less necessary and more a burden

# Formatting
- No empty lines in function defs, even between arities -- except in let/cond constructs with long paired bindings
- long function call : stacking args is ok but counts as 1/2 level of nesting
- let counts as 1/2 level of nesting, threading too
