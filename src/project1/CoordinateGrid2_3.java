package project1;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;

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
    private boolean handMode = false; // 手形拖动模式标志
    private boolean isDragging = false; // 是否正在拖动
    private boolean GridMode = false; // 是否显示网格

    // 窗口和坐标系相关变量
    private long window; // GLFW窗口句柄
    private int windowWidth, windowHeight; // 窗口宽高
    private final int gridStep = 1; // 网格步长

    // 网格尺寸(会根据加载的图片自动调整)
    private int gridWidth = 10; // 默认网格宽度
    private int gridHeight = 10; // 网格高度

    // 视图变换参数
    private float scale = 1.0f; // 缩放比例
    private float offsetX = 0.0f, offsetY = 0.0f; // 偏移量

    // 图片相关变量
    private int textureID = -1; // 纹理ID
    private int imageWidth = 0; // 图片宽度
    private int imageHeight = 0; // 图片高度
    private boolean imageLoaded = false; // 图片是否已加载

    // 鼠标交互相关
    private double mouseX, mouseY; // 当前鼠标位置
    private double lastMouseX, lastMouseY; // 上次鼠标位置
    private boolean ctrlPressed = false; // Ctrl键是否按下
    private static final float DRAG_SENSITIVITY = 1.0f; // 拖动灵敏度

    // 绘制的几何元素
    private List<Point> points = new ArrayList<>(); // 点集合
    private List<Line> lines = new ArrayList<>(); // 线集合
    private List<Point> seedPoints = new ArrayList<>(); // 种子点(用于路径)
    private boolean isClosed = false; // 路径是否闭合
    private float scaleCircle = 1.0f; // 圆圈缩放比例

    // 字体渲染相关
    private int fontVBO; // 字体顶点缓冲对象
    private int fontVAO; // 字体顶点数组对象
    private int fontShaderProgram; // 字体着色器程序

    // 颜色常量
    private static final float[] GRID_COLOR = {0.5f, 0.5f, 0.5f, 1.0f}; // 网格颜色
    private static final float[] AXIS_COLOR = {0.0f, 0.0f, 0.0f, 1.0f}; // 坐标轴颜色
    private static final float[] POINT_COLOR = {0.0f, 1.0f, 0.0f, 1.0f}; // 点颜色
    private static final float[] LINE_COLOR = {0.0f, 0.0f, 1.0f, 1.0f}; // 线颜色
    private static final float[] TEXT_COLOR = {0.0f, 0.0f, 0.0f, 1.0f}; // 文本颜色

    // 光标相关
    private long handCursor = 0; // 手形光标
    private long arrowCursor = 0; // 箭头光标
    private boolean cursorsInitialized = false; // 光标是否已初始化

    // 时间控制
    private final double UPDATE_RATE = 1.0 / 30.0; // 更新频率(30FPS)
    private final double MAX_FRAME_TIME = 0.25; // 最大帧时间

    // 在CoordinateGrid2_3中添加
    private List<Point> currentLivePath = new ArrayList<>();  // 实时路径缓存
    private Deque<List<Point>> confirmedPathHistory = new ArrayDeque<>(100);  // 确认路径历史

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
     * 初始化GLFW、OpenGL和应用程序状态
     */
    private void init() {
        // 设置GLFW错误回调
        GLFWErrorCallback.createPrint(System.err).set();

        // 初始化GLFW库
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        // 配置GLFW窗口
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); // 初始不可见
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE); // 允许调整大小

        // 获取主显示器信息并设置窗口大小为屏幕一半
        long primaryMonitor = glfwGetPrimaryMonitor();
        GLFWVidMode vidMode = glfwGetVideoMode(primaryMonitor);
        windowWidth = vidMode.width() / 2;
        windowHeight = vidMode.height() / 2;

        // 创建GLFW窗口
        window = glfwCreateWindow(windowWidth, windowHeight, "Coordinate Grid", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        // 设置各种回调函数
        glfwSetKeyCallback(window, this::keyCallback);
        glfwSetCursorPosCallback(window, this::cursorPosCallback);
        glfwSetMouseButtonCallback(window, this::mouseButtonCallback);
        glfwSetScrollCallback(window, this::scrollCallback);
        glfwSetFramebufferSizeCallback(window, this::framebufferSizeCallback);

        // 居中窗口
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            glfwGetWindowSize(window, pWidth, pHeight);
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            glfwSetWindowPos(window, (vidmode.width() - pWidth.get(0)) / 2,
                    (vidmode.height() - pHeight.get(0)) / 2);
        }

        // 设置OpenGL上下文并启用垂直同步
        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);

        // 初始化OpenGL功能
        GL.createCapabilities();

        // 初始化字体渲染系统
        initFontRendering();

        // 配置STB Image库(垂直翻转图片)
        stbi_set_flip_vertically_on_load(true);
    }

    /**
     * 初始化光标资源
     */
    private void initCursors() {
        if (!cursorsInitialized) {
            handCursor = glfwCreateStandardCursor(GLFW_HAND_CURSOR); // 手形光标
            arrowCursor = glfwCreateStandardCursor(GLFW_ARROW_CURSOR); // 箭头光标
            cursorsInitialized = true;
        }
    }

    /**
     * 初始化字体渲染系统
     */
    private void initFontRendering() {
        // 顶点着色器源代码
        String vertexShaderSource = "#version 110\n" +
                "attribute vec2 position;\n" +
                "void main() {\n" +
                "    gl_Position = vec4(position, 0.0, 1.0);\n" +
                "}";

        // 片段着色器源代码
        String fragmentShaderSource = "#version 110\n" +
                "uniform vec4 color;\n" +
                "void main() {\n" +
                "    gl_FragColor = color;\n" +
                "}";

        // 创建着色器程序
        fontShaderProgram = glCreateProgram();
        int vertexShader = glCreateShader(GL_VERTEX_SHADER);
        int fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);

        // 编译着色器
        glShaderSource(vertexShader, vertexShaderSource);
        glShaderSource(fragmentShader, fragmentShaderSource);
        glCompileShader(vertexShader);
        glCompileShader(fragmentShader);

        // 附加并链接着色器程序
        glAttachShader(fontShaderProgram, vertexShader);
        glAttachShader(fontShaderProgram, fragmentShader);
        glLinkProgram(fontShaderProgram);

        // 清理着色器对象
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

        // 设置清屏颜色(白色)
        glClearColor(1.0f, 1.0f, 1.0f, 0.0f);

        // 主循环
        while (!glfwWindowShouldClose(window)) {
            // 计算帧时间
            double currentTime = GLFW.glfwGetTime();
            double frameTime = currentTime - lastTime;
            lastTime = currentTime;

            // 限制最大帧时间(防止卡顿)
            if (frameTime > MAX_FRAME_TIME) {
                frameTime = MAX_FRAME_TIME;
            }

            accumulator += frameTime;

            // 清空屏幕
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // 设置视口
            glViewport(0, 0, windowWidth, windowHeight);

            // 设置正交投影矩阵
            glMatrixMode(GL_PROJECTION);
            glLoadIdentity();
            glOrtho(0, windowWidth, windowHeight, 0, -1, 1);

            // 切换到模型视图矩阵
            glMatrixMode(GL_MODELVIEW);
            glLoadIdentity();

            // 固定时间步长更新
            while (accumulator >= UPDATE_RATE) {
                accumulator -= UPDATE_RATE;
                // 这里可以添加固定时间步长的逻辑更新

            }

            // 渲染场景
            drawAll();

            // 交换缓冲区并处理事件
            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    /**
     * 加载图片文件
     * @param path 图片文件路径
     */
    private void loadImage(String path) {
        try (MemoryStack stack = stackPush()) {
            // 分配缓冲区存储图片尺寸和通道信息
            IntBuffer w = stack.mallocInt(1); // 宽度
            IntBuffer h = stack.mallocInt(1); // 高度
            IntBuffer comp = stack.mallocInt(1); // 通道数

            // 加载图片(强制4通道RGBA)
            ByteBuffer imageBuffer = stbi_load(path, w, h, comp, 4);
            if (imageBuffer == null) {
                System.err.println("Failed to load image: " + stbi_failure_reason());
                return;
            }

            // 更新图片尺寸
            imageWidth = w.get(0);
            imageHeight = h.get(0);

            // 根据图片尺寸设置网格
            gridWidth = imageWidth;
            gridHeight = imageHeight;

            // 生成并绑定纹理
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

            // 释放图片内存
            stbi_image_free(imageBuffer);

            // 标记图片已加载
            imageLoaded = true;
        }
    }

    /**
     * 绘制所有内容(图片、网格、点和线)
     */
    private void drawAll() {
        // 计算保持宽高比的显示尺寸
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

        // 应用整体变换(位置+缩放)
        glTranslatef(posX, posY, 0);
        glScalef(displayWidth / gridWidth, displayHeight / gridHeight, 1);

        // 1. 绘制图片(最底层)
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

        // 2. 绘制网格(中间层)
        if(GridMode){
            drawGrid();
        }

        // 3. 绘制点和线(最上层)
        drawPointsAndLines();

        // 恢复变换矩阵
        glPopMatrix();
    }

    /**
     * 绘制点和线
     */
    private void drawPointsAndLines() {
        // 绘制线条
        if (!lines.isEmpty()) {
            glColor4f(LINE_COLOR[0], LINE_COLOR[1], LINE_COLOR[2], LINE_COLOR[3]);
            glBegin(GL_LINES);
            for (Line line : lines) {
                glVertex2f(line.x1, line.y1);
                glVertex2f(line.x2, line.y2);
            }
            glEnd();
        }

        // 绘制反色圆圈(种子点标记)
        if (!seedPoints.isEmpty()) {
            // 启用反色混合模式
            glEnable(GL_BLEND);
            glBlendFunc(GL_ONE_MINUS_DST_COLOR, GL_ZERO);

            // 计算圆圈半径(考虑缩放)
            float radius = gridWidth * scaleCircle / (scale * 30.0f);
            glColor3f(1, 1, 1); // 必须设置为白色

            // 为每个种子点绘制圆圈
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

    /**
     * 绘制网格和坐标轴
     */
    private void drawGrid() {
        // 设置网格颜色
        glColor4f(GRID_COLOR[0], GRID_COLOR[1], GRID_COLOR[2], GRID_COLOR[3]);
        glBegin(GL_LINES);

        // 绘制垂直线(每5个单位一条)
        for (int x = 0; x <= gridWidth; x += 5) {
            glVertex2f(x, 0);
            glVertex2f(x, gridHeight);
        }

        // 绘制水平线(每5个单位一条)
        for (int y = 0; y <= gridHeight; y += 5) {
            glVertex2f(0, y);
            glVertex2f(gridWidth, y);
        }

        // 绘制坐标轴(黑色)
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
    private void renewLine(){
        this.lines.clear();
        // 连接相邻点形成线条
        for(int i =0; i< points.size() - 1; i++){
            Point p1 = points.get(i);
            Point p2 = points.get(i+1);
            this.lines.add(new Line((float) p1.x, (float) p1.y, (float) p2.x, (float) p2.y));
        }
    }

    /**
     * 在窗口左下角显示当前缩放比例
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

        // 使用字体着色器
        glUseProgram(fontShaderProgram);
        int colorLoc = glGetUniformLocation(fontShaderProgram, "color");
        glUniform4f(colorLoc, TEXT_COLOR[0], TEXT_COLOR[1], TEXT_COLOR[2], TEXT_COLOR[3]);

        // 绑定并上传顶点数据
        glBindVertexArray(fontVAO);
        glBindBuffer(GL_ARRAY_BUFFER, fontVBO);
        glBufferData(GL_ARRAY_BUFFER, charBuffer, GL_STATIC_DRAW);
        glScalef(15f, 15f, 1); // 放大字体

        // 绘制文本
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

        // L键加载图片
        if (key == GLFW_KEY_L && action == GLFW_PRESS) {
            loadImage("src/img2.png");
        }

        // H键切换手形模式
        if (key == GLFW_KEY_H && action == GLFW_PRESS) {
            handMode = !handMode;

            // 初始化光标(如果未初始化)
            if (!cursorsInitialized) {
                initCursors();
            }

            // 设置对应光标
            glfwSetCursor(window, handMode ? handCursor : arrowCursor);
        }

        // G键切换网格显示
        if (key == GLFW_KEY_G && action == GLFW_PRESS) {
            GridMode = !GridMode;
        }
        if (key == GLFW_KEY_P && action == GLFW_PRESS) {
            calculatePath();
        }

    }

    /**
     * 鼠标移动回调函数
     */
    private void cursorPosCallback(long window, double xpos, double ypos) {
        // 更新鼠标位置
        mouseX = xpos;
        mouseY = ypos;

        // 手形模式下的拖动处理
        if (handMode && isDragging) {
            // 计算鼠标移动增量
            double dx = xpos - lastMouseX;
            double dy = ypos - lastMouseY;

            // 更新偏移量
            offsetX += dx;
            offsetY += dy;

            // 限制偏移范围(防止拖出视图)
            float effectiveWidth = imageWidth * scale * (float)1.1;
            float effectiveHeight = imageHeight * scale * (float)1.2;
            offsetX = Math.max(-effectiveWidth, Math.min(effectiveWidth, offsetX));
            offsetY = Math.max(-effectiveHeight, Math.min(effectiveHeight, offsetY));

            // 更新最后位置
            lastMouseX = xpos;
            lastMouseY = ypos;
        }
    }
// 返回当前鼠标位置（实时）
public Point getCurrentMousePosition() {
    return new Point(mouseX, mouseY);
}


    /**
     * 鼠标按钮回调函数
     */
    private void mouseButtonCallback(long window, int button, int action, int mods) {
        // 左键处理
        if(button == GLFW_MOUSE_BUTTON_LEFT){
            if(handMode){
                // 手形模式下的拖动开始/结束
                if (action == GLFW_PRESS) {
                    isDragging = true;
                    lastMouseX = mouseX;
                    lastMouseY = mouseY;
                } else if (action == GLFW_RELEASE) {
                    isDragging = false;
                }
            }else{
                // 普通模式下的点添加
                if (action == GLFW_PRESS) {
                    Point p = getPosition(); // 获取网格对齐位置
                    if (p!=null) {
                        seedPoints.add(p); // 添加种子点

                        // 检查是否可以闭合路径
                        if (seedPoints.size() > 2 && !isClosed) {
                            Point firstPoint = seedPoints.get(0);
                            float distance = (float) Math.sqrt(
                                    pow(p.x - firstPoint.x, 2) +
                                            pow(p.y - firstPoint.y, 2)
                            );
                            // 如果距离足够近则闭合路径
                            if (distance < gridStep * 1.5f) {
                                lines.add(new Line((float) p.x, (float) p.y, (float) firstPoint.x, (float) firstPoint.y));
                                isClosed = true;
                            }
                        }
                    }
                }
            }
        }
        // 右键删除最后一个种子点
        else if(GLFW_MOUSE_BUTTON_RIGHT == button){
            if(!handMode && action == GLFW_PRESS){
                Point p = getPosition();
                if(!seedPoints.isEmpty()){
                    Point prePoint = seedPoints.getLast();
                    int distance = (int)Math.sqrt(pow(prePoint.x - p.x,2)+pow(prePoint.y - p.y,2));
                    float optDist = distance / scale; // 考虑缩放因素
                    if(optDist < 10){ // 距离阈值判断
                        seedPoints.removeLast();
                    }
                }
            }
        }
    }

    /**
     * 鼠标滚轮回调函数
     */
    private void scrollCallback(long window, double xoffset, double yoffset) {
        if (ctrlPressed) {
            // Ctrl+滚轮: 缩放视图
            float oldScale = scale;

            // 计算新缩放比例(限制在0.1-10之间)
            scale *= (1.0f + (float)yoffset * 0.1f);
            scale = Math.max(0.1f, Math.min(scale, 10.0f));

            // 计算鼠标在归一化坐标系中的位置
            float[] gridPos = screenToGridCoordinates((float)mouseX, (float)mouseY);
            float mouseGridX = gridPos[0] / gridWidth;
            float mouseGridY = gridPos[1] / gridHeight;

            // 调整偏移量实现以鼠标为中心的缩放
            offsetX += (mouseGridX - 0.5f) * gridWidth * (1 - scale/oldScale) * (windowWidth/(float)gridWidth);
            offsetY += (mouseGridY - 0.5f) * gridHeight * (1 - scale/oldScale) * (windowHeight/(float)gridHeight);
        }else{
            // 单纯滚轮: 调整圆圈大小
            float oldScale = scaleCircle;
            scaleCircle *= (1.0f + (float)yoffset * 0.1f);
            scaleCircle = Math.max(0.1f, Math.min(scaleCircle, 10.0f));
        }
    }

    /**
     * 将屏幕坐标转换为网格坐标
     */
    private float[] screenToGridCoordinates(float screenX, float screenY) {
        // 计算保持宽高比的显示尺寸
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

        // 转换为网格坐标
        float gridX = (screenX - posX) * gridWidth / displayWidth;
        float gridY = (screenY - posY) * gridHeight / displayHeight;

        // 返回并限制在有效范围内
        return new float[]{
                Math.max(0, Math.min(gridWidth, gridX)),
                Math.max(0, Math.min(gridHeight, gridY))
        };
    }

    /**
     * 窗口大小改变回调
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

    // ================== 辅助方法 ==================

    /**
     * 获取鼠标点击点在网格上的对齐位置
     */
    public Point getPosition(){
        float[] gridPos = screenToGridCoordinates((float)mouseX, (float)mouseY);
        float mouseGridX = gridPos[0];
        float mouseGridY = gridPos[1];

        // 检查是否在有效范围内
        if (mouseGridX >= 0 && mouseGridX <= gridWidth &&
                mouseGridY >= 0 && mouseGridY <= gridHeight) {

            // 计算最近的网格点
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
    public void renewPoints(List<Point> points){
        this.points = points;
    }
    //for A*
    private void calculatePath() {
        if (ImageProcess.costMatrix == null) {
            System.out.println("Cost matrix not initialized. Process image first.");
            return;
        }

        Point startPoint = getPosition();
        Point endPoint = getCurrentMousePosition();
        if (startPoint == null || endPoint == null) return;

        // 转换终点到网格坐标
        float[] endGrid = screenToGridCoordinates((float)endPoint.x, (float)endPoint.y);
        int startX = (int)startPoint.x;
        int startY = (int)startPoint.y;
        int endX = (int)Math.round(endGrid[0]);
        int endY = (int)Math.round(endGrid[1]);

        // 调整y轴方向
        int matrixHeight = ImageProcess.costMatrix.length;
        int matrixStartY = matrixHeight - 1 - startY;
        int matrixEndY = matrixHeight - 1 - endY;

        // 检查边界
        if (startX <0 || startX >= ImageProcess.costMatrix[0].length || matrixStartY <0 || matrixStartY >= matrixHeight ||
                endX <0 || endX >= ImageProcess.costMatrix[0].length || matrixEndY <0 || matrixEndY >= matrixHeight) {
            System.out.println("Invalid start/end coordinates");
            return;
        }

        // 获取路径
        List<AStar.Node> path = AStar.findPath(startX, matrixStartY, endX, matrixEndY, ImageProcess.costMatrix);

        // 转换路径坐标回图形界面
        seedPoints.clear();
        for (AStar.Node node : path) {
            int guiY = matrixHeight - 1 - node.y;
            seedPoints.add(new Point(node.x, guiY));
        }
        renewLine();
    }

    // 在键盘回调中触发路径计算（例如绑定到P键）
    private void keyCallbackA(long window, int key, int scancode, int action, int mods) {
        // 原有其他按键处理...
        if (key == GLFW_KEY_P && action == GLFW_PRESS) {
            calculatePath();
        }
    }
}

