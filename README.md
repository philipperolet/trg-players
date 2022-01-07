# Mzero-game deep players
AI players for the [Mzero Game](https://github.com/sittingbull/mzero-game) using Deep Neural Networks.

Try the Mzero Game (as a human) here: [game.machine-zero.com](https://game.machine-zero.com). Click on the big red question mark to start the game.

**Note**: Game tested on chromium-based and firefox browsers, **not** mobile compliant.

Below are instructions to train currently implemented AI players, plot their performance and watch them play.

## Overview
There is currently only one AI player, the M00 Player. It uses a DNN in a simple straightforward way to choose a move at each step of the game.

### M00 Player
M00 takes a subset of the environment's current state as input and feeds it to a neural network of densely connected layers. It outputs logprobabilities for each possible move, and the actual move is drawn according to the probability distribution.

Positive reinforcement is provided when a fruit is eaten: the move that led to eating the fruit is used as the target class and backpropagation occurs.

Similarly, negative reinforcement happens when the player bumps in a wall (i.e. tries to move on a wall cell). 

#### Purpose
The player was developed as an exercise to refresh my knowledge of artificial neural networks, and explore how they can be implemented in Clojure. 

#### Performance
Using a simple network (2 to 3 hidden layers of 500 to 2000 neurons), the player will learn after playing ~ 500 games (of 5K moves each) to avoid walls and move towards a fruit when it walks next to one.

#### Training speed
Training M00 on a recent laptop will take about 1s per game for a 2-hidden-512-units layer network (aka 85-512-512-5 network overall). Thus the performance described above should take ~ 10mns to compute.

## Setup
Debian/Ubuntu Package Management System `apt` is used in instructions below. Please adapt them to your PMS if needed.

### Requirements ###
- Clojure 1.10.1
- Leiningen 2.7.8+
- Intel MKL
```
sudo apt update && sudo apt -y install clojure leiningen intel-mkl
```
#### Optional : requirements for  GPU training
- CUDA toolkit 11.4.1 (exact version)
```
wget https://developer.download.nvidia.com/compute/cuda/repos/ubuntu2004/x86_64/cuda-ubuntu2004.pin
sudo mv cuda-ubuntu2004.pin /etc/apt/preferences.d/cuda-repository-pin-600
wget https://developer.download.nvidia.com/compute/cuda/11.4.1/local_installers/cuda-repo-ubuntu2004-11-4-local_11.4.1-470.57.02-1_amd64.deb
sudo dpkg -i cuda-repo-ubuntu2004-11-4-local_11.4.1-470.57.02-1_amd64.deb
sudo apt-key add /var/cuda-repo-ubuntu2004-11-4-local/7fa2af80.pub
sudo apt-get update
sudo apt-get -y install cuda
```
Instructions were taken from https://developer.nvidia.com/cuda-downloads?target_os=Linux&target_arch=x86_64&Distribution=Ubuntu&target_version=20.04&target_type=deb_local

Alternatively, OpenCL can be used 

### Installation
```
# Install mzero-game
git clone https://github.com/sittingbull/mzero-game.git
cd mzero-game
lein clean && lein install
cd ..
# Install m00 & test run
git clone https://github.com/sittingbull/mzero.git
cd mzero
echo "Launching test mzero game, installing dependencies..."
lein run "{:layer-dims [256 256 256]}" 2 25
```

## Train
```
lein repl
;; repl starts on ns mzero.ai.train
=> (run-games player-options-map nb-games seed)
;; returns updated player
=> (continue-games player nb-games seed)
;; returns player updated again
```
- **nb-games**: number of games the player will play;
- **seed**: used for all randomness purposes of the training (i.e. to generate the boards for the games, *and* to seed the player if it is stochastic);
- **player-options-map**: string of a clojure map representing player options, detailed below.

`run-games` will return the player with trained parameters, as well as measurements performed during training stored in the `:game-measurements` key.

`continue-games` will allow to continue the training where left of by `run-games` (or `continue-games`), for `nb-games` more games.

Games are capped to `mzero.ai.train/nb-steps-per-game`, defaulting to 5000, and board size is set to 30*30, as defined by `mzero.ai.train/default-board-size`.

### M00 options
#### Required
- **:layer-dims**: seq of ints describing the neural network's dense hidden layers (in other words excluding the input layer and the final output layer).
#### Optional
- **:step-measure-fn**: function to take measures at every step of a game, see details [below](#code-overview);
- **:ann-impl**: either an instance of a neural network implementation, or a map of options to generate it:
  - **:computation-mode**: can be one of `:cpu`, `:gpu-cuda` or `:gpu-opencl`;
  - **:ann-impl-name**: the name of the implementation, currently only `neanderthal-impl` is available (see [below](#code-overview) for details);
  - **:act-fns**: activation function pairs, to be chosen from [activations.clj](src/mzero/ai/ann/activations.clj);
  - **:label-distribution-fn**: how to compute proba distributions from the network output, to be chosen from [label_distributions.clj](src/mzero/ai/ann/label_distributions.clj);
- **:weight-generation-fn**: function to initialize the networks' weights (independently of the network's implementation), to be chosen from [initialization.clj](src/mzero/ai/ann/initialization.clj);
### Run training from command line
Training can be run from command line rather than repl:
```
lein run player-opts-string nb-games seed
```
However, functional options are not available via command-line.

## Plot performance
Training data is collected in various [Game Measurements](#game-measurements). To plot training data of a player from the REPL:
```
lein repl
;; starts repl
mzero.ai.train> (require '[mzero.utils.plot :refer [plot-training]])
> (run-games {:layer-dims [512 512]} 500 42)
> (def trained-player *1)
> (plot-training trained-player :score)
```
The above will plot the score evolution during the player's 500 games. It is a moving average over 50 games, to make the plot smoother & easier to interpret.

Any other mesurements described [below](#game-measurements) can be plotted instead of `:score`.

Note: plotting requires GUI, local only

## Watch trained player play a game
In the REPL:
```
mzero.ai.train> (require '[mzero.ai.main :refer [gon]])
> (run-games {:layer-dims [512 512]} 500 42)
> (def trained-player *1)
> (def newly-generated-world (aiw/world 30 42)) ;; world of size 30, seed 42
> (gon "-n 100" newly-generated-world trained-player) ;; starts game, run 100 steps
> (gon) ;; run a step
...
> (gon "-n 10000" (aiw/world 30 43) trained-player) ;; starts a new game, runs 10000 steps
```
At each gon invocation, starting and ending game board will be displayed. `gon` with no args or integer arg runs a few steps on the current game. `gon` with string / world / player starts a new game, see [Mzero Game](https://github.com/sittingbull/mzero-game).

## Code overview
The entry point to the code is [train.clj](src/mzero/ai/train.clj). The M00 player is implemented at [src/mzero/ai/players/m00.clj](src/mzero/ai/players/m00.clj). Below are details on other main namespaces.

### Neural Network implementations
Namespace `mzero.ai.ann` contains all code pertaining to neural net implementation--which is independant of the mzero game and the M00 player.

Currently, the only available implementation is [src/mzero/ai/ann/neanderthal_impl.clj](src/mzero/ai/ann/neanderthal_impl.clj), using the Neanderthal tensor computation library. Many thanks to Dragan Djuric for the very useful [Neanderthal lib](https://neanderthal.uncomplicate.org/).

The namespace also notably contains

- [src/mzero/ai/ann/network.clj](src/mzero/ai/ann/network.clj) neural network component specifications;
- [src/mzero/ai/ann/ann.clj](src/mzero/ai/ann/ann.clj) the neural network protocol;
- [src/mzero/ai/ann/initialization.clj](src/mzero/ai/ann/initialization.clj) weight initialization functions;
- [src/mzero/ai/ann/activations.clj](src/mzero/ai/ann/activations.clj) activation functions implementations for neanderthal_impl.clj;

### Game measurements
[src/mzero/ai/measure.clj](src/mzero/ai/measure.clj): during training, a `step-measure` function will take measures at each game step and be reset after each game. A `game-measure` function will aggregate step measurement data from a game into a single set of measures for this game and append it to the measurement sequence in `:game-measurements` in the player.

Current measures are the score, the `fruit-move-ratio`, number of times a player next to a fruit made the *correct* move to eat it, and the `wall-move-ratio`, number of times a player next to the wall made the *incorrect* move towards the wall.

### M00 modules
In namespace `mzero.ai.players.m0_modules` are:
- [src/mzero/ai/players/m0_modules/senses.clj](src/mzero/ai/players/m0_modules/senses.clj): compute input to the network from environment;
- [src/mzero/ai/players/m0_modules/m00_reinforcement.clj](src/mzero/ai/players/m0_modules/m00_reinforcement.clj): when and how to perform backpropagation according to game events;
- [src/mzero/ai/players/m0_modules/motoneurons.clj](src/mzero/ai/players/m0_modules/motoneurons.clj): final output layer logic.

#### Details on senses / inputs
At each step, the environment data is converted into [senses](src/mzero/ai/players/m0_modules/senses.clj):
- a vector of a 9*9 subset of the game board with floats representing the elements on the board
- a `satiety` scalar, 1.0 when the player just ate a fruit, slowly decreasing;
- a `motoception` scalar, 1.0 when the player just moved, slowly decreasing;
- two `aleaception` scalars, randomly drawn at each step;

# License

Copyright © 2020 Philippe Rolet

Distributed under the Apache Public License 2.0
