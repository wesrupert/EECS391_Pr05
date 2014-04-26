import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class State {
	private List<Integer> footmen = new ArrayList<Integer>();
	private List<Integer> enemyFootmen = new ArrayList<Integer>();
	private Map<Integer, Integer> unitHealth = new HashMap<Integer, Integer>();
	private Map<Integer, Pair<Integer, Integer>> unitLocations = new HashMap<Integer, Pair<Integer, Integer>>();
	
	public State(List<Integer> footmen, List<Integer> enemyFootmen, Map<Integer, Integer> unitHealth, Map<Integer, Pair<Integer, Integer>> unitLocations) {
		this.footmen = new ArrayList<Integer>(footmen);
		this.enemyFootmen = new ArrayList<Integer>(enemyFootmen);
		this.unitHealth = new HashMap<Integer, Integer>(unitHealth);
		this.unitLocations = new HashMap<Integer, Pair<Integer, Integer>>(unitLocations);
	}

	public List<Integer> getFootmen() {
		return footmen;
	}

	public List<Integer> getEnemyFootmen() {
		return enemyFootmen;
	}

	public Map<Integer, Integer> getUnitHealth() {
		return unitHealth;
	}

	public Map<Integer, Pair<Integer, Integer>> getUnitLocations() {
		return unitLocations;
	}
}
