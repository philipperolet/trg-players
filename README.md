# Claby

A simple game of eating fruits in a maze, avoiding unpasteurized cheese and moving enemies, with 6 levels to clear. The game can be played :

- by computers using CLI (**AI world**).
- by humans in a browser (**Lapyrinthe**);


## Setup

### Requirements ###
- Clojure & clojurescript
- Leiningen (who will take care of installing all other reqs)

For more info on requirements / dependencies and which version of what you need to install, see `project.clj`

### Installation
- Install by cloning this rep.

## AI world

### Purpose
**AI world** provides a simple programmatic world, and an interface to implement wannabe-intelligent players to interact in this world. The world's rules are those of the above described games. Therefore, a good player will be able to navigate the maze to eat lots of fruits, while avoiding enemies.

### Usage
Launching AI world will run a single game using the player code located in `claby.ai.player`.

Start game:
- in CLI with `lein run args`;
- in REPL by getting to the `claby.ai.main` namespace and typing `(-main args)`.

Run `lein run -h` to print CLI arguments. They are also described in `cli-options` at [claby.ai.main](src/claby/ai/main.clj).

#### Programmatic mode - interactive mode ###
In programmatic mode, game only displays initial and end states.

Game can be run interactively using the `--interactive/-i` flag. In that case:
- user can pause/step with [enter], run with r, quit wit q;
- game state is displayed every X (optionally specified) steps.

See main.clj for more details.

### Project structure
Game models and actions are in ``claby.game``

3 main modules in `claby.ai`:
- **main.clj** is responsible for launching AI world;
- **world.clj** is responsible for running the world;
- **player.clj** is responsible for running the player.

### How world and player operate
The world is the *reality*, all that is external to the player's intelligence and senses--the player is defined by its senses and its movement decisions. In the real world, the player would be one's brain & nerves, sensing information and deciding actions. The world woud be everything else--including one's bodily functions.

Movement requests are the player's responsibility, while performing the actual movement is the world's responsibility. Theoretically, the player could request movements that are not executed, similarly to somebody suffering from the locked-in syndrome.

#### Architecture for AI Game

![Architecture image](https://docs.google.com/drawings/d/e/2PACX-1vT1ogu40fw8SG1oWGnR4WCJE3kmnCFcYzwMuLwiAuGbJ1vb8V2M8JzLFYiwczdS6D6cYqsMLmmyFO-_/pub?w=960&h=720)

## Lapyrinthe

### Usage

- Start game with niceties (sound, rabbits everywhere, animations) with ``lein fig:build-lapy``
- Start game with minimal skin with ``lein fig:build-mini`` (intended for AI Game visualisation)

Move the player with arrow keys, or e - d - s - f keys. Game starts at level 1, and if the player clears all levels 6 you will see the ending.

Cheat codes allow to start directly at a given level, or to slow down the enemies, by adding the query string `?cheatlev=X&tick=Y`

### Dev & deploy
UX tools and entry points for **lapyrinthe** are in ``claby.ux``

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


# License

Copyright © 2020 Philippe Rolet

Distributed under the Apache Public License 2.0
