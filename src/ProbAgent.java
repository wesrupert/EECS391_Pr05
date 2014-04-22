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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.agent.Agent;
import edu.cwru.sepia.environment.model.history.History;
import edu.cwru.sepia.environment.model.state.ResourceNode;
import edu.cwru.sepia.environment.model.state.ResourceNode.ResourceView;
import edu.cwru.sepia.environment.model.state.ResourceType;
import edu.cwru.sepia.environment.model.state.State.StateView;
import edu.cwru.sepia.environment.model.state.Unit.UnitView;
import edu.cwru.sepia.util.Direction;

/**
 * This agent will first collect gold to produce a peasant,
 * then the two peasants will collect gold and wood separately until reach goal.
 * @author Feng
 *
 */
public class ProbAgent extends Agent {
	private static final long serialVersionUID = -4047208702628325380L;
	private static final int GOLD_REQUIRED = 2000;	
	private static final int PEASANT_RANGE = 2;
	private static final int TOWER_RANGE = 4;
	private static final float TOWER_ACCURACY = 0.75f;
	private static final float INITIAL_TOWER_DENSITY = 0.01f;
	private static final double RANDOM_WALK_PROB = 0.75; // the probability that a peasant should walk in a random direction after being attacked

	private int step;
	private int startingPeasants = 0;
	private Map<Integer, Integer> peasantHealth = new HashMap<Integer, Integer>();
	private Map<Integer, Pair<Integer, Integer>> peasantLocations = new HashMap<Integer, Pair<Integer, Integer>>();
	private GameBoard board;
	private boolean randomWalk = true;
	
	private boolean persistentMode = false;
	private String boardSaveName = "board.board";
	
	private boolean foundGoldMine = false;
	private Pair<Integer, Integer> estGoldMineLocation;
	
	private StateView currentState;
	
	public ProbAgent(int playernum, String[] arguments) {
		super(playernum);
		
		// Persistent mode saves a probability distribution for use across multiple games
		for (String arg : arguments) {
			if (arg.equalsIgnoreCase("Persistent")) {
				System.out.println("Persistent mode enabled");
				persistentMode = true;
			}
		}
	}

	
	@Override
	public Map<Integer, Action> initialStep(StateView newstate, History.HistoryView statehistory) {
		step = 0;
		
		currentState = newstate;
		
		int width = currentState.getXExtent();
		int height = currentState.getYExtent();
		
		
		estGoldMineLocation = new Pair<Integer, Integer>(width - PEASANT_RANGE, PEASANT_RANGE);
		
		for (UnitView unit : currentState.getUnits(playernum)) {
			String unitTypeName = unit.getTemplateView().getName();
			if(unitTypeName.equals("Peasant")) {
				startingPeasants++;
				peasantHealth.put(unit.getID(), unit.getHP());
				peasantLocations.put(unit.getID(), new Pair<Integer, Integer>(unit.getXPosition(), unit.getYPosition()));
			}
		}
		
		board = new GameBoard(width, height, INITIAL_TOWER_DENSITY);
		
		// Load the probabilities from a previous run if enabled
		if (persistentMode) {
			boardSaveName = getUniqueNameForBoard();
			GameBoard loaded = GameBoard.loadGameBoard(boardSaveName);
			if (loaded != null) {
				board = loaded;
				randomWalk = false;
			}
		}
		
		return middleStep(newstate, statehistory);
	}

	private String getUniqueNameForBoard() {
		int width = currentState.getXExtent();
		int height = currentState.getYExtent();
		
		// make a unique id for this configuration
		int id = 0;
		for (Integer pId : peasantLocations.keySet()) {
			id += pId * (peasantLocations.get(pId).getX() + peasantLocations.get(pId).getY());
		}
		
		return (width + "x" + height + "_" + id + ".board");
	}


	@Override
	public Map<Integer,Action> middleStep(StateView newState, History.HistoryView statehistory) {
		step++;
		
		Map<Integer,Action> builder = new HashMap<Integer,Action>();
		currentState = newState;
		
		int currentGold = currentState.getResourceAmount(0, ResourceType.GOLD);
		if (currentGold >= GOLD_REQUIRED) {
			System.out.println("Completed objective!");
			System.exit(0);
		}

		List<UnitView> peasants = new ArrayList<UnitView>();
		List<UnitView> townhalls = new ArrayList<UnitView>();
		
		for (UnitView unit : currentState.getUnits(playernum)) {
			String unitTypeName = unit.getTemplateView().getName();
			if(unitTypeName.equals("TownHall")) {
				townhalls.add(unit);
			} else if(unitTypeName.equals("Peasant")) {
				peasants.add(unit);
				peasantLocations.put(unit.getID(), new Pair<Integer, Integer>(unit.getXPosition(), unit.getYPosition()));
			}
		}
		
		// We are dead
		if (peasants.size() == 0) {
			System.out.println("Dead.");
			if (!alreadySaved) {
				board.print();
				if (persistentMode) {
					System.out.println("Saving board for next time:");
					board.serializeGameBoard(boardSaveName);
				}
				alreadySaved = true;
			}
			System.exit(0);
		}
		
		
		// Build a new peasant if we have lost any
		if (peasants.size() < startingPeasants && currentGold >= peasants.get(0).getTemplateView().getGoldCost()) {
			
			int townhallID = townhalls.get(0).getID();
			int peasantTemplateID = currentState.getTemplate(playernum, "Peasant").getID();
			System.out.println("Making new peasant");
			builder.put(townhallID, Action.createCompoundProduction(townhallID, peasantTemplateID));
		}
		
		List<UnitView> hitList = new ArrayList<>();
		
		// Find all the peasants and update probabilities
		for (UnitView peasant : peasants) {
			int x = peasant.getXPosition();
			int y = peasant.getYPosition();
			
			// update the board with everything that the peasants can see
			updatePeasantViewRange(x, y);
			
			// increment all the squares that peasants have visited
			board.incrementVisits(x, y);
			
			// start tracking any newly made peasants
			if (!peasantHealth.containsKey(peasant.getID())) {
				peasantHealth.put(peasant.getID(), peasant.getHP());
				peasantLocations.put(peasant.getID(), new Pair<Integer, Integer>(peasant.getXPosition(), peasant.getYPosition()));
			}
			
			// find peasants that have taken damage
			if (peasantHealth.get(peasant.getID()) > peasant.getHP()) {
				System.out.println("Peasant " + peasant.getID() + " has been hit!");
				
				// update the probabilities based on a tower hit
				board.incrementHits(x, y);
				updateFromHit(x, y, true);
				randomWalk = false;
				board.print();
				hitList.add(peasant);
			} else {
				
				// update probabilities based on no hit
				updateFromHit(x, y, false);
			}
			peasantHealth.put(peasant.getID(), peasant.getHP());
		}
		
		// determine actions for each peasant
		for (UnitView peasant : peasants) {
			if (randomWalk) { // if no peasant has been hit yet, randomly walk around

				Node nextStep = randomAdjacentNode(peasant.getXPosition(), peasant.getYPosition());
				Direction direction = getDirection(nextStep.getX() - peasant.getXPosition(), nextStep.getY() - peasant.getYPosition());
				
				Action a = Action.createPrimitiveMove(peasant.getID(), direction);
				builder.put(peasant.getID(), a);
				
				peasantLocations.put(peasant.getID(), new Pair<Integer, Integer>(nextStep.getX(), nextStep.getY()));
			} else if (peasant.getCargoAmount() == 0) {
				if (isAdjacent(peasant.getXPosition(), peasant.getYPosition(), estGoldMineLocation.getX(), estGoldMineLocation.getY())
						&& foundGoldMine) { // harvest gold
					System.out.println("Found gold mine, harvesting!");
					Action a = Action.createCompoundGather(peasant.getID(), currentState.resourceAt(estGoldMineLocation.getX(), estGoldMineLocation.getY()));
					builder.put(peasant.getID(), a);
				} else { // move towards goldmine
					List<Pair<Integer, Integer>> bestPath = getBestPath(new Pair<Integer, Integer>(peasant.getXPosition(), peasant.getYPosition()), estGoldMineLocation);
					Pair<Integer, Integer> nextStep = bestPath.get(0);
					
					// if a peasant has been hit, randomly walk around with some probability
					if (hitList.contains(peasant) && Math.random() < RANDOM_WALK_PROB) {
						Node node = randomAdjacentNode(peasant.getXPosition(), peasant.getYPosition());
						nextStep = new Pair<Integer, Integer>(node.getX(), node.getY());
					}
					
					Direction direction = getDirection(nextStep.getX() - peasant.getXPosition(), nextStep.getY() - peasant.getYPosition());
					
					Action a = Action.createPrimitiveMove(peasant.getID(), direction);
					builder.put(peasant.getID(), a);
					
					peasantLocations.put(peasant.getID(), new Pair<Integer, Integer>(nextStep.getX(), nextStep.getY()));
				}
			} else { 
				if (isAdjacent(peasant.getXPosition(), peasant.getYPosition(),
						townhalls.get(0).getXPosition(), townhalls.get(0).getYPosition())) { // Deposit gold
					System.out.println("Depositing!");
					Action a = Action.createCompoundDeposit(peasant.getID(), townhalls.get(0).getID());
					builder.put(peasant.getID(), a);
				} else { // move towards townhall
					List<Pair<Integer, Integer>> bestPath = getBestPath(new Pair<Integer, Integer>(peasant.getXPosition(), peasant.getYPosition()),
							new Pair<Integer, Integer>(townhalls.get(0).getXPosition(), townhalls.get(0).getYPosition()));
					Pair<Integer, Integer> nextStep = bestPath.get(0);
					
					// if a peasant has been hit, randomly walk around with some probability
					if (hitList.contains(peasant) && Math.random() < RANDOM_WALK_PROB) {
						Node node = randomAdjacentNode(peasant.getXPosition(), peasant.getYPosition());
						nextStep = new Pair<Integer, Integer>(node.getX(), node.getY());
					}
					
					Direction direction = getDirection(nextStep.getX() - peasant.getXPosition(), nextStep.getY() - peasant.getYPosition());
					
					Action a = Action.createPrimitiveMove(peasant.getID(), direction);
					builder.put(peasant.getID(), a);
					
					peasantLocations.put(peasant.getID(), new Pair<Integer, Integer>(nextStep.getX(), nextStep.getY()));
				}
			}
		}
		
		return builder;
	}

	@Override
	public void terminalStep(StateView newstate, History.HistoryView statehistory) {
		step++;
		
	}
	
	private Node randomAdjacentNode(int locX, int locY) {
		
		Node current = new Node(locX, locY, getHitProbability(locX, locY));
		
		// We need a node to put into getAdjacentNodes that won't get hit with random walk and isn't the townhall
		// This is just a hack to stop peasants from getting stuck behind the townhall...
		Node fudge = new Node(estGoldMineLocation.getX(), estGoldMineLocation.getY(), getHitProbability(estGoldMineLocation.getX(), estGoldMineLocation.getY()));
		List<Node> adjacentNodes = getAdjacentNodes(current, new ArrayList<Node>(), fudge);
		
		Random random = new Random();
		return adjacentNodes.get(random.nextInt(adjacentNodes.size()));
	}
	
    private Direction getDirection(int x, int y) {
        if (x == 1 && y == 0) {
            return Direction.EAST;
        } else if (x == 1 && y == -1) {
            return Direction.NORTHEAST;
        } else if (x == 0 && y == -1) {
            return Direction.NORTH;
        } else if (x == -1 && y == -1) {
            return Direction.NORTHWEST;
        } else if (x == -1 && y == 0) {
            return Direction.WEST;
        } else if (x == -1 && y == 1) {
            return Direction.SOUTHWEST;
        } else if (x == 0 && y == 1) {
            return Direction.SOUTH;
        } else if (x == 1 && y == 1) {
            return Direction.SOUTHEAST;
        } else {
            System.out.printf("Something bad happened while calculating direction!!! X:%dY:%d\n", x, y);
            return null;
        }
    }
	
    /**
     * Given a peasant locaiton, updates all the squares that it can see with relevant information
     * @param x
     * @param y
     */
	private void updatePeasantViewRange(int x, int y) {
		for(int i = -PEASANT_RANGE; i <= PEASANT_RANGE; i++) {
			for(int j = -PEASANT_RANGE; j <= PEASANT_RANGE; j++) {
				updateSeen(x + i, y + j);
			}
		}
	}
	
	/**
	 * Given a square in range of a peasant, marks it as seen and checks whether there is anything interesting.
	 * If there is, it marks its location
	 * @param x
	 * @param y
	 */
	private void updateSeen(int x, int y) {
		if (!currentState.inBounds(x, y)) {
			return;
		}
		
		board.setSeen(x, y, true);
		
		if (currentState.isResourceAt(x, y)) {
        	ResourceView resource = currentState.getResourceNode(currentState.resourceAt(x, y));
        	if(resource.getType().equals(ResourceNode.Type.GOLD_MINE)) {
//        		System.out.printf("FOUND GOLDMINE AT %d,%d\n", x, y);
        		foundGoldMine = true;
        		estGoldMineLocation = new Pair<Integer, Integer>(x, y);
        	} else if (resource.getType().equals(ResourceNode.Type.TREE)) {
        		board.setHasTree(x, y, true);
        	}
        	
        	board.setTowerProbability(x, y, 0);
        } else if (currentState.isUnitAt(x, y)) {
        	int unitID = currentState.unitAt(x, y);
        	
            String unitName = currentState.getUnit(unitID).getTemplateView().getName();
            if(unitName.equalsIgnoreCase("ScoutTower")) {
            	System.out.println("Found tower!");
        		board.setTowerProbability(x, y, 1);
            } else {
            	board.setTowerProbability(x, y, 0);
            }
        }
		
		// Need to check if our original estimate for the goldmine location has been found
		if (!foundGoldMine && board.getSeen(estGoldMineLocation.getX(), estGoldMineLocation.getY())) {
			System.out.printf("No gold mine at %d,%d\n", estGoldMineLocation.getX(), estGoldMineLocation.getY());
			updateGoldMineEstimate();
		}
	}
	
	/**
	 * Finds the closest unseen square to the original estimated gold mine location
	 * @param x
	 * @param y
	 */
	private void updateGoldMineEstimate() {
		int x = currentState.getXExtent() - PEASANT_RANGE;
	    int y = PEASANT_RANGE;
		for (int range = 1; range < Math.max(currentState.getXExtent(), currentState.getYExtent()); range++) {
			for (int i = x - range; i <= x + range; i++) {
				for (int j = y - range; j <= y + range; j++) {
					if (currentState.inBounds(i, j) && !board.getSeen(i, j)) {
						estGoldMineLocation = new Pair<Integer, Integer>(i, j);
						System.out.printf("New gold mine estimate at %d,%d\n", estGoldMineLocation.getX(), estGoldMineLocation.getY());
						return;
					}
				}
			}
		}
	}

	/**
	 * Gets the probability that you will get hit by a tower in the given square
	 * @param x
	 * @param y
	 * @return The probability
	 */
	private float getHitProbability(int x, int y) {
		float probability = 0;
		
		for (int i = -TOWER_RANGE; i <= TOWER_RANGE; i++) {
			for (int j = -TOWER_RANGE; j <= TOWER_RANGE; j++) {
				int curX = x + i;
				int curY = y + j;
				if (currentState.inBounds(curX, curY)
						&& distance(x, y, curX, curY) <= TOWER_RANGE) { // tower shoots in a circular range
					probability = (probability + board.getTowerProbability(curX, curY)) - (probability * board.getTowerProbability(curX, curY));
				}
			}
		}
		
		return probability * TOWER_ACCURACY;
	}

	/**
	 * Updates the probability map for tower locations
	 * @param x
	 * @param y
	 * @param hit
	 */
	private void updateFromHit(int x, int y, boolean hit) {
		float[][] old = board.getBoardCopy();
		int fromX = Math.max(x - TOWER_RANGE, 0);
		int toX   = Math.min(old.length, x + TOWER_RANGE);
		int fromY = Math.max(y - TOWER_RANGE, 0);
		int toY   = Math.min(old[0].length, y + TOWER_RANGE);
		for (int r = fromX; r < toX; r++) {
			for (int c = fromY; c < toY; c++) {
				if (board.getSeen(r, c) // Only need to update out-of-view cells.
						|| distance(x, y, r, c) <= TOWER_RANGE) { // tower has circular range
					continue; 
				}

				float phn, pht;
				if (hit) {
					// P(H|N) = 1 - P(S|N)
					phn = 1;
					for (int rr = fromX; rr < toX; rr++) {
						for (int cc = fromY; cc < toY; cc++) {
							if (rr == r && cc == c) continue;
							// P(S) = P(N)+(P(T)*(1-P(H)))
							phn *= (1f - old[rr][cc]) + (old[rr][cc] * (1f - TOWER_ACCURACY));
						}
					}
					phn = 1f - phn;

					// P(H|T): same as above, but without skipping r, c
					// Simplifies to 1-((1-P(H|N))*P(M)) since tower existing is given.
					pht = 1f - ((1f - phn) * (1f - TOWER_ACCURACY));
				} else {
					// P(S|N) = P(S)
					phn = 1;
					for (int rr = fromX; rr < toX; rr++) {
						for (int cc = fromY; cc < toY; cc++) {
							if (rr == r && cc == c) continue;
							// P(S) = P(N)+(P(T)*(1-P(H)))
							phn *= (1f - old[rr][cc]) + (old[rr][cc] * (1f - TOWER_ACCURACY));
						}
					}

					// P(S|T): same as above, but without skipping r, c
					// Simplifies to P(H|N))*P(M) since tower existing is given.
					pht = phn * (1f - TOWER_ACCURACY);
				}

				// P(T|H) = P(H|T)*P(T)/(P(H|T)*P(T)+P(H|N)*P(N))
				board.setTowerProbability(r, c, pht * old[r][c] / (pht * old[r][c] + phn * (1 - old[r][c])));
			}
		}
	}


	private double distance(int x1, int y1, int x2, int y2) {
		return Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
	}

	/**
	 * Finds the path with the lowest total probability of getting hit using A* search
	 * @param curLocation
	 * @param dest
	 * @return The path to the destination
	 */
	private List<Pair<Integer, Integer>> getBestPath(Pair<Integer, Integer> curLocation, Pair<Integer, Integer> dest) {
		List<Pair<Integer, Integer>> path =  new LinkedList<Pair<Integer, Integer>>();
		
		
		Node current = new Node(curLocation.getX(), curLocation.getY(), getHitProbability(curLocation.getX(), curLocation.getY()));
		Node target = new Node(dest.getX(), dest.getY(), getHitProbability(dest.getX(), dest.getY()));
		List<Node> openSet = new ArrayList<>();
        List<Node> closedSet = new ArrayList<>();
        
        
        while (true) {
            openSet.remove(current);
            List<Node> adjacent = getAdjacentNodes(current, closedSet, target);

            // Find the adjacent node with the lowest heuristic cost.
            for (Node neighbor : adjacent) {
            	boolean inOpenset = false;
            	List<Node> openSetCopy = new ArrayList<>(openSet);
            	for (Node node : openSetCopy) {
            		if (neighbor.equals(node)) {
            			inOpenset = true;
            			if (neighbor.getAccumulatedCost() < node.getAccumulatedCost()) {
            				openSet.remove(node);
            				openSet.add(neighbor);
            			}
            		}
            	}
            	
            	if (!inOpenset) {
            		openSet.add(neighbor);
            	}
            }

            // Exit search if done.
            if (openSet.isEmpty()) {
                System.out.printf("Target (%d, %d) is unreachable from position (%d, %d).\n",
                                target.getX(), target.getY(), curLocation.getX(), curLocation.getY());
                return null;
            } else if (/*isAdjacent(current, target)*/ current.equals(target)) {
                break;
            }

            // This node has been explored now.
            closedSet.add(current);

            // Find the next open node with the lowest cost.
            Node next = openSet.get(0);
            for (Node node : openSet) {
                if (node.getCost(target) < next.getCost(target)) {
                    next = node;
                }
            }
//            System.out.println("Moving to node: " + current);
            current = next;
        }
        
        // Rebuild the path using the node parents
        path.add(new Pair<Integer, Integer>(curLocation.getX(), curLocation.getY()));
        while(current.getParent() != null) {
        	current = current.getParent();
        	path.add(0, new Pair<Integer, Integer>(current.getX(), current.getY()));
        }
        
        if (path.size() > 1) {
        	path.remove(0);
        }
        
		return path;
	}

	private boolean isAdjacent(Node current, Node target) {
        return isAdjacent(current.getX(), current.getY(), target.getX(), target.getY());
	}
	
	private boolean isAdjacent(int x, int y, int targetX, int targetY) {
	        for (int i = x - 1; i <= x + 1; i++) {
	            for (int j = y - 1; j <= y + 1; j++) {
	                if (i == targetX && j == targetY) {
	                    return true;
	                }
	            }
	        }
	        return false;
	}

	/**
	 * Finds all adjacent nodes, ignoring out of bounds squares, occupied squares, and squares in the closed list
	 * @param current
	 * @param closedSet
	 * @return The adjacent nodes
	 */
	private List<Node> getAdjacentNodes(Node current, List<Node> closedSet, Node dest) {
		List<Node> adjacent = new ArrayList<Node>();
		
		for (int i = -1; i <=1; i++) {
			inner:
			for (int j = -1; j <=1; j++) {
				if (i == 0 && j == 0) {
					continue;
				}
				int x = current.getX() + i;
				int y = current.getY() + j;
				if (!currentState.inBounds(x, y)
						|| board.getHasTree(x, y)
						|| peasantAt(x,y)
						|| board.getTowerProbability(x, y) == 1
						|| isTownHallAt(x, y, dest)) {
					continue;
				}
				Node node = new Node(x, y, getHitProbability(x, y), current);
				for (Node visitedNode : closedSet) {
					if (node.equals(visitedNode)) {
						continue inner;
					}
				}
				adjacent.add(node);
			}
		}
//		System.out.println("AT " + current);
//		System.out.println("NEIGHBORS:");
//		for (Node node : adjacent) {
//			System.out.println("\t" + node);
//		}
		
		return adjacent;
	}
	
	/**
	 * If the destination is not the townhall, then we don't want to include that square in our search
	 * @param x
	 * @param y
	 * @return True if the townhall is at that location AND we aren't traveling to it
	 */
	private boolean isTownHallAt(int x, int y, Node dest) {
		if (x == dest.getX() && y == dest.getY()) {
			return false;
		}
		
		if (currentState.isUnitAt(x, y)) {
			int unitID = currentState.unitAt(x, y);
        	
            String unitName = currentState.getUnit(unitID).getTemplateView().getName();
            if(unitName.equalsIgnoreCase("Townhall")) {
            	return true;
            }
		}
	
		return false;
	}
	
	private boolean peasantAt(int x, int y) {
		Set<Integer> keys = peasantLocations.keySet();
		for (Integer id : keys) {
			Pair<Integer, Integer> pair = peasantLocations.get(id);
			if (x == pair.getX() && y == pair.getY()) {
				return true;
			}
		}
		return false;
	}

	public static String getUsage() {
		return "Determines the location of enemy towers and avoids them in order to collect 2000 gold.";
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
