package project1;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;
import java.io.IOException;
import java.nio.*;
import java.util.*;
import static java.lang.Math.pow;
import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.stb.STBImage.*;
import static org.lwjgl.stb.STBEasyFont.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;
public class CoordinateGrid2_3 implements Runnable {
    // 状态变量
    private boolean handMode = false; // ⼿形拖动模式标志
    private boolean isDragging = false; // 是否正在拖动
    private boolean GridMode = false; // 是否显示⽹格
    // 窗⼝和坐标系相关变量
    private long window; // GLFW窗⼝句柄
    private int windowWidth, windowHeight; // 窗⼝宽⾼
    private final int gridStep = 1; // ⽹格步⻓
    // ⽹格尺⼨(会根据加载的图⽚⾃动调整)
    private int gridWidth = 10; // 默认⽹格宽度
    private int gridHeight = 10; // ⽹格⾼度
    // 视图变换参数
    private float scale = 1.0f; // 缩放⽐例
    private float offsetX = 0.0f, offsetY = 0.0f; // 偏移量
    // 图⽚相关变量
    private int textureID = -1; // 纹理ID
    private int imageWidth = 0; // 图⽚宽度
    private int imageHeight = 0; // 图⽚⾼度
    private boolean imageLoaded = false; // 图⽚是否已加载
    // ⿏标交互相关
    private double currentMouseX, currentMouseY; // 当前⿏标位置
    private double lastMouseX, lastMouseY; // 上次⿏标位置
    private boolean ctrlPressed = false; // Ctrl键是否按下
    private static final float DRAG_SENSITIVITY = 1.0f; // 拖动灵敏度
    // 绘制的⼏何元素
    private List<Point> pointsList = new ArrayList<>(); // 点集合
    private List<List<Point>> pointListListPreview = new ArrayList< >();
    private List<List<Point>> pointListListSave = new ArrayList< >();

    private List<Line> linesPreview = new ArrayList<>(); // 线集合
    private List<Line> linesSave = new ArrayList<>(); // 线集合

    private List<Point> seedPoints = new ArrayList<>(); // 种⼦点(⽤于路径)
    private boolean isClosed = false; // 路径是否闭合
    private float scaleCircle = 1.0f; // 圆圈缩放⽐例
    // 字体渲染相关
    private int fontVBO; // 字体顶点缓冲对象
    private int fontVAO; // 字体顶点数组对象
    private int fontShaderProgram; // 字体着⾊器程序
    // 颜⾊常量
    private static final float[] GRID_COLOR = {0.5f, 0.5f, 0.5f, 1.0f}; // ⽹格颜⾊
    private static final float[] AXIS_COLOR = {0.0f, 0.0f, 0.0f, 1.0f}; // 坐标轴颜⾊
    private static final float[] POINT_COLOR = {0.0f, 1.0f, 0.0f, 1.0f}; // 点颜⾊
    private static final float[] LINE_COLOR = {0.0f, 0.0f, 1.0f, 1.0f}; // 线颜⾊
    private static final float[] TEXT_COLOR = {0.0f, 0.0f, 0.0f, 1.0f}; // ⽂本颜⾊
    // 光标相关
    private long handCursor = 0; // ⼿形光标
    private long arrowCursor = 0; // 箭头光标
    private boolean cursorsInitialized = false; // 光标是否已初始化
    // 时间控制
    private final double UPDATE_RATE = 1.0 / 30.0; // 更新频率(30FPS)
    private final double MAX_FRAME_TIME = 0.25; // 最⼤帧时间

    List<Point> latestPath;


    //记录全局最后保留line
//    private List<Point> latestPath = pointListListPreview.get(pointListListPreview.size() - 1); // 获取最新路径

    public static void main(String[] args) {
        new CoordinateGrid2_3().run();
    }
    @Override
    public void run() {
        init(); // 初始化
        loop(); // 主循环
        cleanup(); // 清理资源
    }
    /**
     * 初始化GLFW、OpenGL和应⽤程序状态
     */
    private void init() {
// 设置GLFW错误回调
        GLFWErrorCallback.createPrint(System.err).set();
// 初始化GLFW库
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }
// 配置GLFW窗⼝
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); // 初始不可⻅
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE); // 允许调整⼤⼩
// 获取主显示器信息并设置窗⼝⼤⼩为屏幕⼀半
        long primaryMonitor = glfwGetPrimaryMonitor();
        GLFWVidMode vidMode = glfwGetVideoMode(primaryMonitor);
        windowWidth = vidMode.width() / 2;
        windowHeight = vidMode.height() / 2;
// 创建GLFW窗⼝
        window = glfwCreateWindow(windowWidth, windowHeight, "Coordinate Grid", NULL,
                NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }
// 设置各种回调函数
        glfwSetKeyCallback(window, this::keyCallback);
        glfwSetCursorPosCallback(window, this::currentMousePosCallback);
        glfwSetMouseButtonCallback(window, this::mouseClickCallback);
        glfwSetScrollCallback(window, this::scrollCallback);
        glfwSetFramebufferSizeCallback(window, this::framebufferSizeCallback);
// 居中窗⼝
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            glfwGetWindowSize(window, pWidth, pHeight);
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            glfwSetWindowPos(window, (vidmode.width() - pWidth.get(0)) / 2,
                    (vidmode.height() - pHeight.get(0)) / 2);
        }
// 设置OpenGL上下⽂并启⽤垂直同步
        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);
// 初始化OpenGL功能
        GL.createCapabilities();
// 初始化字体渲染系统
        initFontRendering();
// 配置STB Image库(垂直翻转图⽚)
        stbi_set_flip_vertically_on_load(true);
    }
    /**
     * 初始化光标资源
     */
    private void initCursors() {
        if (!cursorsInitialized) {
            handCursor = glfwCreateStandardCursor(GLFW_HAND_CURSOR); // ⼿形光标
            arrowCursor = glfwCreateStandardCursor(GLFW_ARROW_CURSOR); // 箭头光标
            cursorsInitialized = true;
        }
    }
    /**
     * 初始化字体渲染系统
     */
    private void initFontRendering() {
// 顶点着⾊器源代码
        String vertexShaderSource = "#version 110\n" +
                "attribute vec2 position;\n" +
                "void main() {\n" +
                " gl_Position = vec4(position, 0.0, 1.0);\n" +
                "}";
// ⽚段着⾊器源代码
        String fragmentShaderSource = "#version 110\n" +
                "uniform vec4 color;\n" +
                "void main() {\n" +
                " gl_FragColor = color;\n" +
                "}";
// 创建着⾊器程序
        fontShaderProgram = glCreateProgram();
        int vertexShader = glCreateShader(GL_VERTEX_SHADER);
        int fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
// 编译着⾊器
        glShaderSource(vertexShader, vertexShaderSource);
        glShaderSource(fragmentShader, fragmentShaderSource);
        glCompileShader(vertexShader);
        glCompileShader(fragmentShader);
// 附加并链接着⾊器程序
        glAttachShader(fontShaderProgram, vertexShader);
        glAttachShader(fontShaderProgram, fragmentShader);
        glLinkProgram(fontShaderProgram);
// 清理着⾊器对象
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
    }
    /**
     * 主渲染循环
     */
    private void loop() {
// 初始化时间变量
        double lastTime = GLFW.glfwGetTime();
        double accumulator = 0.0;
// 设置清屏颜⾊(⽩⾊)
        glClearColor(1.0f, 1.0f, 1.0f, 0.0f);
// 主循环
        while (!glfwWindowShouldClose(window)) {
// 计算帧时间
            double currentTime = GLFW.glfwGetTime();
            double frameTime = currentTime - lastTime;
            lastTime = currentTime;
// 限制最⼤帧时间(防⽌卡顿)
            if (frameTime > MAX_FRAME_TIME) {
                frameTime = MAX_FRAME_TIME;
            }
            accumulator += frameTime;
// 清空屏幕
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
// 设置视⼝
            glViewport(0, 0, windowWidth, windowHeight);
// 设置正交投影矩阵
            glMatrixMode(GL_PROJECTION);
            glLoadIdentity();
            glOrtho(0, windowWidth, windowHeight, 0, -1, 1);
// 切换到模型视图矩阵
            glMatrixMode(GL_MODELVIEW);
            glLoadIdentity();
// Check if seedPoints has elements and getPosition() is valid
            // 固定时间步长更新
            while (accumulator >= UPDATE_RATE) {
                accumulator -= UPDATE_RATE;

                // Check if seedPoints has elements and getPosition() is valid
                if (!seedPoints.isEmpty() && getPosition() != null) {
                    Point start = seedPoints.getLast();
                    Point end = getPosition();
                    pointsList = AStar.Node.convertNodesToPoints(
                            AStar.findPath((int)start.x, (int)start.y, (int)end.x, (int)end.y, ImageProcess.costMatrix)
                    );
                } else {
                    pointsList = Collections.emptyList(); // Handle empty case
                }
                renewLine();
                addNewPointsPreview(pointsList);
//                addNewPointsSave(latestPath);不需要每次loop都要录入。
            }

// 渲染场景
            drawAll();
// 交换缓冲区并处理事件
            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }
    /**
     * 加载图⽚⽂件
     * @param path 图⽚⽂件路径
     */
    private void loadImage(String path) {
        try (MemoryStack stack = stackPush()) {
// 分配缓冲区存储图⽚尺⼨和通道信息
            IntBuffer w = stack.mallocInt(1); // 宽度
            IntBuffer h = stack.mallocInt(1); // ⾼度
            IntBuffer comp = stack.mallocInt(1); // 通道数
// 加载图⽚(强制4通道RGBA)
            ByteBuffer imageBuffer = stbi_load(path, w, h, comp, 4);
            if (imageBuffer == null) {
                System.err.println("Failed to load image: " + stbi_failure_reason());
                return;
            }
// 更新图⽚尺⼨
            imageWidth = w.get(0);
            imageHeight = h.get(0);
// 根据图⽚尺⼨设置⽹格
            gridWidth = imageWidth;
            gridHeight = imageHeight;
// ⽣成并绑定纹理
            textureID = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, textureID);
// 设置纹理参数
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
// 上传纹理数据到GPU
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, imageWidth, imageHeight,
                    0, GL_RGBA, GL_UNSIGNED_BYTE, imageBuffer);
// 释放图⽚内存
            stbi_image_free(imageBuffer);
// 标记图⽚已加载
            imageLoaded = true;

            ImageProcess.processImageToSobel(path);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    /**
     * 绘制所有内容(图⽚、⽹格、点和线)
     */
    private void drawAll() {
// 计算保持宽⾼⽐的显示尺⼨
        float imageAspect = (float)imageWidth / imageHeight;
        float displayWidth, displayHeight;
        if (windowWidth / windowHeight > imageAspect) {
            displayHeight = windowHeight * scale;
            displayWidth = displayHeight * imageAspect;
        } else {
            displayWidth = windowWidth * scale;
            displayHeight = displayWidth / imageAspect;
        }
// 计算绘制位置(居中+偏移)
        float posX = (windowWidth - displayWidth) / 2 + offsetX;
        float posY = (windowHeight - displayHeight) / 2 + offsetY;
// 保存当前变换矩阵
        glPushMatrix();
// 应⽤整体变换(位置+缩放)
        glTranslatef(posX, posY, 0);
        glScalef(displayWidth / gridWidth, displayHeight / gridHeight, 1);
// 1. 绘制图⽚(最底层)
        if (imageLoaded) {
            glEnable(GL_TEXTURE_2D);
            glBindTexture(GL_TEXTURE_2D, textureID);
// 绘制纹理四边形
            glBegin(GL_QUADS);
            glTexCoord2f(0, 0); glVertex2f(0, gridHeight);
            glTexCoord2f(1, 0); glVertex2f(gridWidth, gridHeight);
            glTexCoord2f(1, 1); glVertex2f(gridWidth, 0);
            glTexCoord2f(0, 1); glVertex2f(0, 0);
            glEnd();
            glDisable(GL_TEXTURE_2D);
        }
// 2. 绘制⽹格(中间层)
        if(GridMode){
            drawGrid();
        }
// 3. 绘制预览线
        drawPointsAndLinesPreview();
//4. 绘制保存线
        drawLinesSave();
// 恢复变换矩阵
        glPopMatrix();
    }
    /**
     * 绘制点和线
     */
    private void drawPointsAndLinesPreview() {
// 绘制线条
        if (!pointListListPreview.isEmpty()) {

            latestPath = pointListListPreview.get(pointListListPreview.size() - 1); // 获取最新路径
            if (latestPath.size() >= 2) { // 确保有可绘制的线段
                glColor4f(LINE_COLOR[0], LINE_COLOR[1], LINE_COLOR[2], LINE_COLOR[3]);
                glBegin(GL_LINES);
                // 动态生成最新路径的线段并绘制
                for (int i = 0; i < latestPath.size() - 1; i++) {
                    Point p1 = latestPath.get(i);
                    Point p2 = latestPath.get(i + 1);
                    glVertex2f((float) p1.x, (float) p1.y);
                    glVertex2f((float) p2.x, (float) p2.y);
                }
                glEnd();
            }
            else {
                latestPath = new ArrayList<>(); // 处理空情况
            }
        }

// 绘制反⾊圆圈(种⼦点标记)
        if (!seedPoints.isEmpty()) {
// 启⽤反⾊混合模式
            glEnable(GL_BLEND);
            glBlendFunc(GL_ONE_MINUS_DST_COLOR, GL_ZERO);
// 计算圆圈半径(考虑缩放)
            float radius = gridWidth * scaleCircle / (scale * 30.0f);
            glColor3f(1, 1, 1); // 必须设置为⽩⾊
// 为每个种⼦点绘制圆圈
            for (Point point : seedPoints) {
                glBegin(GL_LINE_LOOP);
                for (int i = 0; i < 32; i++) {
                    double angle = 2.0 * Math.PI * i / 32;
                    glVertex2f(
                            (float) (point.x + (float)(radius * Math.cos(angle))),
                            (float) (point.y + (float)(radius * Math.sin(angle)))
                    );
                }
                glEnd();
            }
// 恢复默认混合模式
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        }
    }
    private void drawLinesSave() {
        if (!linesSave.isEmpty()) { // 改为检查 linesSave
            glColor4f(LINE_COLOR[0], LINE_COLOR[1], LINE_COLOR[2], LINE_COLOR[3]);
            glBegin(GL_LINES);
            for (Line line : linesSave) {
                glVertex2f(line.x1, line.y1);
                glVertex2f(line.x2, line.y2);
            }
            glEnd();
        }
    }
//// 绘制线条
//        if (!lines.isEmpty()) {
//            glColor4f(LINE_COLOR[0], LINE_COLOR[1], LINE_COLOR[2], LINE_COLOR[3]);
//            glBegin(GL_LINES);
//            for (Line line : lines) {
//                glVertex2f(line.x1, line.y1);
//                glVertex2f(line.x2, line.y2);
//            }
//        }

    /**
     * 绘制⽹格和坐标轴
     */
    private void drawGrid() {
// 设置⽹格颜⾊
        glColor4f(GRID_COLOR[0], GRID_COLOR[1], GRID_COLOR[2], GRID_COLOR[3]);
        glBegin(GL_LINES);
// 绘制垂直线(每5个单位⼀条)
        for (int x = 0; x <= gridWidth; x += 5) {
            glVertex2f(x, 0);
            glVertex2f(x, gridHeight);
        }
// 绘制⽔平线(每5个单位⼀条)
        for (int y = 0; y <= gridHeight; y += 5) {
            glVertex2f(0, y);
            glVertex2f(gridWidth, y);
        }
// 绘制坐标轴(⿊⾊)
        glColor4f(AXIS_COLOR[0], AXIS_COLOR[1], AXIS_COLOR[2], AXIS_COLOR[3]);
        glLineWidth(2);
// X轴
        glVertex2f(0, gridHeight/2);
        glVertex2f(gridWidth, gridHeight/2);
// Y轴
        glVertex2f(gridWidth/2, 0);
        glVertex2f(gridWidth/2, gridHeight);
        glEnd();
        glLineWidth(1); // 恢复线宽
    }
    /**
     * 根据点集合更新线集合
     */
    private void renewLine() {
        this.linesPreview.clear();
        // 遍历所有路径点列表
        for (List<Point> pointList : pointListListPreview) {
            int size = pointList.size();
            // 确保列表中有足够的点来形成线段
            if (size >= 2) {
                // 连接当前列表中的相邻点形成线段
                for (int i = 0; i < size - 1; i++) {
                    Point p1 = pointList.get(i);
                    Point p2 = pointList.get(i + 1);
                    this.linesPreview.add(new Line((float) p1.x, (float) p1.y, (float) p2.x, (float) p2.y));

                }

            }
        }

    }

//    private void renewLine(){
//        this.lines.clear();
//// 连接相邻点形成线条
//        for(int i =1; i< pointListList.size() - 1; i++){
//            List<Point> currentList = pointListList.get(i);
//            if(currentList.size()>=2) {
//                for (int j = 0; j < currentList.size(); j++) {
//                    Point p1 = currentList.get(i);
//                    Point p2 = currentList.get(i + 1);//i还是j？
//                    this.lines.add(new Line((float) p1.x, (float) p1.y, (float) p2.x,
//                            (float) p2.y));//强转了float
//                }
//            }
//        }
//    }
    /**
     * 在窗⼝左下⻆显示当前缩放⽐例
     */
    private void displayScale() {
        String scaleText = String.format("Scale: %.2f", scale);
// 分配缓冲区存储字体数据
        ByteBuffer charBuffer = MemoryUtil.memAlloc(scaleText.length() * 270);
        int quads = stb_easy_font_print(0, 0, scaleText, null, charBuffer);
// 设置正交投影矩阵
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, windowWidth, windowHeight, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        glTranslatef(10, windowHeight - 20, 0);
// 使⽤字体着⾊器
        glUseProgram(fontShaderProgram);
        int colorLoc = glGetUniformLocation(fontShaderProgram, "color");
        glUniform4f(colorLoc, TEXT_COLOR[0], TEXT_COLOR[1], TEXT_COLOR[2],
                TEXT_COLOR[3]);
// 绑定并上传顶点数据
        glBindVertexArray(fontVAO);
        glBindBuffer(GL_ARRAY_BUFFER, fontVBO);
        glBufferData(GL_ARRAY_BUFFER, charBuffer, GL_STATIC_DRAW);
        glScalef(15f, 15f, 1); // 放⼤字体
// 绘制⽂本
        glDrawArrays(GL_QUADS, 0, quads * 4);
// 恢复OpenGL状态
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        glUseProgram(0);
// 恢复矩阵
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
// 释放内存
        MemoryUtil.memFree(charBuffer);
    }
// ================== 回调函数 ==================
    /**
     * 键盘回调函数
     */
    private void keyCallback(long window, int key, int scancode, int action, int mods) {
// ESC键退出
        if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
            glfwSetWindowShouldClose(window, true);
        }
// 检测Ctrl键状态
        if (key == GLFW_KEY_LEFT_CONTROL || key == GLFW_KEY_RIGHT_CONTROL) {
            ctrlPressed = action != GLFW_RELEASE;
        }
// L键加载图⽚
        if (key == GLFW_KEY_L && action == GLFW_PRESS) {
            loadImage("src/img2.png");
        }
        if (key == GLFW_KEY_R && action == GLFW_PRESS) {
            loadImage("src/example.png");
        }
// H键切换⼿形模式
        if (key == GLFW_KEY_H && action == GLFW_PRESS) {
            handMode = !handMode;
// 初始化光标(如果未初始化)
            if (!cursorsInitialized) {
                initCursors();
            }
// 设置对应光标
            glfwSetCursor(window, handMode ? handCursor : arrowCursor);
        }
// G键切换⽹格显示
        if (key == GLFW_KEY_G && action == GLFW_PRESS) {
            GridMode = !GridMode;
        }
//enter直接闭合，扣图
//        if(key == GLFW_KEY_ENTER){
//            int size = 0;
//            for(int i =0;i<pointListList.size();i++){
//                size += pointListList.get(i).size();
//            }
//            if(size>3){
//                if(isClosed){
////出图
//                }else{
//// 第⼀次，未闭合，将其闭合
////添加计算路径返回point List的代码
//                }
//            }
//        }
        if (key == GLFW_KEY_Z && action == GLFW_PRESS && ctrlPressed) {
            if(isClosed){
                isClosed = false;
//删除最后⼀段路径代码
            }else{
                seedPoints.removeLast();
//删除最后⼀段路径的代码
            }
        }
        if (key == GLFW_KEY_P && action == GLFW_PRESS) {
// calculatePath();
        }
    }
    //实时模式下触发路径，有待确认，似乎没有⽤上检测四周范围然后确定grid
    private void currentMousePosCallback(long window, double xpos, double ypos) {
// 更新⿏标位置
        currentMouseX = xpos;
        currentMouseY = ypos;
// ⼿形模式下的拖动处理
        if (handMode && isDragging) {
// 计算⿏标移动增量
            double dx = xpos - lastMouseX;
            double dy = ypos - lastMouseY;
// 更新偏移量
            offsetX += dx;
            offsetY += dy;
// 限制偏移范围(防⽌拖出视图)
            float effectiveWidth = imageWidth * scale * (float)1.1;
            float effectiveHeight = imageHeight * scale * (float)1.2;
            offsetX = Math.max(-effectiveWidth, Math.min(effectiveWidth, offsetX));
            offsetY = Math.max(-effectiveHeight, Math.min(effectiveHeight, offsetY));
// 更新最后位置
            lastMouseX = xpos;
            lastMouseY = ypos;
        }
//A*相关
// else if(!handMode){
// // 实时路径计算
// if (isPathCalculating && pathStartPoint != null) {
// Point currentEnd = new Point(xpos, ypos);
// calculatePath(pathStartPoint, currentEnd);
// }
// }
    }
    // 返回当前⿏标位置（实时）
    public Point getCurrentMousePos() {
        return new Point(currentMouseX, currentMouseY);
    }
    /**
     * ⿏标按钮回调函数
     */
    private void mouseClickCallback(long window, int button, int action, int mods) {
// 左键处理
        if(button == GLFW_MOUSE_BUTTON_LEFT){
            if(handMode){
                if (action == GLFW_PRESS) {
                    isDragging = true;
                    lastMouseX = currentMouseX;
                    lastMouseY = currentMouseY;
                } else if (action == GLFW_RELEASE) {
                    isDragging = false;
                }
            }else{
                if (action == GLFW_PRESS) {
// 实现标点
                    Point p = getPosition();
// 检查是否在坐标系范围内
                    if (p!=null) {
// 添加最近的⽹格点
                        seedPoints.add(p);
//此处在测试renewLine()
// renewLine();
                        System.out.println(p.x + "N" + p.y);

                        double cost = ImageProcess.getCostMatrix((int) getCurrentMousePos().x, (int) getCurrentMousePos().y);  // 正常位置
                        System.out.println("Cost: " + cost);
// 如果有多个点，考虑闭合
// 检查是否可以闭合
                        if (seedPoints.size() > 2 && !isClosed) {
                            Point firstPoint = seedPoints.get(0);
                            float distance = (float) Math.sqrt(
                                    pow(p.x - firstPoint.x, 2) +
                                            pow(p.y - firstPoint.y, 2)
                            );
// 闭合阈值设为⽹格步⻓的1.5倍
//                            if (distance < gridStep * 1.5f) {
//                                linesPreview.add(new Line((float) p.x, (float) p.y, (float)
//                                        firstPoint.x, (float) firstPoint.y));
//                                isClosed = true;
//                            }
                            //新的方法来实现闭合：
                            if (distance < gridStep * 1.5f) {
                                // 闭合路径，确保 latestPath 包含闭合线段
                                latestPath.add(seedPoints.get(0)); // 将起点加入路径末尾

                                isClosed = true;
                                // 清空预览相关列表，避免重复
                                pointListListPreview.clear();
                                linesPreview.clear();
                            }
                            //在点击的时候要保存上一个节点的路径绘制，存到Save当中

                        }
                        if (seedPoints.size()>=2) {
                            addNewPointsSave(latestPath);
                        }

                    }
                }
            }
        }
        else if(GLFW_MOUSE_BUTTON_RIGHT == button){
            if(!handMode){
                if(action == GLFW_PRESS){
                    Point p = getPosition();
// 找到最新的seedpoint，然后判断点击范围是否接近
                    Point prePoint = seedPoints.getLast();
                    int distance = (int)Math.sqrt(pow(prePoint.x - p.x,2)+pow(prePoint.y
                            - p.y,2));
                    float optDist = distance / scale; // 考虑缩放时点击的准确性会下降
                    System.out.println("距离最近seedpoint：" + optDist);
                    if(optDist< 10){
                        seedPoints.removeLast();
                    }
                }
            }
        }
    }
    /**
     * ⿏标滚轮回调函数
     */
    private void scrollCallback(long window, double xoffset, double yoffset) {
        if (ctrlPressed) {
// Ctrl+滚轮: 缩放视图
            float oldScale = scale;
// 计算新缩放⽐例(限制在0.1-10之间)
            scale *= (1.0f + (float)yoffset * 0.1f);
            scale = Math.max(0.1f, Math.min(scale, 10.0f));
// 计算⿏标在归⼀化坐标系中的位置
            float[] gridPos = screenToGridCoordinates((float) currentMouseX, (float)
                    currentMouseY);
            float mouseGridX = gridPos[0] / gridWidth;
            float mouseGridY = gridPos[1] / gridHeight;
// 调整偏移量实现以⿏标为中⼼的缩放
            offsetX += (mouseGridX - 0.5f) * gridWidth * (1 - scale/oldScale) *
                    (windowWidth/(float)gridWidth);
            offsetY += (mouseGridY - 0.5f) * gridHeight * (1 - scale/oldScale) *
                    (windowHeight/(float)gridHeight);
        }else{
// 单纯滚轮: 调整圆圈⼤⼩
            float oldScale = scaleCircle;
            scaleCircle *= (1.0f + (float)yoffset * 0.1f);
            scaleCircle = Math.max(0.1f, Math.min(scaleCircle, 10.0f));
        }
    }
    /**
     * 将屏幕坐标转换为⽹格坐标
     */
    private float[] screenToGridCoordinates(float screenX, float screenY) {
// 计算保持宽⾼⽐的显示尺⼨
        float imageAspect = (float)imageWidth / imageHeight;
        float displayWidth, displayHeight;
        if (windowWidth / windowHeight > imageAspect) {
            displayHeight = windowHeight * scale;
            displayWidth = displayHeight * imageAspect;
        } else {
            displayWidth = windowWidth * scale;
            displayHeight = displayWidth / imageAspect;
        }
// 计算绘制位置(居中+偏移)
        float posX = (windowWidth - displayWidth) / 2 + offsetX;
        float posY = (windowHeight - displayHeight) / 2 + offsetY;
// 转换为⽹格坐标
        float gridX = (screenX - posX) * gridWidth / displayWidth;
        float gridY = (screenY - posY) * gridHeight / displayHeight;
// 返回并限制在有效范围内
        return new float[]{
                Math.max(0, Math.min(gridWidth, gridX)),
                Math.max(0, Math.min(gridHeight, gridY))
        };
    }
    /**
     * 窗⼝⼤⼩改变回调
     */
    private void framebufferSizeCallback(long window, double xoffset, double yoffset) {
        windowWidth = (int) xoffset;
        windowHeight = (int) yoffset;
    }
    /**
     * 清理资源
     */
    private void cleanup() {
// 销毁光标
        if (cursorsInitialized) {
            glfwDestroyCursor(handCursor);
            glfwDestroyCursor(arrowCursor);
        }
// 清理OpenGL资源
        glDeleteBuffers(fontVBO);
        glDeleteVertexArrays(fontVAO);
        glDeleteProgram(fontShaderProgram);
// 清理GLFW资源
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        Objects.requireNonNull(glfwSetErrorCallback(null)).free();
    }
// ================== 内部类 ==================
    /**
     * 表示2D点的内部类
     */
    public static class Point {
        public final double x, y;
        public Point(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }
    /**
     * 表示2D线段的内部类
     */
    private static class Line {
        float x1, y1, x2, y2;
        Line(float x1, float y1, float x2, float y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }
    }
// ================== 辅助⽅法 ==================
    /**
     * 获取⿏标点击点在⽹格上的对⻬位置
     */
    public Point getPosition(){
        float[] gridPos = screenToGridCoordinates((float) currentMouseX, (float)
                currentMouseY);
        float mouseGridX = gridPos[0];
        float mouseGridY = gridPos[1];
// 检查是否在有效范围内
        if (mouseGridX >= 0 && mouseGridX <= gridWidth &&
                mouseGridY >= 0 && mouseGridY <= gridHeight) {
// 计算最近的⽹格点
            float nearestX = Math.round(mouseGridX / gridStep) * gridStep;
            nearestX = Math.max(0, Math.min(nearestX, gridWidth));
            float nearestY = Math.round(mouseGridY / gridStep) * gridStep;
            nearestY = Math.max(0, Math.min(nearestY, gridHeight));
            return new Point(nearestX, nearestY);
        }
        return null;
    }
    /**
     * 更新点集合
     */
    public void addNewPointsPreview(List<Point> points){
        this.pointListListPreview.add(points);
    }
    public void addNewPointsSave(List<Point> points) {
//        this.pointListListSave.add(points);
        if (points != null && !points.isEmpty()) {
            // 生成线段并保存到 linesSave
            pointListListSave.add(new ArrayList<>(points)); // 添加点列表副本
            for (int i = 0; i < points.size() - 1; i++) {
                Point p1 = points.get(i);
                Point p2 = points.get(i + 1);
                linesSave.add(new Line((float)p1.x, (float)p1.y, (float)p2.x, (float)p2.y));
            }
            // 如果是闭合路径，添加首尾连接线段
            if (isClosed && points.size() >= 2) {
                Point first = points.get(0);
                Point last = points.get(points.size() - 1);
                linesSave.add(new Line((float)last.x, (float)last.y, (float)first.x, (float)first.y));
            }
        }
    }

}