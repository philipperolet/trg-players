# Claby

A simple game of eating fruits in a maze.

## Overview

### Requirements ###
The project uses Leiningen. See project.clj for requirements / dependencies and which version of what you need to install.

### Use ###
- Install by cloning this rep.
- Run with ``lein fig:build``
Move the player with arrow keys, or e - d - s - f keys

## Development

To get an interactive development environment run:

    lein fig:build

This will auto compile and send all changes to the browser without the
need to reload. After the compilation process is complete, you will
get a Browser Connected REPL. An easy way to try it is:

    (js/alert "Am I connected?")

and you should see an alert in the browser window.

To clean all compiled files:

	lein clean

To create a production build run:

	lein clean
	lein fig:min


## License

Copyright © 2020 Philippe Rolet

Distributed under the Apache Public License 2.0
