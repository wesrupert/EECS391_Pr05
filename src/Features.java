import java.util.List;
import java.util.Map;


public class Features {
	private static final int NUM_FEATURES = 7;
	
	private double[] w;
	private double[] f;
	
	public Features() {
		
		w = new double[NUM_FEATURES];
		
		// Initialize random weights in (-1,1)
		for (int i = 0; i < w.length; i++) {
			w[i] = Math.random() * 2 - 1;
		}
	}
	
	public double qFunction(double[] f) {
		double q = 0;
		
		for (int i = 0; i < NUM_FEATURES; i++) {
			q += w[i] * f[i];
		}
		
		return q;
	}
	
	public static double[] getFeatures(Integer footman, Integer enemy, List<Integer> footmen, List<Integer> enemyFootmen, Map<Integer, Integer> unitHealth, Map<Integer, Pair<Integer, Integer>> unitLocations, Map<Integer, Integer> attack) {
		double[] f = new double[NUM_FEATURES];
		// constant
		f[0] = 1;
		
		// health of footman
		f[1] = unitHealth.get(footman);
		
		// health of enemy
		f[2] = unitHealth.get(enemy);
		
		// # of other friendly units currently attacking enemy
		f[3] = 0;
		for (Integer attacker : attack.keySet()) {
			if (attacker == footman) {
				continue;
			}
			f[3] += attack.get(attacker) == enemy ? 1 : 0;
		}
		
		// is enemy the current target of footman?
		f[4] = (attack.get(footman) == null || attack.get(footman) == enemy) ? 1 : 0;
		
		// what is the ratio of hitpoints of enemy to footman
		f[5] = unitHealth.get(footman) / unitHealth.get(enemy);
		
		// is enemy my closest enemy?
		f[6] = isClosest(footman, enemy, enemyFootmen, unitLocations) ? 1 : 0;
		
		return f;
	}

	private static boolean isClosest(Integer footman, Integer enemy, List<Integer> enemyFootmen, Map<Integer, Pair<Integer, Integer>> unitLocations) {
		int enemyDist = chebyshevDistance(unitLocations.get(footman), unitLocations.get(enemy));
		
		for (Integer curEnemy : enemyFootmen) {
			if (chebyshevDistance(unitLocations.get(footman), unitLocations.get(curEnemy)) < enemyDist) {
				return false;
			}
		}
		
		return true;
	}
	
	private static int chebyshevDistance(Pair<Integer, Integer> p, Pair<Integer, Integer> q) {
		int x = p.getX() - q.getX();
		int y = p.getY() - q.getY();
		return Math.max(x, y);
	}
}
