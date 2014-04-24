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
	private static final float DISCOUNT_FACTOR = 0.9f;
	private static final float LEARNING_RATE = 0.0001f;
	private static final float EPSILON = 0.02f;
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
	private double[] w;
	
	private StateView currentState;
	
	public QLearningAgent(int playernum, String[] args) {
		super(playernum);
		
		if (args.length > 0) {
			episodes = Integer.parseInt(args[0]);
		}
		
		w = new double[FEATURES];
		for (int i = 0; i < w.length; i++) {
			w[i] = 1;
		}
		
	}

	
	@Override
	public Map<Integer, Action> initialStep(StateView newstate, History.HistoryView statehistory) {
		step = 0;
		
		currentState = newstate;
		
		for (UnitView unit : currentState.getAllUnits()) {
			String unitTypeName = unit.getTemplateView().getName();
			if(unitTypeName.equals("Footman")) {
				unitHealth.put(unit.getID(), unit.getHP());
				unitLocations.put(unit.getID(), new Pair<Integer, Integer>(unit.getXPosition(), unit.getYPosition()));
				if (currentState.getUnits(playernum).contains(unit)) {
					footmen.add(unit.getID());
				} else {
					enemyFootmen.add(unit.getID());
				}
			}
		}
		
		evaluationPhase = gameNumber % 15 > 10;
		
		Map<Integer,Action> builder = new HashMap<Integer,Action>();
		
		attack = assignTargets(footmen, enemyFootmen, unitHealth, unitLocations);
		
		for (Integer footmanID : attack.keySet()) {
			Action b = TargetedAction.createCompoundAttack(footmanID, attack.get(footmanID));
			builder.put(footmanID, b);
		}
		
		return builder;
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
		
		if (!eventHasHappened(curFootmen, curEnemyFootmen, curUnitHealth)) {
			return builder;
		}
		
		for (Integer footman : curFootmen) {
			double reward = getReward(footman, attack.get(footman), curFootmen, curEnemyFootmen, curUnitHealth, curUnitLocations);
			
			if (!evaluationPhase) {
				updateQFunction(footman, reward);
			}
		}
		
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
		
		//TODO print out stuff when we are in evaluation mode
		
		if (gameNumber == episodes) {
			System.exit(0);
		}
		
		gameNumber++;
	}
	
	private boolean eventHasHappened(List<Integer> curFootmen, List<Integer> curEnemyFootmen, Map<Integer, Integer> curUnitHealth) {
		// TODO Auto-generated method stub
		// Needs to determine if a significant event has passed, like someone getting attacked
		return false;
	}

	private Map<Integer, Integer> assignTargets(List<Integer> footmen, List<Integer> enemyFootmen, Map<Integer, Integer> unitHealth, Map<Integer, Pair<Integer, Integer>> unitLocations) {
		Map<Integer, Integer> attack = new HashMap<Integer, Integer>();
		
		for (Integer footman : footmen) {
			if (1.0 - EPSILON < Math.random() && !evaluationPhase) {
				attack.put(footman, enemyFootmen.get((int)(Math.random() * enemyFootmen.size())));
			} else {
				double maxQ = Double.NEGATIVE_INFINITY;
				int currentTarget = enemyFootmen.get(0);
				for (Integer enemy : enemyFootmen) {
					double curQ = qFunction(footman, enemy, footmen, enemyFootmen, unitHealth, unitLocations);
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
	
	private double qFunction(Integer footman, Integer enemy, List<Integer> footmen, List<Integer> enemyFootmen, Map<Integer, Integer> unitHealth, Map<Integer, Pair<Integer, Integer>> unitLocations) {
		// TODO replace the 1.0's with actual f's
		// Possible features:
		// # of other friendly units currently attacking enemy
		// health of footman
		// health of enemy
		// is enemy the current target of footman?
		// how many other footmen are attacking enemy?
		// what is the ratio of hitpoints of enemy to footman
		return w[0] +
				w[1] * 1.0 +
				w[2] * 1.0 +
				w[3] * 1.0 + 
				w[4] * 1.0;
				// ...
	}
	
	private void updateQFunction(Integer footman, double reward) {
		// TODO Auto-generated method stub
		
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
