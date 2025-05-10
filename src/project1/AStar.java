package project1;
import java.util.*;

public class AStar {
    public static List<Node> findPath(int startX, int startY, int endX, int endY, double[][] costMatrix) {
        PriorityQueue<Node> open = new PriorityQueue<>();
        boolean[][] closed = new boolean[costMatrix.length][costMatrix[0].length];
        double[][] gScore = new double[costMatrix.length][costMatrix[0].length];
        for (double[] row : gScore) Arrays.fill(row, Double.MAX_VALUE);

        gScore[startY][startX] = 0;
        open.add(new Node(startX, startY, 0, heuristic(startX, startY, endX, endY), null));

        int[][] dirs = {{-1,0}, {1,0}, {0,-1}, {0,1}, {-1,-1}, {-1,1}, {1,-1}, {1,1}};

        while (!open.isEmpty()) {
            Node current = open.poll();
            if (current.x == endX && current.y == endY)
                return reconstructPath(current);

            if (closed[current.y][current.x]) continue;
            closed[current.y][current.x] = true;

            for (int[] d : dirs) {
                int nx = current.x + d[0];
                int ny = current.y + d[1];
                if (nx <0 || nx >= costMatrix[0].length || ny<0 || ny >= costMatrix.length)
                    continue;

                double moveCost = costMatrix[ny][nx];
                double dist = (Math.abs(d[0]) + Math.abs(d[1]) == 2) ? Math.sqrt(2) : 1.0;
                double tentativeG = current.g + moveCost * dist;

                if (tentativeG < gScore[ny][nx]) {
                    gScore[ny][nx] = tentativeG;
                    open.add(new Node(nx, ny, tentativeG, heuristic(nx, ny, endX, endY), current));
                }
            }
        }
        return Collections.emptyList();
    }

    private static double heuristic(int x1, int y1, int x2, int y2) {
        return Math.sqrt(Math.pow(x1-x2, 2) + Math.pow(y1-y2, 2));
    }

    private static List<Node> reconstructPath(Node node) {
        LinkedList<Node> path = new LinkedList<>();
        while (node != null) {
            path.addFirst(node);
            node = node.parent;
        }
        return path;
    }

    static class Node implements Comparable<Node> {
        int x, y;
        double g, h;
        Node parent;
        double f() { return g + h; }

        Node(int x, int y, double g, double h, Node parent) {
            this.x = x; this.y = y;
            this.g = g; this.h = h;
            this.parent = parent;
        }

        @Override
        public int compareTo(Node other) {
            return Double.compare(this.f(), other.f());
        }
    }
}
