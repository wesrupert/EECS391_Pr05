
public class Node {
	private int x;
	private int y;
	private float probability = 0.01f;
	private Node parent;
	
//	public Node(int x, int y) {
//		this.x = x;
//		this.y = y;
//	}
	
	public Node(int x, int y, float probability) {
		this.x = x;
		this.y = y;
		this.probability = probability;
	}

//	public Node(Node node, Node parent) {
//		this.x = node.x;
//		this.y = node.y;
//		this.probability = node.probability;
//		this.parent = parent;
//	}
	
	public Node(int x, int y, float probability, Node parent) {
		this.x = x;
		this.y = y;
		this.probability = probability;
		this.parent = parent;
	}

	public int getX() {
		return x;
	}

	public int getY() {
		return y;
	}

	public float getProbability() {
		return probability;
	}

	public void setProbability(float probability) {
		this.probability = probability;
	}
	
	public Node getParent() {
		return parent;
	}
	
	@Override
	public boolean equals(Object o) {
		if (o == null) {
            return false;
		}
        if (o == this) {
            return true;
        }
        if (!(o instanceof Node)) {
            return false;
        }
        
        Node node = (Node) o;
        return x == node.getX() && y == node.getY();
	}

	public float getCost(Node target) {
		return this.getHeuristicCost(target) + this.getAccumulatedCost();
	}

	public float getAccumulatedCost() {
		float accumCost;
		if (parent == null) {
            accumCost = probability;
		} else {
            accumCost = parent.getAccumulatedCost() + probability;
		}
		return accumCost;
	}

	private float getHeuristicCost(Node target) {
		int x = Math.abs(this.x - target.getX());
        int y = Math.abs(this.y - target.getY());
        int cost = Math.max(x, y);
        return (float)cost * 0.01f;
	}
	
	public String toString() {
		return "Node at " + x + "," + y + " with probability " + probability;
	}
}
