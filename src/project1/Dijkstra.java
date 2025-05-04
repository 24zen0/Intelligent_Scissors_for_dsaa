package project1;

import java.util.*;

public class Dijkstra {
    // 静态成员共享图像数据
    private static double[][] sobelMatrix;
    private static int width, height;
    private static Dijkstra[][] nodeGrid; // 节点网格

    // 节点属性
    public int x, y;
    public double distance;
    public Dijkstra predecessor;
    public boolean visited;

    // 初始化网格和Sobel数据
    public static void initialize(double[][] sobel, int w, int h) {
        sobelMatrix = sobel;
        width = w;
        height = h;
        nodeGrid = new Dijkstra[h][w];

        // 创建所有节点
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Dijkstra node = new Dijkstra();
                node.x = x;
                node.y = y;
                node.distance = Double.POSITIVE_INFINITY;
                node.visited = false;
                nodeGrid[y][x] = node;
            }
        }
    }

    // 根据坐标获取节点
    public static Dijkstra getNode(int x, int y) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            return nodeGrid[y][x];
        }
        return null;
    }

    // 计算从起点到终点的最短路径
    public static List<Dijkstra> findPath(int startX, int startY, int endX, int endY) {
        Dijkstra start = getNode(startX, startY);
        Dijkstra end = getNode(endX, endY);
        if (start == null || end == null) return Collections.emptyList();

        resetNodes(); // 重置所有节点状态
        PriorityQueue<Dijkstra> queue = new PriorityQueue<>(Comparator.comparingDouble(n -> n.distance));
        start.distance = 0;
        queue.add(start);

        while (!queue.isEmpty()) {
            Dijkstra current = queue.poll();
            if (current.visited) continue;
            if (current == end) break; // 提前终止

            current.visited = true;
            for (Dijkstra neighbor : getNeighbors(current)) {
                double cost = getCost(current, neighbor);
                double newDist = current.distance + cost;

                if (newDist < neighbor.distance) {
                    neighbor.distance = newDist;
                    neighbor.predecessor = current;
                    if (!queue.contains(neighbor)) {
                        queue.add(neighbor);
                    }
                }
            }
        }

        return buildPath(end); // 回溯路径
    }

    // 获取邻居节点（8邻域）
    private static List<Dijkstra> getNeighbors(Dijkstra node) {
        int[][] dirs = {{-1,-1}, {-1,0}, {-1,1}, {0,-1}, {0,1}, {1,-1}, {1,0}, {1,1}};
        List<Dijkstra> neighbors = new ArrayList<>();
        for (int[] d : dirs) {
            Dijkstra n = getNode(node.x + d[0], node.y + d[1]);
            if (n != null) neighbors.add(n);
        }
        return neighbors;
    }

    // 计算移动成本（结合Sobel边缘强度）
    private static double getCost(Dijkstra from, Dijkstra to) {
        // 对角线移动成本为√2，否则为1
        double distCost = (Math.abs(from.x - to.x) + Math.abs(from.y - to.y)) == 2
                ? Math.sqrt(2) : 1.0;
        // 使用目标像素的Sobel值作为权重（边缘越强，成本越高）
        return distCost * (1.0 + sobelMatrix[to.y][to.x]);
    }

    // 回溯路径
    private static List<Dijkstra> buildPath(Dijkstra end) {
        LinkedList<Dijkstra> path = new LinkedList<>();
        while (end != null) {
            path.addFirst(end);
            end = end.predecessor;
        }
        return path;
    }

    // 重置所有节点状态
    private static void resetNodes() {
        for (Dijkstra[] row : nodeGrid) {
            for (Dijkstra node : row) {
                node.distance = Double.POSITIVE_INFINITY;
                node.visited = false;
                node.predecessor = null;
            }
        }
    }
}