# 2021-01 : te-impl-speed

3 implémentations du fonctionnement du tree-exploration player existent: te-node, dag-node, java-dag-node

Il y a 3 tunings possibles :
- wall-fix = if wall encountered, set freq to infinity so this path is never again selected
- random-min = chose randomly what child to explore when they have the same frequency
- move-position = is implemented in Clojure and in Java

L'xp doit trouver quel est le plus rapide en temps et en nombre de steps.
Par rapport à la baseline :
- wall-fix devrait < le nb de steps sans augmenter le temps/step
- random-min devrait < le nb de steps en augmentant le temps/step suffisamment faiblement donc diminution
- movePosition en java devrait diminuer le temps total sans baisser le nb de steps

L'ordre de rapidité des implems devrait théoriquement être te-node (lent) < dag-node < java-node (rapide).

On va faire toutes les expériences correspondantes. 

Les expériences groupées par paramètres identiques étant parallélisées, une mesure de temps sur une expérience individuelle n'a pas de valeur (car le temps peut être arbitrairement court ou long selon le temps accordé par l'os au thread de l'xp). La seule mesure pertinente est de prendre le temps total et diviser par le nombre d'expériences.
