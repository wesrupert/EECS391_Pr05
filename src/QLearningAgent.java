/**
 *  Strategy Engine for Programming Intelligent Agents (SEPIA)
    Copyright (C) 2012 Case Western Reserve University

    This file is part of SEPIA.

    SEPIA is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    SEPIA is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with SEPIA.  If not, see <http://www.gnu.org/licenses/>.
 */
//package edu.cwru.sepia.agent;


import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.action.TargetedAction;
import edu.cwru.sepia.agent.Agent;
import edu.cwru.sepia.environment.model.history.History;
import edu.cwru.sepia.environment.model.state.State.StateView;
import edu.cwru.sepia.environment.model.state.Unit.UnitView;

/**
 * This agent will first collect gold to produce a peasant,
 * then the two peasants will collect gold and wood separately until reach goal.
 * @author Feng
 *
 */
public class QLearningAgent extends Agent {
	private static final long serialVersionUID = -4047208702628325380L;
	private static final double GAMMA = 0.9; // Discount factor, orig 0.9
	private static final double ALPHA = 0.0001; // Learning rate
	private static final double EPSILON = 0.02; // For GLIE exploration, orig 0.02

	private State prevState = null;
	private AttackAction prevAction = new AttackAction(new HashMap<Integer, Integer>());
	
	// If we are in evaluation phase, freeze the Q function and play 5 games
	private boolean evaluationPhase = false;
	private int gameNumber = 1;
	private int episodes = 100;
	private boolean firstRound = true;
	private double curEpsilon;
	private double currentGameReward = 0.0;
	private double avgGameReward = 0.0;
	private Features features;
	
	private String finalOutput = "";
	
	private StateView currentState;
	
	public QLearningAgent(int playernum, String[] args) {
		super(playernum);
		
		curEpsilon = EPSILON;
		
		if (args.length > 0) {
			episodes = Integer.parseInt(args[0]);
		}
		
		features = new Features();
	}

	
	@Override
	public Map<Integer, Action> initialStep(StateView newstate, History.HistoryView statehistory) {
		currentState = newstate;
		
		currentGameReward = 0.0;
		
		evaluationPhase = (gameNumber - 1) % 15 > 9;
		
		if (!evaluationPhase) {
			avgGameReward = 0.0;
		}
		
		
		firstRound = true;
		prevState = null;
		prevAction = new AttackAction(new HashMap<Integer, Integer>());
		
		return middleStep(newstate, statehistory);
	}

	@Override
	public Map<Integer,Action> middleStep(StateView newState, History.HistoryView statehistory) {
		Map<Integer,Action> builder = new HashMap<Integer,Action>();
		currentState = newState;

		
		List<Integer> curFootmen = new ArrayList<Integer>();
		List<Integer> curEnemyFootmen = new ArrayList<Integer>();
		Map<Integer, Integer> curUnitHealth = new HashMap<Integer, Integer>();
		Map<Integer, Pair<Integer, Integer>> curUnitLocations = new HashMap<Integer, Pair<Integer, Integer>>();
		
		for (UnitView unit : currentState.getAllUnits()) {
			String unitTypeName = unit.getTemplateView().getName();
			if (unitTypeName.equals("Footman")) {
				curUnitLocations.put(unit.getID(), new Pair<Integer, Integer>(unit.getXPosition(), unit.getYPosition()));
				curUnitHealth.put(unit.getID(), unit.getHP());
				if (currentState.getUnits(playernum).contains(unit)) {
					curFootmen.add(unit.getID());
				} else {
					curEnemyFootmen.add(unit.getID());
				}
			}
		}
		
		State currentState = new State(curFootmen, curEnemyFootmen, curUnitHealth, curUnitLocations);
		
		if (!firstRound) {
			if (!eventHasHappened(currentState, prevState)) {
				return builder;
			}
			
			for (Integer footman : prevState.getFootmen()) {
				
				double reward = getReward(currentState, prevState, prevAction, footman);
//				System.out.println("Reward = " + reward);
				currentGameReward += reward;
				
				if (!evaluationPhase) {
					updateQFunction(reward, currentState, prevState, prevAction, footman);
				}
			}
//			System.out.println("");
		} else {
			firstRound = false;
		}
		
		// Update previous state to current state
		prevState = currentState;
		
		prevAction = assignTargets(prevState, prevAction);
		
		for (Integer footmanID : prevAction.getAttack().keySet()) {
			Action b = TargetedAction.createCompoundAttack(footmanID, prevAction.getAttack().get(footmanID));
			builder.put(footmanID, b);
		}
		
		return builder;
	}


	@Override
	public void terminalStep(StateView newstate, History.HistoryView statehistory) {
		boolean won = false;
		for (UnitView unit : currentState.getUnits(playernum)) {
			String unitTypeName = unit.getTemplateView().getName();
			if (unitTypeName.equals("Footman")) {
				won = true;
			}
		}
		String winLose = won ? "won" : "lost";
		
		if (evaluationPhase) {
			avgGameReward += (currentGameReward - avgGameReward) / ((gameNumber - 1) % 15 - 9);
			System.out.printf("Played evaluation game %d and %s (Cumulative reward: %.2f)\n", ((gameNumber - 1) % 15 - 9), winLose, currentGameReward);
		} else {
			System.out.printf("Played game %d and %s\n", ((gameNumber / 15) * 10 + (gameNumber % 15)), winLose);
		}
		
		if (gameNumber % 15 == 0) {
			curEpsilon = curEpsilon < 0 ? 0 : curEpsilon - 0.002f;
			String out = String.format("Games trained on: %d\tAverage Reward:%f\n", ((gameNumber / 15) * 10), avgGameReward);
			System.out.print(out);
			finalOutput += out;
		}
		
		if (((gameNumber / 15) * 10) >= episodes) {
			System.out.println();
			System.out.println(finalOutput);
			System.exit(0);
		}
		
		gameNumber++;
	}
	
	/**
	 * Determines if a significant event has happened, which is someone being attacked or someone dying.
	 * @param curState
	 * @param prevState
	 * @return True, if an event has happened
	 */
	private boolean eventHasHappened(State curState, State prevState) {
		if (curState.getFootmen().size() < prevState.getFootmen().size()
				|| curState.getEnemyFootmen().size() < prevState.getEnemyFootmen().size()) {
			// uh oh, they dead
			return true;
		}
		
		boolean someoneInjured = false;
		
		List<Integer> footmen = new ArrayList<Integer>();
		footmen.addAll(curState.getFootmen());
		footmen.addAll(curState.getEnemyFootmen());
		
		for (Integer footman : footmen) {
			someoneInjured |= (curState.getUnitHealth().get(footman) < prevState.getUnitHealth().get(footman));
		}
		
		return someoneInjured;
	}
	
	/**
	 * Uses the Q Function to determine which actions (Attack(Footman, Enemy)) maximizes Q
	 * @param state
	 * @param prevAction
	 * @return A attack plan which assigns targets to each footman
	 */
	private AttackAction assignTargets(State state, AttackAction prevAction) {
		Map<Integer, Integer> attack = new HashMap<Integer, Integer>();
		
		for (Integer footman : state.getFootmen()) {
			if (!evaluationPhase && 1.0 - curEpsilon < Math.random()) {
				attack.put(footman, state.getEnemyFootmen().get((int)(Math.random() * state.getEnemyFootmen().size())));
//				System.out.println("Hit random assignment");
			} else {
				double maxQ = Double.NEGATIVE_INFINITY;
				int currentTarget = state.getEnemyFootmen().get(0);
				
				// Find the enemy that gives the highest Q function
				for (Integer enemy : state.getEnemyFootmen()) {
					double[] f = Features.getFeatures(state, footman, enemy, prevAction);
					double curQ = features.qFunction(f);
					if (curQ > maxQ) {
						maxQ = curQ;
						currentTarget = enemy;
					}
				}
				attack.put(footman, currentTarget);
			}
		}
		
		return new AttackAction(attack);
	}
	
	/**
	 * Updates the weights associated with the Q function features based on the previous and current states
	 * @param reward
	 * @param curState
	 * @param prevState
	 * @param prevAction
	 * @param footman
	 */
	private void updateQFunction(double reward, State curState, State prevState, AttackAction prevAction, Integer footman) {
		State currentState = new State(curState);
		double[] prevF = Features.getFeatures(prevState, footman, prevAction.getAttack().get(footman), prevAction);
		double previousQ = features.qFunction(prevF);
		
		
		// footman can be dead at this point and therefore not in curState, so add him with health of 0
		if (!currentState.getFootmen().contains(footman)) {
			currentState.getFootmen().add(footman);
			currentState.getUnitHealth().put(footman, 0);
			currentState.getUnitLocations().put(footman, prevState.getUnitLocations().get(footman));
		}
		
		// Find the max Q function w.r.t. the possible actions
		AttackAction curAction = assignTargets(currentState, prevAction);
		
		// Find Q(s',a')
		double[] newF = Features.getFeatures(currentState, footman, curAction.getAttack().get(footman), curAction);
		double newQ = features.qFunction(newF);
		
		double delLoss = (reward + GAMMA * newQ - previousQ);
		
		// updates the w vector
		features.updateWeights(prevF, delLoss, ALPHA);
	}
	
	/**
	 * Finds the reward for a state and action
	 * @param curState
	 * @param prevState
	 * @param prevAction
	 * @param footman
	 * @return The reward
	 */
	private double getReward(State curState, State prevState, AttackAction prevAction, Integer footman) {
		// Reward = -0.1 - FHP - FKILLED + EHP + EKILLED
		double reward = -0.1;
		
		// Update the attacking footman's location.
		curState.getUnitLocations().put(prevAction.getAttack().get(footman), prevState.getUnitLocations().get(prevAction.getAttack().get(footman)));
		
		// (FHP/FKILLED) Update reward based on footman's health.
		if (!curState.getFootmen().contains(footman)) {
			// Ally killed
			reward -= 100.0;
		} else {
			// Ally injured
			int healthLost = prevState.getUnitHealth().get(footman) - curState.getUnitHealth().get(footman);
			reward -= healthLost;
		}
		
		Integer target = prevAction.getAttack().get(footman);
		
		if (!curState.getFootmen().contains(footman)) {
			curState.getUnitLocations().put(footman, prevState.getUnitLocations().get(footman));
		}
		if (!curState.getEnemyFootmen().contains(target)) {
			curState.getUnitLocations().put(target, prevState.getUnitLocations().get(target));
		}

		// (EHP/EKILLED) Update reward based on enemy's health.
		if (areAdjacent(curState.getUnitLocations().get(footman), curState.getUnitLocations().get(target))) {
			if (!curState.getEnemyFootmen().contains(target)) {
				// Enemy killed
				reward += 100;
			} else {
				// Enemy injured
				int healthLost = prevState.getUnitHealth().get(target) - curState.getUnitHealth().get(target);
				reward += healthLost;
			}
		}
		
		return reward;
	}
	
	/**
	 * 
	 * @param p
	 * @param q
	 * @return True, if p and q are adjacent
	 */
	private boolean areAdjacent(Pair<Integer, Integer> p, Pair<Integer, Integer> q) {
		for (int i = p.getX() - 1; i <= p.getX() + 1; i++) {
			for (int j = p.getY() - 1; j <= p.getY() + 1; j++) {
				if (q.getX() == i && q.getY() == j) {
					return true;
				}
			}
		}
		
		return false;
	}

	public static String getUsage() {
		return "Uses Q learning to defeat enemies.";
	}
	
	@Override
	public void savePlayerData(OutputStream os) {
		//this agent lacks learning and so has nothing to persist.
		
	}
	@Override
	public void loadPlayerData(InputStream is) {
		//this agent lacks learning and so has nothing to persist.
	}
}
