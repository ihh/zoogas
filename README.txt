Z00 Gas

(To run, type 'make zoogas'.)


WELCOME TO YOUR NEW JOB

Hi, welcome to your first day as keeper at the Rock-Paper-Scissors zoo!
There are three types of animal here: reds, greens and blues.
Reds eat greens eat blues eat reds.
Munch munch munch munch munch.

There's not much to do; just watch'em eat each other and breed.
You could use this cement (S) to make cages, I guess.
Or torment them with the acid spray (D). Kinda cruel though.
Folk say they like the perfume (F), but y'ask me, they horny enough without it.
Keep your hands off the mutator gas (G), that ain't for newbies like you!

You can track your population balances down the bottom of the screen.
Tools are on the right. Watch your diversity score!

OK, I'm off to the secret Volcano - that's the next game. Catch ya later!
---STAN THE MAN, HEAD ZOOKEEPER


[Underneath Stan's note is a vinyl-bound folder]

   ---++ FORMAL CODE OF ETHICS ++---
        ROCK-PAPER-SCISSORS ZOO

As zoo's resident keeper/bioengineer, your goal is to maintain a diverse zoo population.
You have several tools to do this, delivered by pressing the following keys (using the mouse to target):

S - cement spray
D - death spray (acid)
F - fecundity (perfume)
G - mutator gas

Your reserve stocks of these gases are shown to the right of the board.
Your population charts are shown under the board.
Your diversity score is shown as a number (bottom right) and a grey bar (bottom).
The higher your score, the faster your sprays and gases will be restocked.
(Your highest diversity score is also shown.)

Things to try:
- use cement spray to build a cage enclosure
- select a single species inside a cage (hint: make cage small or use acid)
- mutate the animals inside the cage
- maintain the cage walls
- build two cages with a common wall
- break down the wall with corrosive gas (or just let the wall decay)
- just go crazy and spray cement everywhere, creating a loosely-connected set of patches
- use mutator gas all over the patches
- trap perfume and mutator gas in the walls
- can you get a stable 3-cycle inside a cage? (seems HARD -- I haven't done it yet -- STAN)

There is a cheat code, but you'll have to guess it!
(Or look in the source code. All sorts of fun can be had by tweaking the game parameters, too.)



Implementation details:
Cyclic competitive Lotka-Volterra model on 4-neighbor square grid.
Ian Holmes
9/22/09
