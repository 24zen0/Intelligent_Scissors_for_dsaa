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
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.stb.STBImage.*;
import static org.lwjgl.stb.STBEasyFont.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

public class CoordinateGrid2_3 implements Runnable {
    // 状态变量
    private boolean handMode = false;
    private boolean isDragging = false;
    private boolean GridMode = false;
    // 窗口和坐标系相关变量
    private long window;
    private int windowWidth, windowHeight;
    private final int gridStep = 1;

    // 这里需要以图片像素为准修改宽度。而且格子数为下面的width/gap。
    private int gridWidth = 10; // 默认网格宽度
    private int gridHeight = 10;      // 网格高度会根据宽高比自动计算
    private float scale = 1.0f;
    private float offsetX = 0.0f, offsetY = 0.0f;

    // 新增图片相关变量
    private int textureID = -1;
    private int imageWidth = 0;
    private int imageHeight = 0;
    private boolean imageLoaded = false;

    // 鼠标和交互相关变量
    private double mouseX, mouseY;
    private double lastMouseX, lastMouseY;
    private boolean ctrlPressed = false;
    private static final float DRAG_SENSITIVITY = 1.0f; // 拖动灵敏度系数
    // 绘制的点和线
    private List<Point> points = new ArrayList<>();
    private List<Line> lines = new ArrayList<>();
    private List<Point> seedPoints = new ArrayList<>();
    private boolean isClosed = false;
    private float scaleCircle = 1.0f;
    // 字体渲染相关
    private int fontVBO;
    private int fontVAO;
    private int fontShaderProgram;

    // 颜色常量
    private static final float[] GRID_COLOR = {0.5f, 0.5f, 0.5f, 1.0f};
    private static final float[] AXIS_COLOR = {0.0f, 0.0f, 0.0f, 1.0f};
    private static final float[] POINT_COLOR = {0.0f, 1.0f, 0.0f, 1.0f};
    private static final float[] LINE_COLOR = {0.0f, 0.0f, 1.0f, 1.0f};
    private static final float[] TEXT_COLOR = {0.0f, 0.0f, 0.0f, 1.0f};

    // 在类变量区添加
    private long handCursor = 0;
    private long arrowCursor = 0;
    private boolean cursorsInitialized = false;

    // 时间更新
    private final double UPDATE_RATE = 1.0 / 30.0;
    private final double MAX_FRAME_TIME = 0.25;


    public static void main(String[] args) {
        new CoordinateGrid2_3().run();
    }

    public void run() {
        init();
        loop();

        // 释放资源

        cleanup();
    }

    private void init() {
        // 设置错误回调
        GLFWErrorCallback.createPrint(System.err).set();

        // 初始化GLFW
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        // 配置GLFW
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        // 获取屏幕尺寸并设置窗口大小为屏幕的一半
        long primaryMonitor = glfwGetPrimaryMonitor();
        GLFWVidMode vidMode = glfwGetVideoMode(primaryMonitor);
        windowWidth = vidMode.width() / 2;
        windowHeight = vidMode.height() / 2;


        // 创建窗口
        window = glfwCreateWindow(windowWidth, windowHeight, "Coordinate Grid", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        // 设置回调
        glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> keyCallback(window, key, scancode, action, mods));
        glfwSetCursorPosCallback(window, (window, xpos, ypos) -> cursorPosCallback(window, xpos, ypos));
        glfwSetMouseButtonCallback(window, (window, button, action, mods) -> mouseButtonCallback(window, button, action, mods));
        glfwSetScrollCallback(window, (window, xoffset, yoffset) -> scrollCallback(window, xoffset, yoffset));

        // 获取线程栈并推入一个新的栈帧
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);

            // 获取窗口大小
            glfwGetWindowSize(window, pWidth, pHeight);

            // 获取显示器分辨率
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());

            // 居中窗口
            glfwSetWindowPos(
                    window,
                    (vidmode.width() - pWidth.get(0)) / 2,
                    (vidmode.height() - pHeight.get(0)) / 2
            );
        }

        // 设置当前上下文
        glfwMakeContextCurrent(window);
        // 启用垂直同步
        glfwSwapInterval(1);
        // 显示窗口
        glfwShowWindow(window);

        // 初始化OpenGL
        GL.createCapabilities();

        // 初始化字体渲染
        initFontRendering();

        // 初始化STB Image
        stbi_set_flip_vertically_on_load(true);

    }

    // 在初始化方法中添加
    private void initCursors() {
        if (!cursorsInitialized) {
            handCursor = glfwCreateStandardCursor(GLFW_HAND_CURSOR);
            arrowCursor = glfwCreateStandardCursor(GLFW_ARROW_CURSOR);
            cursorsInitialized = true;
        }
    }

    private void initFontRendering() {
        // 创建着色器程序
        String vertexShaderSource = "#version 110\n" +
                "attribute vec2 position;\n" +
                "void main() {\n" +
                "    gl_Position = vec4(position, 0.0, 1.0);\n" +
                "}";

        String fragmentShaderSource = "#version 110\n" +
                "uniform vec4 color;\n" +
                "void main() {\n" +
                "    gl_FragColor = color;\n" +
                "}";

        fontShaderProgram = glCreateProgram();
        int vertexShader = glCreateShader(GL_VERTEX_SHADER);
        int fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);

        glShaderSource(vertexShader, vertexShaderSource);
        glShaderSource(fragmentShader, fragmentShaderSource);

        glCompileShader(vertexShader);
        glCompileShader(fragmentShader);

        glAttachShader(fontShaderProgram, vertexShader);
        glAttachShader(fontShaderProgram, fragmentShader);

        glLinkProgram(fontShaderProgram);

        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);

        // 创建VAO和VBO
        fontVAO = glGenVertexArrays();
        fontVBO = glGenBuffers();

        glBindVertexArray(fontVAO);
        glBindBuffer(GL_ARRAY_BUFFER, fontVBO);

        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, 0L);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

    }

    private void loop() {

        double lastTime = GLFW.glfwGetTime();
        double accumulator = 0.0;

        glClearColor(1.0f, 1.0f, 1.0f, 0.0f);

        // 主循环
        while (!glfwWindowShouldClose(window)) {



            double currentTime = GLFW.glfwGetTime();
            double frameTime = currentTime - lastTime;
            lastTime = currentTime;

            if (frameTime > MAX_FRAME_TIME) {
                frameTime = MAX_FRAME_TIME;
            }

            accumulator += frameTime;

            // 清空屏幕
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // 设置视口
            glViewport(0, 0, windowWidth, windowHeight);

            // 设置投影矩阵
            glMatrixMode(GL_PROJECTION);
            glLoadIdentity();
            glOrtho(0, windowWidth, windowHeight, 0, -1, 1);

            // 切换到模型视图矩阵
            glMatrixMode(GL_MODELVIEW);
            glLoadIdentity();

            // 执行固定时间步长更新
            while (accumulator >= UPDATE_RATE) {
                accumulator -= UPDATE_RATE;
                // calculation
                System.out.println(windowHeight);
            }

            // 渲染(可以传入插值因子用于平滑渲染)
            drawAll();

            // 交换缓冲区
            glfwSwapBuffers(window);
            // 轮询事件
            glfwPollEvents();
        }
    }

    private void loadImage(String path) {
        try (MemoryStack stack = stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer comp = stack.mallocInt(1);

            // 加载图片
            ByteBuffer imageBuffer = stbi_load(path, w, h, comp, 4);
            if (imageBuffer == null) {
                System.err.println("Failed to load image: " + stbi_failure_reason());
                return;
            }

            imageWidth = w.get(0);
            imageHeight = h.get(0);

            // 根据图片尺寸设置网格
            gridWidth = imageWidth;
            gridHeight = imageHeight;

            // 创建纹理
            textureID = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, textureID);

            // 设置纹理参数
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

            // 上传纹理数据
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, imageWidth, imageHeight,
                    0, GL_RGBA, GL_UNSIGNED_BYTE, imageBuffer);

            // 释放图像内存
            stbi_image_free(imageBuffer);

            imageLoaded = true;
        }
    }


    // 绘制图片
    private void drawImage() {
        // 计算坐标系在窗口中的位置和大小
        float scaledGridWidth = gridWidth * scale;
        float scaledGridHeight = gridHeight * scale;

        // 计算坐标系在窗口中的位置
        float gridX, gridY;

        if (windowWidth > scaledGridWidth && windowHeight > scaledGridHeight) {
            // 窗口大于坐标系，将坐标系居中
            gridX = (windowWidth - scaledGridWidth) / 2 + offsetX;
            gridY = (windowHeight - scaledGridHeight) / 2 + offsetY;
        } else {
            // 窗口小于坐标系，以左上角为基准
            gridX = offsetX;
            gridY = offsetY;
        }

        // 启用纹理和混合
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // 绑定纹理
        glBindTexture(GL_TEXTURE_2D, textureID);

        // 保存当前变换矩阵
        glPushMatrix();

        // 应用平移和缩放
        glTranslatef(gridX, gridY, 0);
        glScalef(scale, scale, 1);

        // 绘制图片（在网格下方）
        glBegin(GL_QUADS);
        glTexCoord2f(0, 0); glVertex2f(0, gridHeight);
        glTexCoord2f(1, 0); glVertex2f(gridWidth, gridHeight);
        glTexCoord2f(1, 1); glVertex2f(gridWidth, 0);
        glTexCoord2f(0, 1); glVertex2f(0, 0);
        glEnd();

        // 恢复变换矩阵
        glPopMatrix();

        // 禁用纹理
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_BLEND);
    }

    private void drawAll() {
        // 计算图片显示尺寸（保持宽高比）
        float imageAspect = (float)imageWidth / imageHeight;
        float displayWidth, displayHeight;

        if (windowWidth / windowHeight > imageAspect) {
            displayHeight = windowHeight * scale;
            displayWidth = displayHeight * imageAspect;
        } else {
            displayWidth = windowWidth * scale;
            displayHeight = displayWidth / imageAspect;
        }

        // 计算绘制位置（居中+偏移）
        float posX = (windowWidth - displayWidth) / 2 + offsetX;
        float posY = (windowHeight - displayHeight) / 2 + offsetY;

        // 保存当前变换矩阵
        glPushMatrix();

        // 应用整体变换（位置+缩放）
        glTranslatef(posX, posY, 0);
        glScalef(displayWidth / gridWidth, displayHeight / gridHeight, 1);

        // 1. 绘制图片（在最底层）
        if (imageLoaded) {
            glEnable(GL_TEXTURE_2D);
            glBindTexture(GL_TEXTURE_2D, textureID);

            glBegin(GL_QUADS);
            glTexCoord2f(0, 0); glVertex2f(0, gridHeight);
            glTexCoord2f(1, 0); glVertex2f(gridWidth, gridHeight);
            glTexCoord2f(1, 1); glVertex2f(gridWidth, 0);
            glTexCoord2f(0, 1); glVertex2f(0, 0);
            glEnd();

            glDisable(GL_TEXTURE_2D);
        }

//         2. 绘制网格（在中间层）
        if(GridMode){
            drawGrid();
        }

        // 3. 绘制点和线（在最上层）
        drawPointsAndLines();

        // 恢复变换矩阵
        glPopMatrix();
    }

    private void drawPointsAndLines() {

        // 绘制线条（保持不变）
        if (!lines.isEmpty()) {
            glColor4f(LINE_COLOR[0], LINE_COLOR[1], LINE_COLOR[2], LINE_COLOR[3]);
            glBegin(GL_LINES);
            for (Line line : lines) {
                glVertex2f(line.x1, line.y1);
                glVertex2f(line.x2, line.y2);
            }
            glEnd(); }

        // 绘制反色圆圈
        if (!seedPoints.isEmpty()) {
            // 1. 设置反色混合模式
            glEnable(GL_BLEND);
            glBlendFunc(GL_ONE_MINUS_DST_COLOR, GL_ZERO); // 反色混合公式

            // 2. 绘制圆圈
            float radius = gridWidth * scaleCircle / (scale * 30.0f);
            glColor3f(1, 1, 1); // 必须设置为白色
            for (Point point : seedPoints) {
                glBegin(GL_LINE_LOOP);
                for (int i = 0; i < 32; i++) {
                    double angle = 2.0 * Math.PI * i / 32;
                    glVertex2f(
                            point.x + (float)(radius * Math.cos(angle)),
                            point.y + (float)(radius * Math.sin(angle))
                    );
                }
                glEnd();
            }

            // 3. 恢复默认混合
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        }
    }

    private void drawGrid() {
        glColor4f(GRID_COLOR[0], GRID_COLOR[1], GRID_COLOR[2], GRID_COLOR[3]);
        glBegin(GL_LINES);

        // 垂直线（每5个主网格一条线）
        for (int x = 0; x <= gridWidth; x += 5) {
            glVertex2f(x, 0);
            glVertex2f(x, gridHeight);
        }

        // 水平线（每5个主网格一条线）
        for (int y = 0; y <= gridHeight; y += 5) {
            glVertex2f(0, y);
            glVertex2f(gridWidth, y);
        }

        // 绘制坐标轴
        glColor4f(AXIS_COLOR[0], AXIS_COLOR[1], AXIS_COLOR[2], AXIS_COLOR[3]);
        glLineWidth(2);
        // X轴
        glVertex2f(0, gridHeight/2);
        glVertex2f(gridWidth, gridHeight/2);
        // Y轴
        glVertex2f(gridWidth/2, 0);
        glVertex2f(gridWidth/2, gridHeight);
        glEnd();
        glLineWidth(1);
    }

    // 更新路径线条，便于draw
    private void renewLine(){
        this.lines.clear();
        for(int i =0; i< points.size() - 1; i++){
            Point p1 = points.get(i);
            Point p2 = points.get(i+1);
            this.lines.add(new Line(p1.x, p1.y, p2.x, p2.y));
        }
    }
    private void displayScale() {
        // 在窗口左下角显示缩放比例
        String scaleText = String.format("Scale: %.2f", scale);

        // 使用STB EasyFont渲染文本
        ByteBuffer charBuffer = MemoryUtil.memAlloc(scaleText.length() * 270);
        int quads = stb_easy_font_print(0, 0, scaleText, null, charBuffer);

        // 设置正交投影
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, windowWidth, windowHeight, 0, -1, 1);

        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        glTranslatef(10, windowHeight - 20, 0);

        // 渲染文本
        glUseProgram(fontShaderProgram);
        int colorLoc = glGetUniformLocation(fontShaderProgram, "color");
        glUniform4f(colorLoc, TEXT_COLOR[0], TEXT_COLOR[1], TEXT_COLOR[2], TEXT_COLOR[3]);

        glBindVertexArray(fontVAO);
        glBindBuffer(GL_ARRAY_BUFFER, fontVBO);
        glBufferData(GL_ARRAY_BUFFER, charBuffer, GL_STATIC_DRAW);
        glScalef(15f, 15f, 1);  // 放大1.5倍

        glDrawArrays(GL_QUADS, 0, quads * 4);

//        // 步骤1：确认着色器工作
//        glUseProgram(fontShaderProgram);
//        glUniform4f(/* ... */, 1,0,0,1); // 使用醒目红色

// 步骤2：直接绘制矩形（绕过字体渲染）
        glDrawArrays(GL_TRIANGLES, 0, 3);

// 步骤3：检查OpenGL错误
        int err = glGetError();
        if (err != GL_NO_ERROR) {
            System.err.println("OpenGL Error: " + err);
        }

        // 恢复状态
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

    // 回调函数
    // 修改键盘回调以支持L键加载图片
    private void keyCallback(long window, int key, int scancode, int action, int mods) {
        if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
            glfwSetWindowShouldClose(window, true);
        }

        // 检测Ctrl键状态
        if (key == GLFW_KEY_LEFT_CONTROL || key == GLFW_KEY_RIGHT_CONTROL) {
            ctrlPressed = action != GLFW_RELEASE;
        }

        // 加载图片
        if (key == GLFW_KEY_L && action == GLFW_PRESS) {
//            loadImage("src/example.png");
            loadImage("src/example.jpg");

        }

        // H键切换模式
        if (key == GLFW_KEY_H && action == GLFW_PRESS) {
            handMode = !handMode;

            // 确保光标已初始化
            if (!cursorsInitialized) {
                initCursors();
            }

            // 设置对应光标
            glfwSetCursor(window, handMode ? handCursor : arrowCursor);

            System.out.println("Hand mode " + (handMode ? "enabled" : "disabled"));
        }
        // 按下G添加网格
        if (key == GLFW_KEY_G && action == GLFW_PRESS) {
            GridMode = !GridMode;

            System.out.println("Grid mode " + (GridMode ? "enabled" : "disabled"));
        }
    }

    private void cursorPosCallback(long window, double xpos, double ypos) {
        mouseX = xpos;
        mouseY = ypos;

        if (handMode && isDragging) {
            // 计算鼠标移动增量
            double dx = xpos - lastMouseX;
            double dy = ypos - lastMouseY;

            // 更新偏移量
            offsetX += dx;
            offsetY += dy;
            System.out.println(scale);
            float effectiveWidth = imageWidth * scale * (float)1.1;
            float effectiveHeight = imageHeight * scale * (float)1.2;
            offsetX = Math.max(-effectiveWidth, Math.min(effectiveWidth, offsetX));
            offsetY = Math.max(-effectiveHeight, Math.min(effectiveHeight, offsetY));
            // 限制偏移范围（可选）
//            offsetX = Math.max(-windowWidth, Math.min(windowWidth, offsetX));
//            offsetY = Math.max(-windowHeight, Math.min(windowHeight, offsetY));

            // 更新最后位置
            lastMouseX = xpos;
            lastMouseY = ypos;
        }
    }

    private void mouseButtonCallback(long window, int button, int action, int mods) {
        if(button == GLFW_MOUSE_BUTTON_LEFT){
            if(handMode){
                if (action == GLFW_PRESS) {
                    isDragging = true;
                    lastMouseX = mouseX;
                    lastMouseY = mouseY;
                } else if (action == GLFW_RELEASE) {
                    isDragging = false;
                }
            }else{
                if (action == GLFW_PRESS) {
                    // 实现标点
                    Point p = getPosition();
                    // 检查是否在坐标系范围内
                    if (p!=null) {
                        // 添加最近的网格点
                        seedPoints.add(p);
                        System.out.println(p.x + "N" + p.y);
                        // 如果有多个点，考虑闭合
                        if (points.size() > 1) {
                            // 检查是否可以闭合
                            if (points.size() > 2 && !isClosed) {
                                Point firstPoint = points.get(0);
                                float distance = (float) Math.sqrt(
                                        pow(p.x - firstPoint.x, 2) +
                                                pow(p.y - firstPoint.y, 2)
                                );

                                // 闭合阈值设为网格步长的1.5倍
                                if (distance < gridStep * 1.5f) {
                                    lines.add(new Line(p.x, p.y, firstPoint.x, firstPoint.y));
                                    isClosed = true;
                                }
                            }
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
                    int distance = (int)Math.sqrt(pow(prePoint.x - p.x,2)+pow(prePoint.y - p.y,2));
                    float optDist = distance / scale; // 考虑缩放时点击的准确性会下降
                    System.out.println("距离最近seedpoint：" + optDist);
                    if(optDist< 10){
                        seedPoints.removeLast();
                    }
                }
            }
        }
    }

    private void scrollCallback(long window, double xoffset, double yoffset) {
        if (ctrlPressed) {
            float oldScale = scale;

            // 计算新的缩放比例
            scale *= (1.0f + (float)yoffset * 0.1f);
            scale = Math.max(0.1f, Math.min(scale, 10.0f));

            // 计算鼠标在归一化坐标系中的位置
            float[] gridPos = screenToGridCoordinates((float)mouseX, (float)mouseY);
            float mouseGridX = gridPos[0] / gridWidth;
            float mouseGridY = gridPos[1] / gridHeight;

            // 调整偏移量以实现以鼠标为中心的缩放
            offsetX += (mouseGridX - 0.5f) * gridWidth * (1 - scale/oldScale) * (windowWidth/(float)gridWidth);
            offsetY += (mouseGridY - 0.5f) * gridHeight * (1 - scale/oldScale) * (windowHeight/(float)gridHeight);
        }else{
            // 直接调整滚轮能手动调节点的大小
            float oldScale = scaleCircle;

            // 计算新的缩放比例
            scaleCircle *= (1.0f + (float)yoffset * 0.1f);
            scaleCircle = Math.max(0.1f, Math.min(scaleCircle, 10.0f));
        }
    }

    // 将屏幕坐标转换为网格坐标
    private float[] screenToGridCoordinates(float screenX, float screenY) {
        // 计算图片显示尺寸和位置（与drawAll中相同）
        float imageAspect = (float)imageWidth / imageHeight;
        float displayWidth, displayHeight;

        if (windowWidth / windowHeight > imageAspect) {
            displayHeight = windowHeight * scale;
            displayWidth = displayHeight * imageAspect;
        } else {
            displayWidth = windowWidth * scale;
            displayHeight = displayWidth / imageAspect;
        }

        float posX = (windowWidth - displayWidth) / 2 + offsetX;
        float posY = (windowHeight - displayHeight) / 2 + offsetY;

        // 转换为网格坐标
        float gridX = (screenX - posX) * gridWidth / displayWidth;
        float gridY = (screenY - posY) * gridHeight / displayHeight;

        return new float[]{
                Math.max(0, Math.min(gridWidth, gridX)),
                Math.max(0, Math.min(gridHeight, gridY))
        };
    }

    private void cleanup() {
        if (cursorsInitialized) {
            glfwDestroyCursor(handCursor);
            glfwDestroyCursor(arrowCursor);
        }
        // 其他清理代码...
        glDeleteBuffers(fontVBO);
        glDeleteVertexArrays(fontVAO);
        glDeleteProgram(fontShaderProgram);
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        Objects.requireNonNull(glfwSetErrorCallback(null)).free();
    }
    // 内部类表示点和线
    public static class Point {
        float x, y;

        Point(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    private static class Line {
        float x1, y1, x2, y2;

        Line(float x1, float y1, float x2, float y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }
    }


    // 性能测试
    long lastPointTime;
    int POINTS_PER_SEC = 5;
    private void renderFrame(boolean collectStats) {
        // 1. 添加随机点
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastPointTime > 1000 / POINTS_PER_SEC) {
            addRandomPoint();
            lastPointTime = currentTime;
        }

    }
    private void addRandomPoint() {
        float x = (float) (Math.random() * imageWidth);
        float y = (float) (Math.random() * imageHeight);
        points.add(new Point(x, y));

    }

    // 获取鼠标在坐标轴上的位置
    public Point getPosition(){
        float[] gridPos = screenToGridCoordinates((float)mouseX, (float)mouseY);
        float mouseGridX = gridPos[0];
        float mouseGridY = gridPos[1];
        System.out.println(mouseGridX + " " + mouseGridY);
        // 检查是否在坐标系范围内
        if (mouseGridX >= 0 && mouseGridX <= gridWidth && mouseGridY >= 0 && mouseGridY <= gridHeight) {
            // （假设网格线间距为1个单位）

            // 计算最近的网格点X坐标
            float nearestX = Math.round(mouseGridX / gridStep) * gridStep;
            nearestX = Math.max(0, Math.min(nearestX, gridWidth));

            // 计算最近的网格点Y坐标
            float nearestY = Math.round(mouseGridY / gridStep) * gridStep;
            nearestY = Math.max(0, Math.min(nearestY, gridHeight));

            return new Point(nearestX, nearestY);
        }
        return null;
    }

    // 更新绘制点与线
    public void renewPoints(List<Point> points){
        this.points = points;
    }


}