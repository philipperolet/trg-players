# Claby

A simple game of eating fruits in a maze. 

## Overview

### Requirements ###
The project uses Leiningen. See project.clj for requirements / dependencies and which version of what you need to install.

### Use ###
- Install by cloning this rep.
- Run game intended for human, with lapy skin, with ``lein fig:build-lapy``
- Run game intended for AI, with minimal skin with ``lein fig:build-mini``

Move the player with arrow keys, or e - d - s - f keys

## Development

### Project structure
UX tools and entry points are in ``claby.ux``

Game models and actions are in ``claby.game``

### Use
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
