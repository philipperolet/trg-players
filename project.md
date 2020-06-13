# Project Management

## Todo
### AI Game (AIG) v0.1
#### version 0.1.0: 
1 game thread  qui loggue l'état initial, les actions, les player senses sur la sortie standard, et qui s'arrête quand le jeu finit; routine qui fait bouger aléatoirement le player toutes les 200 steps
- créer la spec movement
- refactor partiel pour utiliser cette spec (si poss backward-compatible)
- test & code d'une itération de jeu
- test & code d'une itération de joueur
- test & code d'une partie

#### v0.1.1 ####
- remplacer la routine par un player thread qui prend l'entrée standard et transmet au game thread

- puis une autre qui joue aléatoirement
- ajouter le paramétrage de niveau et de taille
#### ajouter le stockage
#### ajouter le thread de visu
#### Créer un joueur pas trop débile

## Backlog
- remove libs from vcs to avoid useless commits / searchs?
- tab should autocomplete
- add walls & fruits with clicks
- let tests be pretty-printed
- convenient way of having tests in emacs

## Done
### AI Game v0.1
#### v0.1.0
- visualisation logguée clean du jeu
- jeu possible avec player aléatoire qui bouge toutes les 2 secondes

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

