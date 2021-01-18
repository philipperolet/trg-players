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


## Résultats
Tous les résultats sont dans doc/xps
- 1 -> résultat en utilisant le move-position codé en clojure
- 2 -> résultat en utilisant le movePosition en java
- 1-aborted -> résultat tronqué, gardé pour comparaison de consistence avec 1
### Code
```
lein run -m experiments.clj/te-impl-speed 25 100
```
en activant et désactivant le flag pour move-position dans events.cljc

Interprétation:
```
cat 2021-01-15-te-impl-speed-1.txt | grep "Data\|Time \|Mean"
```

### Consistence des expériences (temps, steps)
- Steps: Oui. on a bien exacte concordance des nombres de steps -> la répétabilité aléatoire fonctionne
- Temps: Ecart de 10% (relativement exact) entre les 2 runs, le second semble 10% plus rapide
=> les conclusions probante sur les temps inter-expériences doivent porter sur + de 10/20% d'écart

### Performances
#### Fréquents bloquages de dag-node & java-dag
Si random-min n'est pas activé, 2 à 10% des parties "bloquent" (toujours rien après 50000 steps). Ça n'est pas observé avec te-node

Encore plus observé avec dag-node (d'où l'absence d'xp dag node sans random-min ou wall-fix). Sur les 1-2 jeux observés, le player fait des aller-retours

#### Java-dag impl la plus rapide
Lorsque les blocages sont évités (random-min), java-dag clairement la plus rapide, facteur 2 sur le temps p/rap à dag-node (qui a sensiblement le mm nombre de steps)

##### Bizzarrerie
En revanche, java-dag va plus vite que te-node **parce qu'il a 3 fois moins de steps**. Le temps par step est bizarrement plus élevé que celui de te-node.

##### Rogue XPs
En augmentant la taille du board, pour comparer te-impl sans optim VS java-dag avec random-min + wall-fix (taille de board 40)
	- on se maintient à en gros 3 fois plus rapide
	- alors que bizarrement l'écart de steps augmente : 9 * plus de steps pour te-node (donc les steps sont 3 * plus lentes...)

#### Random-min bonne optiomisation
Step unique moins performante, mais réduction du nombre total de steps suffisant pour au global réduire le temps d'éxécution (divise par 2 ou 3 le nb de steps)

#### Wall-fix bonne optimisation
Remarque similaire à random-min bien que moins tranchés

#### MovePosition en java : pas d'amélioration
Les temps sont même jusqu'à 20% plus mauvais (20% pas très significatif, cf le par. consistence des xps, mais parfois un peu meilleur -- bottom-line : pas utile

#### Point d'attention sur la mesure temps
On a observé parfois que le CPU n'est utilisé qu'à 10% pendant certaines parties; et d'autres fois 100%. => influe sur la moyenne temporelle (car on n'a qu'une moyenne).

## Next steps
Suite aux résultats :
- on enlève les options random-min et wall-fix, on les active en permanence
- on enlève le move-position en java
- java-dag-node devient l'implémentation standard, on supprime dag-node

