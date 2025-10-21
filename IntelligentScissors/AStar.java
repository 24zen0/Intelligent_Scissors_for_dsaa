package project1;
import java.util.*;

public class AStar {
    // 启发式权重 - 1.0为标准A*，>1为权重A*（更快但可能不是最优）
    private static final double HEURISTIC_WEIGHT = 1.0;

    // 8方向移动
    private static final int[][] DIRECTIONS_8 = {
            {-1, 0}, {1, 0}, {0, -1}, {0, 1},  // 水平和垂直方向
            {-1, -1}, {-1, 1}, {1, -1}, {1, 1}  // 对角线方向
    };

    // 方向移动成本 - 对角线移动成本为√2
    private static final double[] DIRECTION_COSTS = {
            1.0, 1.0, 1.0, 1.0,  // 水平和垂直方向
            Math.sqrt(2), Math.sqrt(2), Math.sqrt(2), Math.sqrt(2)  // 对角线方向
    };

    public static List<Node> findPath(int startX, int startY, int endX, int endY, double[][] costMatrix) {
        // 添加边界检查
        if (startX < 0 || startX >= costMatrix[0].length ||
                startY < 0 || startY >= costMatrix.length) {
            return Collections.emptyList();
        }

        PriorityQueue<Node> open = new PriorityQueue<>();
        boolean[][] closed = new boolean[costMatrix.length][costMatrix[0].length];
        double[][] gScore = new double[costMatrix.length][costMatrix[0].length];
        for (double[] row : gScore) Arrays.fill(row, Double.MAX_VALUE);

        gScore[startY][startX] = 0;
        open.add(new Node(startX, startY, 0, heuristic(startX, startY, endX, endY), null));

        while (!open.isEmpty()) {
            Node current = open.poll();
            if (current.x == endX && current.y == endY)
                return smoothPath(reconstructPath(current), costMatrix);

            if (closed[current.y][current.x]) continue;
            closed[current.y][current.x] = true;

            // 使用8方向移动
            for (int dir = 0; dir < DIRECTIONS_8.length; dir++) {
                int[] d = DIRECTIONS_8[dir];
                int nx = current.x + d[0];
                int ny = current.y + d[1];

                // 边界检查
                if (nx < 0 || nx >= costMatrix[0].length || ny < 0 || ny >= costMatrix.length)
                    continue;

                // 确保成本访问安全
                double moveCost = costMatrix[ny][nx];

                // 对角线移动时检查对角相邻的两个点是否都是可行的
                if (dir >= 4) {  // 对角线方向
                    int ax = current.x + (d[0] != 0 ? d[0] : 0);
                    int ay = current.y;
                    int bx = current.x;
                    int by = current.y + (d[1] != 0 ? d[1] : 0);

                    // 如果对角相邻的两个点中任何一个不可行，则不能对角线移动
                    if (costMatrix[ay][ax] > 0.9 || costMatrix[by][bx] > 0.9)
                        continue;
                }

                double dist = DIRECTION_COSTS[dir];
                double tentativeG = current.g + moveCost * dist;

                if (tentativeG < gScore[ny][nx]) {
                    gScore[ny][nx] = tentativeG;
                    open.add(new Node(nx, ny, tentativeG,
                            adaptiveHeuristic(nx, ny, endX, endY, costMatrix), current));
                }
            }
        }
        return Collections.emptyList();
    }

    private static double heuristic(int x1, int y1, int x2, int y2) {
        // 欧几里得距离
        return HEURISTIC_WEIGHT * Math.sqrt(Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2));
    }

    // 自适应启发式函数 - 根据周围环境动态调整权重
    private static double adaptiveHeuristic(int x, int y, int endX, int endY, double[][] costMatrix) {
        double baseHeuristic = heuristic(x, y, endX, endY);

        // 计算周围区域的平均成本
        double avgCost = 0;
        int count = 0;
        int radius = 5; // 检查周围5x5区域

        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int nx = x + dx;
                int ny = y + dy;

                if (nx >= 0 && nx < costMatrix[0].length && ny >= 0 && ny < costMatrix.length) {
                    avgCost += costMatrix[ny][nx];
                    count++;
                }
            }
        }

        if (count > 0) {
            avgCost /= count;
        }

        // 根据平均成本调整启发式权重
        // 成本高的区域（如障碍物附近）增加权重，使算法更关注实际成本
        // 成本低的区域减少权重，使算法更关注启发式估计
        double adaptiveWeight = HEURISTIC_WEIGHT * (0.5 + avgCost);

        return adaptiveWeight * baseHeuristic;
    }

    static List<Node> reconstructPath(Node point) {
        List<Node> path = new ArrayList<>();
        while (point != null) {
            path.add(0, point); // 添加到列表开头
            point = point.parent;
        }
        return path;
    }

    // 路径平滑处理
    public static List<Node> smoothPath(List<Node> path, double[][] costMatrix) {
        if (path.size() <= 2) return path;

        // 第一步：应用RDP算法简化路径
        List<Node> simplifiedPath = rdpSimplify(path, costMatrix, 1.0);

        // 第二步：应用贝塞尔曲线平滑处理
        return applyBezierSmoothing(simplifiedPath, costMatrix);
    }

    // Ramer-Douglas-Peucker算法简化路径
    private static List<Node> rdpSimplify(List<Node> path, double[][] costMatrix, double epsilon) {
        if (path.size() <= 2) return path;

        double dmax = 0;
        int index = 0;
        int end = path.size() - 1;

        // 找到距离首尾连线最远的点
        for (int i = 1; i < end; i++) {
            double d = perpendicularDistance(path.get(i), path.get(0), path.get(end));
            if (d > dmax) {
                index = i;
                dmax = d;
            }
        }

        List<Node> result = new ArrayList<>();

        // 如果最远距离大于阈值，则递归处理
        if (dmax > epsilon) {
            List<Node> recResults1 = rdpSimplify(path.subList(0, index + 1), costMatrix, epsilon);
            List<Node> recResults2 = rdpSimplify(path.subList(index, end + 1), costMatrix, epsilon);

            // 合并结果
            result.addAll(recResults1.subList(0, recResults1.size() - 1));
            result.addAll(recResults2);
        } else {
            // 否则只保留首尾点
            result.add(path.get(0));
            result.add(path.get(end));
        }

        return result;
    }

    // 计算点到线的垂直距离
    private static double perpendicularDistance(Node point, Node lineStart, Node lineEnd) {
        double x0 = point.x;
        double y0 = point.y;
        double x1 = lineStart.x;
        double y1 = lineStart.y;
        double x2 = lineEnd.x;
        double y2 = lineEnd.y;

        // 线段长度的平方
        double l2 = Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2);

        // 如果线段长度为0，返回点到起点的距离
        if (l2 == 0) return Math.sqrt(Math.pow(x0 - x1, 2) + Math.pow(y0 - y1, 2));

        // 计算投影比例
        double t = ((x0 - x1) * (x2 - x1) + (y0 - y1) * (y2 - y1)) / l2;

        // 如果投影点在线段外，返回点到最近端点的距离
        if (t < 0) return Math.sqrt(Math.pow(x0 - x1, 2) + Math.pow(y0 - y1, 2));
        if (t > 1) return Math.sqrt(Math.pow(x0 - x2, 2) + Math.pow(y0 - y2, 2));

        // 计算投影点坐标
        double projX = x1 + t * (x2 - x1);
        double projY = y1 + t * (y2 - y1);

        // 返回点到投影点的距离
        return Math.sqrt(Math.pow(x0 - projX, 2) + Math.pow(y0 - projY, 2));
    }

    // 检查两点之间是否有障碍物
    private static boolean isLineOfSight(Node a, Node b, double[][] costMatrix) {
        // 使用Bresenham算法检查直线上的所有点
        int dx = Math.abs(b.x - a.x);
        int dy = Math.abs(b.y - a.y);
        int sx = a.x < b.x ? 1 : -1;
        int sy = a.y < b.y ? 1 : -1;
        int err = dx - dy;

        int x = a.x;
        int y = a.y;

        while (x != b.x || y != b.y) {
            // 如果点在地图范围内且成本过高，认为有障碍物
            if (x >= 0 && x < costMatrix[0].length && y >= 0 && y < costMatrix.length) {
                if (costMatrix[y][x] > 0.8) { // 阈值可以调整
                    return false;
                }
            }

            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }

        return true;
    }

    // 应用贝塞尔曲线平滑路径
    private static List<Node> applyBezierSmoothing(List<Node> path, double[][] costMatrix) {
        if (path.size() <= 2) return path;

        List<Node> smoothedPath = new ArrayList<>();
        smoothedPath.add(path.get(0)); // 添加起点

        // 每三个点生成一条贝塞尔曲线
        for (int i = 1; i < path.size() - 1; i++) {
            Node p0 = path.get(i - 1);
            Node p1 = path.get(i);
            Node p2 = path.get(i + 1);

            // 生成贝塞尔曲线上的点
            for (double t = 0.1; t < 1.0; t += 0.1) {
                // 二次贝塞尔曲线公式
                double x = (1-t)*(1-t)*p0.x + 2*(1-t)*t*p1.x + t*t*p2.x;
                double y = (1-t)*(1-t)*p0.y + 2*(1-t)*t*p1.y + t*t*p2.y;

                // 取整并检查是否在地图范围内
                int ix = (int) Math.round(x);
                int iy = (int) Math.round(y);

                if (ix >= 0 && ix < costMatrix[0].length && iy >= 0 && iy < costMatrix.length) {
                    // 确保曲线上的点不会穿过障碍物
                    if (costMatrix[iy][ix] <= 0.8) {
                        smoothedPath.add(new Node(ix, iy, 0, 0, null));
                    } else {
                        // 如果穿过障碍物，使用原始点
                        smoothedPath.add(p1);
                        break;
                    }
                }
            }
        }

        smoothedPath.add(path.get(path.size() - 1)); // 添加终点

        // 第三步：应用Catmull-Rom样条曲线进行更平滑的插值
        return applyCatmullRomSmoothing(smoothedPath, costMatrix);
    }

    // 应用Catmull-Rom样条曲线平滑路径
    private static List<Node> applyCatmullRomSmoothing(List<Node> path, double[][] costMatrix) {
        if (path.size() <= 3) return path;

        List<Node> smoothedPath = new ArrayList<>();
        smoothedPath.add(path.get(0)); // 添加起点

        // 为Catmull-Rom样条添加端点控制
        Node startControl = path.get(0);
        Node endControl = path.get(path.size() - 1);

        // 每四个点生成一条Catmull-Rom样条曲线
        for (int i = 0; i < path.size() - 1; i++) {
            Node p0 = (i == 0) ? startControl : path.get(i - 1);
            Node p1 = path.get(i);
            Node p2 = path.get(i + 1);
            Node p3 = (i == path.size() - 2) ? endControl : path.get(i + 2);

            // 生成Catmull-Rom样条曲线上的点
            for (double t = 0.1; t < 1.0; t += 0.1) {
                double t2 = t * t;
                double t3 = t2 * t;

                // Catmull-Rom样条公式
                double x = 0.5 * ((2 * p1.x) +
                        (-p0.x + p2.x) * t +
                        (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2 +
                        (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3);

                double y = 0.5 * ((2 * p1.y) +
                        (-p0.y + p2.y) * t +
                        (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2 +
                        (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3);

                // 取整并检查是否在地图范围内
                int ix = (int) Math.round(x);
                int iy = (int) Math.round(y);

                if (ix >= 0 && ix < costMatrix[0].length && iy >= 0 && iy < costMatrix.length) {
                    // 确保曲线上的点不会穿过障碍物
                    if (costMatrix[iy][ix] <= 0.8) {
                        smoothedPath.add(new Node(ix, iy, 0, 0, null));
                    } else {
                        // 如果穿过障碍物，使用原始点
                        smoothedPath.add(p2);
                        break;
                    }
                }
            }
        }

        smoothedPath.add(path.get(path.size() - 1)); // 添加终点
        return smoothedPath;
    }

    static class Node implements Comparable<Node> {
        int x, y;
        double g, h;
        Node parent;

        double f() {
            return g + h;
        }

        Node(int x, int y, double g, double h, Node parent) {
            this.x = x;
            this.y = y;
            this.g = g;
            this.h = h;
            this.parent = parent;
        }

        @Override
        public int compareTo(Node other) {
            int result = Double.compare(this.f(), other.f());
            if (result != 0) return result;

            // 如果f值相同，优先选择h值较小的节点（更接近目标）
            return Double.compare(this.h, other.h);
        }

        // 将 LinkedList<Node> 转换为 LinkedList<Point>
        public static List<CoordinateGrid2_3.Point> convertNodesToPoints(List<Node> nodes) {
            List<CoordinateGrid2_3.Point> points = new ArrayList<>();
            for (Node node : nodes) {
                points.add(new CoordinateGrid2_3.Point(node.x, node.y));
            }
            return points;
        }
    }
}
