# Claby

A simple game of eating fruits in a maze, avoiding unpasteurized cheese and moving enemies, with 6 levels to clear. The game can be played :

- by humans in a browser (**Lapyrinthe**);
- by computers using CLI (**AI game**).

## Setup

### Requirements ###
- Clojure & clojurescript
- Leiningen (who will take care of installing all other reqs)

For more info on requirements / dependencies and which version of what you need to install, see `project.clj`

### Installation
- Install by cloning this rep.

## Usage : Lapyrinthe ###

- Start game with niceties (sound, rabbits everywhere, animations) with ``lein fig:build-lapy``
- Start game with minimal skin with ``lein fig:build-mini`` (intended for AI Game visualisation)

Move the player with arrow keys, or e - d - s - f keys. Game starts at level 1, and if the player clears all levels 6 you will see the ending.

Cheat codes allow to start directly at a given level, or to slow down the enemies, by adding the query string `?cheatlev=X&tick=Y`

## Usage : AI game ##

Start game
- in CLI with `lein run args`
- in REPL by getting to the `claby.ai.main` namespace and typing `(run args)`

Run `lein run -h` to print CLI arguments. They are also described in `cli-options` at [claby.ai.main](src/claby/ai/main.clj).

### Programmatic mode - interactive mode ###
In programmatic mode, game only displays initial and end states.

Game can be run interactively using the `--interactive/-i` flag. In that case:
- user can pause/step with [enter], run with r, quit wit q;
- game state is displayed every X (optionally specified) steps.

See main.clj for more details.

## Development

### Project structure
Game models and actions are in ``claby.game``

UX tools and entry points for **lapyrinthe** are in ``claby.ux``

Code for **AI Game** is in ``claby.ai``

### Architecture for AI Game

![Architecture image](https://docs.google.com/drawings/d/e/2PACX-1vT1ogu40fw8SG1oWGnR4WCJE3kmnCFcYzwMuLwiAuGbJ1vb8V2M8JzLFYiwczdS6D6cYqsMLmmyFO-_/pub?w=960&h=720)

### Lapyrinthe : use
To get an interactive development environment run:

    lein fig:build-{mini|lapy}

This will auto compile and send all changes to the browser without the
need to reload. After the compilation process is complete, you will
get a Browser Connected REPL. An easy way to try it is:

    (js/alert "Am I connected?")

and you should see an alert in the browser window.

To clean all compiled files:

	lein clean

To create a production build run:

	lein clean
	lein fig:prod


## License

Copyright © 2020 Philippe Rolet

Distributed under the Apache Public License 2.0
