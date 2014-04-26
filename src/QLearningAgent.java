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
	private static final float LAMBDA = 0.9f; // Discount factor
	private static final float ALPHA = 0.0001f; // Learning rate
	private static final float EPSILON = 0.02f; // For GLIE exploration
	private static final int FEATURES = 5;

	private int step;
	private List<Integer> footmen = new ArrayList<Integer>();
	private List<Integer> enemyFootmen = new ArrayList<Integer>();
	private Map<Integer, Integer> unitHealth = new HashMap<Integer, Integer>();
	private Map<Integer, Pair<Integer, Integer>> unitLocations = new HashMap<Integer, Pair<Integer, Integer>>();
	
	private Map<Integer, Integer> attack = new HashMap<Integer, Integer>();
	
	// If we are in evaluation phase, freeze the Q function and play 5 games
	private boolean evaluationPhase = false;
	private int gameNumber = 1;
	private int episodes = 100;
	private boolean firstRound = true;
	private float curEpsilon;
	private double currentGameReward = 0.0;
	private double avgGameReward = 0.0;
	private Features features;
	
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
		step = 0;
		currentState = newstate;
		
		currentGameReward = 0.0;
		
		evaluationPhase = (gameNumber - 1) % 15 > 9;
		
		if (!evaluationPhase) {
			avgGameReward = 0.0;
		}
		
		firstRound = true;
		
		return middleStep(newstate, statehistory);
	}

	@Override
	public Map<Integer,Action> middleStep(StateView newState, History.HistoryView statehistory) {
		step++;
		
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
		
		if (!firstRound) {
			if (!eventHasHappened(curFootmen, curEnemyFootmen, curUnitHealth)) {
				return builder;
			}
			
			for (Integer footman : curFootmen) {
				double reward = getReward(footman, attack.get(footman), curFootmen, curEnemyFootmen, curUnitHealth, curUnitLocations);
				
				currentGameReward += reward;
				
				if (!evaluationPhase) {
					updateQFunction(reward, footman, attack.get(footman), curFootmen, curEnemyFootmen, curUnitHealth, curUnitLocations, curUnitHealth);
				}
			}
		} else {
			firstRound = false;
		}
		
		// Update previous state to current state
		footmen = curFootmen;
		enemyFootmen = curEnemyFootmen;
		unitLocations = curUnitLocations;
		unitHealth = curUnitHealth;
		
		attack = assignTargets(footmen, enemyFootmen, unitHealth, unitLocations);
		
		for (Integer footmanID : attack.keySet()) {
			Action b = TargetedAction.createCompoundAttack(footmanID, attack.get(footmanID));
			builder.put(footmanID, b);
		}
		
		return builder;
	}


	@Override
	public void terminalStep(StateView newstate, History.HistoryView statehistory) {
		step++;
		
		if (evaluationPhase) {
			avgGameReward += (currentGameReward - avgGameReward) / ((gameNumber - 1) % 15 - 9);
			System.out.println("Played evaluation game " + ((gameNumber - 1) % 15 - 9) );
		} else {
			System.out.println("Played game " + ((gameNumber / 15) * 10 + (gameNumber % 15)));
		}
		
		if (gameNumber % 15 == 0) {
			curEpsilon = curEpsilon < 0 ? 0 : curEpsilon - 0.002f;
			
			System.out.printf("Games trained on: %d\tAverage Reward:%f\n", ((gameNumber / 15) * 10), avgGameReward);
		}
		
		if (gameNumber == episodes) {
			System.exit(0);
		}
		
		gameNumber++;
	}
	
	private boolean eventHasHappened(List<Integer> curFootmen, List<Integer> curEnemyFootmen, Map<Integer, Integer> curUnitHealth) {
		// Needs to determine if a significant event has passed, like someone getting attacked
		if (curFootmen.size() < footmen.size() || curEnemyFootmen.size() < enemyFootmen.size()) {
			// uh oh, they dead
			return true;
		}
		
		boolean someoneInjured = false;
		
		for (Integer footman : curFootmen) {
			someoneInjured |= (curUnitHealth.get(footman) < unitHealth.get(footman));
		}
		
		for (Integer footman : curEnemyFootmen) {
			someoneInjured |= (curUnitHealth.get(footman) < unitHealth.get(footman));
		}
		
		return someoneInjured;
	}

	private Map<Integer, Integer> assignTargets(List<Integer> footmen, List<Integer> enemyFootmen, Map<Integer, Integer> unitHealth, Map<Integer, Pair<Integer, Integer>> unitLocations) {
		Map<Integer, Integer> attack = new HashMap<Integer, Integer>();
		
		for (Integer footman : footmen) {
			if (1.0 - curEpsilon < Math.random() && !evaluationPhase) {
				attack.put(footman, enemyFootmen.get((int)(Math.random() * enemyFootmen.size())));
			} else {
				double maxQ = Double.NEGATIVE_INFINITY;
				int currentTarget = enemyFootmen.get(0);
				for (Integer enemy : enemyFootmen) {
					double curQ = qFunction(footman, enemy, footmen, enemyFootmen, unitHealth, unitLocations, this.attack);
					if (curQ > maxQ) {
						maxQ = curQ;
						currentTarget = enemy;
					}
				}
				attack.put(footman, currentTarget);
			}
		}
		
		return attack;
	}
	
	private double qFunction(Integer footman, Integer enemy, List<Integer> footmen, List<Integer> enemyFootmen,
			Map<Integer, Integer> unitHealth, Map<Integer, Pair<Integer, Integer>> unitLocations, Map<Integer, Integer> attack) {
		// TODO make sure attack map will work on first time through
		double[] f = Features.getFeatures(footman, enemy, footmen, enemyFootmen, unitHealth, unitLocations, attack);
		return features.qFunction(f);
	}
	
	private void updateQFunction(double reward, Integer footman, Integer enemy, List<Integer> footmen, List<Integer> enemyFootmen,
			Map<Integer, Integer> unitHealth, Map<Integer, Pair<Integer, Integer>> unitLocations, Map<Integer, Integer> attack) {
		
		// TODO make sure attack prev/current state is right
		double[] f = Features.getFeatures(footman, enemy, this.footmen, this.enemyFootmen, this.unitHealth, this.unitLocations, this.attack);
		double previousQ = features.qFunction(f);
		
		// TODO update weights
		//	Loop over features/weights:
		//		delLoss = -(Reward + gamma * (max over a')[Q(s',a')-Q(s,a)]) * feature_i(s,a)
		//		w_i <- w_i - alpha * delLoss
	}
	
	private double getReward(Integer footman, Integer target, List<Integer> curFootmen, List<Integer> curEnemyFootmen, Map<Integer, Integer> curUnitHealth, Map<Integer, Pair<Integer, Integer>> curUnitLocations) {
		double reward = -0.1;
		
		if (!footmen.contains(footman)) {
			reward -= 100.0;
		} else {
			int healthLost = unitHealth.get(footman) - curUnitHealth.get(footman);
			reward -= healthLost;
		}
		
		if (!enemyFootmen.contains(target)) {
			reward += 100;
		} else {
			int healthLost = unitHealth.get(target) - curUnitHealth.get(target);
			reward += healthLost;
		}
		
		return reward;
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
