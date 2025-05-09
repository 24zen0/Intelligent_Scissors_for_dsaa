package project1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Dijkstra {
    private double[] dist;
    private int[] parent;
    private boolean[] visited;
    private final double[][] costMatrix;
    private final int start;
    private final int end;

    public Dijkstra( int start, int end) {
        this.costMatrix = ImageProcess.costMatrix;
        this.start = start;
        this.end = end;
        initialize();
        execute();
    }

    private void initialize() {
        int vexNum = costMatrix.length;
        dist = new double[vexNum];
        parent = new int[vexNum];
        visited = new boolean[vexNum];

        for (int i = 0; i < vexNum; i++) {
            dist[i] = Integer.MAX_VALUE;
            parent[i] = -1;
        }
        dist[start] = 0;
    }

    private void execute() {
        int vexNum = costMatrix.length;
        for (int i = 0; i < vexNum; i++) {
            int u = minDistance();
            if (u == -1 || u == end) break; // 提前终止条件

            visited[u] = true;
            for (int v = 0; v < vexNum; v++) {
                if (costMatrix[u][v] != 0 &&
                        dist[u] != Integer.MAX_VALUE &&
                        !visited[v] &&
                        dist[u] + costMatrix[u][v] < dist[v]) {

                    dist[v] = dist[u] + costMatrix[u][v];
                    parent[v] = u;
                }
            }
        }
    }

    private int minDistance() {
        double min = Integer.MAX_VALUE;
        int minIndex = -1;
        for (int v = 0; v < costMatrix.length; v++) {
            if (!visited[v] && dist[v] <= min) {
                min = dist[v];
                minIndex = v;
            }
        }
        return minIndex;
    }

    public List<Integer> getShortestPath() {
        List<Integer> path = new ArrayList<>();
        if (dist[end] == Integer.MAX_VALUE) return path;

        for (int v = end; v != -1; v = parent[v]) {
            path.add(v);
        }
        Collections.reverse(path);
        return path;
    }

    public double getShortestDistance() {
        return dist[end];
    }
}