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

	private int step;
	private int startingPeasants = 0;
	private Map<Integer, Integer> unitHealth = new HashMap<Integer, Integer>();
	private Map<Integer, Pair<Integer, Integer>> unitLocations = new HashMap<Integer, Pair<Integer, Integer>>();
	
	// If we are in evaluation phase, freeze the Q function and play 5 games
	private boolean evaluationPhase = false;
	private int gameNumber = 1;
	
	private StateView currentState;
	
	public QLearningAgent(int playernum, String[] arguments) {
		super(playernum);
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
			}
		}
		
		evaluationPhase = gameNumber % 15 > 10;
		
		return middleStep(newstate, statehistory);
	}

	@Override
	public Map<Integer,Action> middleStep(StateView newState, History.HistoryView statehistory) {
		step++;
		
		Map<Integer,Action> builder = new HashMap<Integer,Action>();
		currentState = newState;

		List<UnitView> footmen = new ArrayList<UnitView>();
		List<UnitView> enemyFootmen = new ArrayList<UnitView>();
		
		for (UnitView unit : currentState.getAllUnits()) {
			String unitTypeName = unit.getTemplateView().getName();
			if (unitTypeName.equals("Footman")) {
				if (currentState.getUnits(playernum).contains(unit)) {
					footmen.add(unit);
				} else {
					enemyFootmen.add(unit);
				}
				unitLocations.put(unit.getID(), new Pair<Integer, Integer>(unit.getXPosition(), unit.getYPosition()));
			}
		}
		
		if (eventHasHappened()) {
			// TODO loop through our footmen and assign targets
		}
		
		
		return builder;
	}

	private boolean eventHasHappened() {
		// TODO Auto-generated method stub
		// Needs to determine if a significant event has passed, like someone getting attacked
		return false;
	}


	@Override
	public void terminalStep(StateView newstate, History.HistoryView statehistory) {
		step++;
		gameNumber++;
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
