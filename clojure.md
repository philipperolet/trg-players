## Clojure design / function parameters
### how to handle data common to multiple functions without parameter explosion
In OO, objects communicate, the parameter would become a field

In FP, functions (processes) operate on data, the parameter can be added to the relevant data structure:

- The data structure is passed along -- in the same way you have {this} or {self} in OO like python, so it's okay to have 1 more param;
- you can revisit the logic of data structures to regroup them and reduce the number of parameters (e.g. street + zip + city = address);
- another option is to have another arity for the function in which the default data is passed. This arity would not be idem potent (but can still be free of side effect).
