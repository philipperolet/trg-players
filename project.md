# Todo
- système pour enregistrer facilement et récupérer facilement les résultats
- résultats de random, d'exhaust, de tree-expl
- exhaustive-senses -- et résultats
- tree-expl senses -- et résultats

# Backlog  - v0.2.6 - M0.0.2
TBD

# Icebox
## Vitesse
- speed tests on mul -> to be redone later (possibly good surprises: 0.1ms for 1Mops => ~ 10GF ok)
- get 10x speed improvements (current = 2.5 GFlops)

- Perf théorique de l'ordi : 4 coeurs, freq. 1.8 Ghz, supposément 32 float ops / cycle => 4 * 1.8 * 32 ~ 220 GFlops
  - voir comment pousser un peu la perf, ton core i7 peut monter à 4.6Ghz
  
- parvenir à des couches de 10K neurones qui fonctionnent
  - se rapprocher de la perf théorique de ~300GFlops
  => déjà atteindre 40GFlops, soit 10GFlops/coeur, soit 10MFlop/ms/coeur
  - çad avec 4 couches intermédiaires de 1000 neurones, on doit faire 1000 itérations en XX

## Autre
- feat : board seed specified as cli option
- compare speed on aws machine vs on your machine
- speed : use a C routine to make mul! fast (& other ops), via JNI?
- feat : make it 30 times faster using GPU
- option to store game & player data in a db for random player
  - do it via the seed it's better
- * for exhaustive player
- tools to inspect games
- paranoia session : mettre tout sur un git perso dans un serveur ovh redondé

## Refactorings
- get-test-world in all tests
- use fixtures for test instr/uninstr
- :unstrumented in meta rather than body for utils.testing.deftest

## Colder
- Add player using reinforcement learning - basic 
- Visu de jeu sur le net
- Then use DL in a basic way to model part of it
- then look up DL players
- then look up alphazero
- rewrite random using player senses
- rewrite exhaustive using player senses
- rewrite tree-exploration using player senses


# Changelog
### v0.2.5 | m0.0.1-reflexes
- Added arcreflexes for fruit eating and motoinhibition
- Added reflex for random movement
- Changed movement computation and allowed nil movements
- Fixed weight initialization issues
### v0.2.4 |  m0.0.1-halfway
- **Motoneurons** alpha version (not yet as in arch docs)
- **Activation** as in arch docs
- **Senses** as in arch docs
- **m00** player impl using all those

- Use of neanderthal lib for fast computation, various speed tests
- Dummy Luno player impl., for basic testing purposes

### v0.2.3
- feat: add game level to run options
### v0.2.2
- ref: move-position java est supprimé
- feat: supprime param tuning, intègre wall-fix & random-min par défaut
- ref: supprime dag-node, met java-dag impl par défaut pour tree-xplo
- xp: java-dag, random-min, wall-fix, clojure-based move-position sont les plus rapides
- ref: séparer les utils des xps de xp1000
- fix: mzero.utils.xp/std retourne -1.0 au lieu d'une erreur lorsqu'il n'y a qu'une mesure
- fix: assure qu'utils.xp/measure exécute les mesures lorsqu'on l'appelle
- fix: MovePosition.java en dur et pas en lien

### v0.2.1b - Cleaning & split
- tree-exploration behaviour flags
  - wall-fix : mark a move with a wall as already infinitely explored, so it won't be selected in next sims
  - random-min : randomness in choosing simulation path between directions explored with same frequency
- performance improvements / experiments
  - dag-node to use an underlyin
  - java-dag to use java arrays rather than vectors
  - movePosition as compiled java method
  - micro-optims : direct coll calls rather than get, min-direction tuning...
- repeatable randomness via seeding of random-using functions
- remove direction from tree-node spec, keep it only as children map key spec
- multiple bugfixes (blocking bugs while running games)
- Split cljs part from core clj part in separate project : claby / mzero
- remove zipper impl from tree exploration

#### Notes : repeatable randomness
- Décision d'utiliser `clojure.data.generators`
  -> attention, le générateur est seedé à 42 par défaut et donc par défaut suit toujours la même séquence de hasard
- Tricky bug : même seedé, le hasard n'est pas répétable si plusieurs exécutions parallèles utilisent la même instance de RNG

### v0.2.1 - Tree exploration player (double impl.)
- added tree-exploration-player
- fixed tree-exploration-player, added a faster (zipper) impl
+ determine slower impl throug xp
 + on the same 10 boards, average time & sims/sec for 10 steps for both options
 + change generation to use seeded random
+ add test showing both impl are ~ (equivalence test)
+ make equivalence test pass 
+ reproduce observed blocking bug

#### Misc
- added modsubvec, check-spec & scaffold (to utils), get-player-senses (to player)
- refactor player cli options & player creation to fit new TE-player

### AI World v0.2.0
+ refactor player dependency injection to not require any more coding, + docs
+ refactor world / main / game-runner to extract threaded-timing logic and doc
+ refactor tests to grasp default args and work on all game runners, + add tests for all game runners
+ add watcher-based runner
+ make parallel computing of experiment work
+ make script to run experiment with  many rand games, compare avg step number 
+ monothread runner (very fast) | x logging level works all the time
+ option to disable logging
+ player bug stops the game and shows error message
+ Make player implementation selection for the algorithm easy
+ Refactoring pass
+ ref: random player via an interface
+ add: non-random player - spans all the board algorithmically
+ Missteps in world state string
+ for non-interactive mode, adjustable log rate--defaulting to no log
- ref: full-state -> world-state 
- Documenter le fonctionnement souhaité dans README.md, main/world/player.clj
- préparer le backlog de la v0.2.0 pour qu'il aille à ce fonctionnement

### AI Game v0.1
#### v0.1.1 ####
- refactoring pour séparer setup & run et être plus FCIS
- régler le pb des tests

#### v0.1.0 - DONE
- possibilité de pauser / reprendre la partie, et de faire du step-by-step
- documenter et passer à la version suivante
- permettre l'ajustement de la quantité de murs
- lancer interactivement avec par défaut un affichage toutes les 2s
- mettre un log de début & un de fin
- passer l'affichage en log
- rationnaliser les args
- test & code d'une partie
- visualisation logguée clean du jeu
- jeu possible avec player aléatoire qui bouge toutes les 2 secondes
- créer la spec movement
- test & code d'une itération de jeu
- test & code d'une itération de joueur

#### base
- Document what it should do in README.md
- commande lein qui démarre le main thread
- démarre le game thread qui loggue l'état initial et affiche un message toutes les secondes

### Lapyrinthe v1
- Fix bug on level 3

### General
- séparer les codes de jeu humain vs jeu machine
- ajouter env d'exec mini
- test whether board creation is faster in java or clojure
- faire bouger les ennemis
- ajouter un level apéro, 1 apéro + fromage, 1 2* apéro + covid
- permettre la détection de fruits / lapins bloqués
- ajouter des ennemis statiques
- message de victoire
- gestion pour être toujours plein écran
- bloquer sur mobile
- affichage du level
- animation du message de niveaux
- cut game in multiple files (same namespace?)
- make tests faster
- instrument macro instruments only functions of namespace, not others
- board a ses éléments en fonction du niveau
- ajouter un système de passage de niveaux
- ajouter un level fromage
- victoire si tous fruits mangés
- animation de victoire
- mort si fromage & animation si mort
- possibilité de semer du fromage
- nouvelle case fromage, dans le html comme dans le compte
- scroll au bon moment
- passer le test get-html-for-state de cljs à clj
- centrer le lapin
- éviter 2e lancement musique
- message arrowkeys
- make it beautiful
- wall functions : generate random wall, add wall to board
- fruit functions : add random fruit, sow fruits accross board
- added create-nice-board
- refactoring of initial game state creation (incl. extract empty-board function)
- upgraded dependencies to later versions of clojure & reagent (req. to fix some issues)
- made generation.cljc working on cljs env
- made core.cljs compatible with reagent 0.10.0
- added find-in-board function 
- added "staging" build that does not include tests (dev build now not easily usable)
- split game in multiple files
- macro to run appropriately all spec checks
- wall generation & fruit sowing for board
- create game weird board is confined to tests
- score counter
- eat capability
- instrument functions during testing & dev
- prevent player from existing on wall on the board in specs
- wall capabilities and tests
- up action
- l,r,d action
- test all specs once in core
- remove game size from specs, put it as arg of game creation
- react component showing the state (incl. adding keys to table elts)
- refactor html-gen code to limit arg nb & add gen-html-cell
- turn matrix into HTML board
- setup testing workflow
- create game with empty board, working test
- allow magit auth with 2fa outside emacs
- Hello world in a browser


