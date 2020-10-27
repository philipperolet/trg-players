# Unsorted
- a change in a spec requires a new call to instrument to be taken into account
  - make sure there is actually a call to instrument somewhere (sometimes randomly instrumented nonetheless)
- a map is a coll
- thrown? is part of is
- ret specs are not checked by instrument, they are meant for doc & test.check
- partial -> oui comme tu penses et pas comme tu veux. sur le début.
- let values are evaluated when run; but if the value is a function, the variables in the function (e.g. derefing an atom) are not -- all the bound values are bound, not free ;)

# Clojure design / function parameters

Pass only params needed -- not the full object
Use destructuring when you'd want a let -- not every time

### how to handle data common to multiple functions without parameter explosion
In OO, objects communicate, the parameter would become a field

In FP, functions (processes) operate on data, the parameter can be added to the relevant data structure:

- The data structure is passed along -- in the same way you have {this} or {self} in OO like python, so it's okay to have 1 more param;
- you can revisit the logic of data structures to regroup them and reduce the number of parameters (e.g. street + zip + city = address);
- another option is to have another arity for the function in which the default data is passed. This arity would not be idem potent (but can still be free of side effect).

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
The one with the best effort/relevant-coverage ratio.

## Testing only public vs also private
Case for only public -> to avoid test suite become impl detail dependant : "only test the public interface". Test the private functions via the public interface.

Case for also private -> it can get hard / less legible to test private functions via public int rather than directly.

## TDD
Mostly imagined for OO, since testing can be very complex in OO when not thought of a priori during design.
For FP, and FCIS design, it's less necessary and more a burden

