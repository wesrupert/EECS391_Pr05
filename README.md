# EECS 391 - Programming Assignment 5
### Authors: Josh Braun (jxb532), Wes Rupert (wkr3)

This is fifth programming assignment for Case Western Reserve University's EECS 391 Introduction to Artificial Intelligence course. The project requires CWRU's SEPIA AI engine to run.

To run the assignment parts, execute the following:

```bat
sh buildQLearningGame.sh
sh buildQLearningGame2.sh
```

buildQLearningGame will build a 5v5 game, and buildQLearningGame2 will build a 10v10 game.

Note that the epsilon greedy exploration in first games will tend to make the average reward gained from the evaluation games slightly unpredictable at first.
As more games are played, the reward starts to settle, although there is still quite a bit of variation due to the non-deterministic nature of the game.

It is also important to note that the performance of the agent depends on the initial values of the features.
Since they are set to a random value in (-1,1), it may be necessary to play the game multiple times to get a feel for the agent's performance.

The program will print the outcome for each of the training games (win/lose), and will output the total reward for each of the evaluation games.
The average cumulative rewards for each of the evaluation phases will print out in total at the end of the desired number of games.