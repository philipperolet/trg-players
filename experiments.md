# 18/12/2020
Experiments on :
- wall-fix = if wall encountered, set freq to infinity so this path is never again selected
- random-min = chose randomly what child to explore when they have the same frequency

Observation : 
- size 26, 10 xps : wall fix should reduce number of steps but instead increases it
Hypothesis :
- Deterministic behaviour in choosing children leads to excessive required steps on some boards, skewing results if done on not enough boards

Test #1 : this should not be observed on larger nb of xps, i.e. 100 xps
Test #2 : randomizing children selection should lead back to wall fix dominance over non-wall fix

Test #1 : 100 boards of size 25, on cloud, with & without wall fix
Test #2 : same but with randmin activated
Test #3 : while preparing to do other stuff, do it again with 250 boards and another seed
