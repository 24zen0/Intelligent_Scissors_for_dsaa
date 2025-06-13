package project1;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.DataBufferInt;
import java.io.File;
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
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class CoordinateGrid2_3 implements Runnable {

    /** 冷却比例 (0 < α < 1) */
    private static final double COOLING_RATE   = 0.1;

    /** 当像素代价 < 此阈值时，生成新种子 */
    private static final double COST_THRESHOLD = 0.85;

    private static final int    TIME_THRESHOLD = 200;

    // 状态变量
    private boolean pathCoolingMode= false;
    private boolean snapMode = false;
    private boolean handMode = false; // ⼿形拖动模式标志
    private boolean isDragging = false; // 是否正在拖动
    private boolean GridMode = false; // 是否显示⽹格
    private boolean shouldOpenImageWindow = false;
    private boolean imageWindowOpen = false;
    // 窗⼝和坐标系相关变量
    private long window; // GLFW窗⼝句柄
    private static long imageWindow = NULL; // 第二窗口
    private int windowWidth, windowHeight; // 第一窗⼝宽⾼
    private final int windowWidth2=600;
    private final int windowHeight2=600; // 第一窗⼝宽⾼

    private final int gridStep = 1; // ⽹格步⻓
    // ⽹格尺⼨(会根据加载的图⽚⾃动调整)
    private int gridWidth = 10; // 默认⽹格宽度
    private int gridHeight = 10; // ⽹格⾼度
    // 视图变换参数
    private float scale = 1.0f; // 缩放⽐例
    private float offsetX = 0.0f, offsetY = 0.0f; // 偏移量
    // 图⽚相关变量
    BufferedImage originalImage;
    private int textureID = -1; // 纹理ID
    private int imageWidth = 0; // 图⽚宽度
    private int imageHeight = 0; // 图⽚⾼度
    private boolean imageLoaded = false; // 图⽚是否已加载
    private int maskTextureID = -1; // 第二窗口
    private int finalTexture = -1;
    // ⿏标交互相关
    private double currentMouseX, currentMouseY; // 当前⿏标位置
    private double lastMouseX, lastMouseY; // 上次⿏标位置
    private boolean ctrlPressed = false; // Ctrl键是否按下
    private static final float DRAG_SENSITIVITY = 1.0f; // 拖动灵敏度
    // 绘制的⼏何元素
    private List<Point> pointsList = new ArrayList<>(); // 点集合
    private List<Point> endList = new ArrayList<>(); // 结束闭合时的点集合
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
    private static final float[] LINE_COLOR = {1.0f, 0.0f, 1.0f, 1.0f}; // 线颜⾊
    private static final float[] TEXT_COLOR = {0.0f, 0.0f, 0.0f, 1.0f}; // ⽂本颜⾊
    // 光标相关
    private long handCursor = 0; // ⼿形光标
    private long arrowCursor = 0; // 箭头光标
    private boolean cursorsInitialized = false; // 光标是否已初始化
    // 时间控制
    private final double UPDATE_RATE = 1.0 / 30.0; // 更新频率(30FPS)
    private final double MAX_FRAME_TIME = 0.25; // 最⼤帧时间
    //路径闭合
    private boolean isPathClosed = false;
    private int closedPathIndex = -1;
    // — Path Cooling 状态追踪 —
    /** 每个像素在活线中连续出现的帧计数 */
    private Map<Point,Integer> stabilityTime     = new HashMap<>();
    /** 每个像素在活线中连续共线（前缀一致）的帧计数 */
    private Map<Point,Integer> coalescenceCount  = new HashMap<>();
    /** 保存上一帧的活线路径，用于判断共线 */
    private List<Point>        previousPoints    = new ArrayList<>();

    static List<Point> latestPath;
//    static List<Point> latestPath1;
//    static List<Point> latestPath2;
//    static List<Point> latestPath3;
//    static List<Point> latestPath4;

    public static void main(String[] args) {
        System.setProperty("jdk.attach.allowAttachSelf", "true");
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
                    //Point end = getPosition();
                    Point end = new Point((int) getPosition().x, (int) getPosition().y);

                    if (snapMode) {
                        end = snap((int) getPosition().x, (int) getPosition().y, 20);
                    } else {
                        end = new Point((int) getPosition().x, (int) getPosition().y);
                    }


                    pointsList = AStar.Node.convertNodesToPoints(
                            AStar.findPath((int) start.x, (int) start.y, (int) end.x, (int) end.y, ImageProcess.costMatrix)
                    );
                } else {
                    pointsList = Collections.emptyList(); // Handle empty case
                }
                addNewPointsPreview(pointsList);
                // 4.1 更新稳定性（连续出现）和共线计数（前缀一致）
                for (Point p : pointsList) {
                    // 连续出现在活线中的帧数 +1
                    stabilityTime.put(p, stabilityTime.getOrDefault(p, 0) + 1);

                    // 如果上一帧活线也包含此点，则前缀共线 +1
                    if (previousPoints.contains(p)) {
                        coalescenceCount.put(p, coalescenceCount.getOrDefault(p, 0) + 1);
                    }
                }
                // 保存本帧路径，供下一次共线判断
                previousPoints = new ArrayList<>(pointsList);

                // 4.2 调用改造后的冷却检测（将替换原来的 processPathCooling()）
                if (!handMode) {
                    if (pathCoolingMode) {
                        processPathCooling(); // Check and save stable paths
                    }
                }
            }
// 渲染场景
            drawAll();

            // 检测是否应该开启第二窗口
            if (shouldOpenImageWindow && !imageWindowOpen) {
                openImageWindow();
                shouldOpenImageWindow = false;
                imageWindowOpen = true;
            }
            // 第二窗口的渲染
            if (imageWindow != NULL) {

                System.out.println(finalTexture);
                glfwMakeContextCurrent(imageWindow);
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

                renderImageWindow();
                glfwSwapBuffers(imageWindow);

                // 检查图片窗口是否关闭
                if (glfwWindowShouldClose(imageWindow)) {
                    glfwDestroyWindow(imageWindow);
                    imageWindow = NULL;
                    imageWindowOpen = false;
                }
            }


// 交换缓冲区并处理事件
            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }
    private void openImageWindow() {
        // 配置图片窗口
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);

        // 创建图片窗口（共享主窗口的上下文）
        imageWindow = glfwCreateWindow(windowWidth2, windowHeight2, "图片展示窗口", NULL, window);
        if (imageWindow == NULL) {
            throw new RuntimeException("Failed to create the image window");
        }

        // 设置窗口位置（主窗口右侧）
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            IntBuffer pX = stack.mallocInt(1);
            IntBuffer pY = stack.mallocInt(1);

            glfwGetWindowSize(window, pWidth, pHeight);
            glfwGetWindowPos(window, pX, pY);

            glfwSetWindowPos(
                    imageWindow,
                    pX.get(0) + pWidth.get(0) + 10,
                    pY.get(0)
            );
        }

        // 初始化图片窗口的OpenGL上下文
        glfwMakeContextCurrent(imageWindow);
        GL.createCapabilities();
        glClearColor(0.9f, 0.9f, 0.9f, 1.0f);

        // 加载图片纹理
        createAndApplyMask(originalImage);
        // 显示图片窗口
        glfwShowWindow(imageWindow);
    }

    public void createAndApplyMask(BufferedImage originalImage) {
        System.out.println("inter createAndApplyMask");
        // 1. 创建原始图像纹理
        textureID = createTexture(originalImage, false);

        // 2. 创建掩膜纹理（注意二值图像的特殊处理）
        ArrayList<Point> closePath = new ArrayList<>();
        for(int i=0; i<pointListListSave.size(); i++) {
            closePath.addAll(pointListListSave.get(i));
        }
        BufferedImage mask = convertToMaskWithFill(closePath, imageWidth, imageHeight);

        BufferedImage finalImage = cropWithMask(originalImage, mask);

//        maskTextureID = createTexture(mask, true);
        maskTextureID = createTexture(mask, false);
        // 3. 应用掩膜生成最终纹理
        finalTexture = createRGBATexture(finalImage);
        // 测试，为验证finaltexture是否正确传入，先将其赋值成其他

    }

    // 创建OpenGL纹理
    private int createTexture(BufferedImage image, boolean isBinaryMask) {
        int[] pixels = new int[image.getWidth() * image.getHeight()];
        image.getRGB(0, 0, image.getWidth(), image.getHeight(), pixels, 0, image.getWidth());

        ByteBuffer buffer = BufferUtils.createByteBuffer(image.getWidth() * image.getHeight() * 4);

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = pixels[y * image.getWidth() + x];
                buffer.put((byte) ((pixel >> 16) & 0xFF)); // R
                buffer.put((byte) ((pixel >> 8) & 0xFF));  // G
                buffer.put((byte) (pixel & 0xFF));         // B

                // 二值掩膜特殊处理：将亮度转换为alpha值
                if(isBinaryMask) {
                    int r = (pixel >> 16) & 0xFF;
                    int g = (pixel >> 8) & 0xFF;
                    int b = pixel & 0xFF;
                    int alpha = (r + g + b) / 3; // 计算灰度值作为alpha
                    buffer.put((byte) alpha);    // A
                } else {
                    buffer.put((byte) ((pixel >> 24) & 0xFF)); // 普通图像的原始alpha
                }
            }
        }
        buffer.flip();

        int textureID = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureID);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, image.getWidth(), image.getHeight(),
                0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);

        return textureID;
    }

    // 渲染纹理到指定位置
    private void renderTexture(int textureID, float x, float y, float width, float height) {
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, textureID);
        glBegin(GL_QUADS);
        glTexCoord2f(0, 1); glVertex2f(x, y +height);
        glTexCoord2f(1, 1); glVertex2f(x + width, y +height);
        glTexCoord2f(1, 0); glVertex2f(x +width, y);
        glTexCoord2f(0, 0); glVertex2f(x, y);
        glEnd();
    }
    /**
     * 根据二值掩膜裁剪图像
     * @param originalImage 原始图像
     * @param mask 二值掩膜(BufferedImage.TYPE_BYTE_BINARY)
     * @return 裁剪后的图像(保留掩膜白色区域)
     */
    public static BufferedImage cropWithMask(BufferedImage originalImage, BufferedImage mask) {
        // 1. 获取掩膜的非空区域边界
        Rectangle bounds = findMaskBounds(mask);
        if (bounds.width <= 0 || bounds.height <= 0) {
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB); // 返回1x1透明图像
        }

        // 2. 创建结果图像（带透明度）
        BufferedImage result = new BufferedImage(
                bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB);

        // 3. 提取掩膜的对应区域（优化：直接处理像素，避免getSubimage拷贝）
        int[] maskPixels = new int[bounds.width * bounds.height];
        mask.getRGB(bounds.x, bounds.y, bounds.width, bounds.height,
                maskPixels, 0, bounds.width);

        // 4. 提取并处理原始图像区域
        int[] originalPixels = new int[bounds.width * bounds.height];
        originalImage.getRGB(bounds.x, bounds.y, bounds.width, bounds.height,
                originalPixels, 0, bounds.width);

        // 5. 应用掩膜（黑色区域变透明）
        for (int i = 0; i < originalPixels.length; i++) {
            // 掩膜黑色像素（RGB全0）则设置完全透明
            if ((maskPixels[i] & 0x00FFFFFF) == 0) {
                originalPixels[i] = 255; // 保留RGB，Alpha=0
                System.out.print("processiing");
            }
        }

        // 6. 设置结果像素
        result.setRGB(0, 0, bounds.width, bounds.height, originalPixels, 0, bounds.width);
        return result;
    }
    /**
     * 找到掩膜中白色区域的边界矩形
     */
    private static Rectangle findMaskBounds(BufferedImage mask) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (int y = 0; y < mask.getHeight(); y++) {
            for (int x = 0; x < mask.getWidth(); x++) {
                if ((mask.getRGB(x, y) & 0xFFFFFF) != 0) { // 非黑色像素
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }
    /**
     * 高质量图像缩放
     * @param image 原始图像
     * @param width 目标宽度
     * @param height 目标高度
     * @param highQuality 是否使用高质量缩放
     */
    public static BufferedImage resizeImage(BufferedImage image,
                                            int width, int height,
                                            boolean highQuality) {
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = scaled.createGraphics();

        if (highQuality) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
        }

        g.drawImage(image, 0, 0, width, height, null);
        g.dispose();

        return scaled;
    }



    private void renderImageWindow() {
        // 获取窗口的帧缓冲尺寸（兼容Retina等高DPI屏幕）
        int[] winWidth = new int[1];
        int[] winHeight = new int[1];
        glfwGetFramebufferSize(imageWindow, winWidth, winHeight);
        int fbWidth = winWidth[0];
        int fbHeight = winHeight[0];

        // 设置视口（覆盖整个窗口）
        glViewport(0, 0, fbWidth, fbHeight);

        // 清屏并填充黑边颜色（例如纯黑）
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);

        // 计算图片原始宽高比
        float imageAspect = (float)imageWidth / imageHeight;
        float windowAspect = (float)fbWidth / fbHeight;

        float renderWidth, renderHeight;
        float offsetX, offsetY;

        if (imageAspect > windowAspect) {
            // 图片比窗口更宽 → 以宽度为基准缩放
            renderWidth = fbWidth/ imageAspect;
            renderHeight = fbWidth / imageAspect;
            offsetX = 0;
            offsetY = (fbHeight - renderHeight) / 2;
        } else {
            // 图片比窗口更高 → 以高度为基准缩放
            renderHeight = fbHeight* imageAspect;
            renderWidth = fbHeight * imageAspect;
            offsetX = (fbWidth - renderWidth) / 2;
            offsetY = 0;
        }

        // 渲染纹理（确保纹理坐标正确）
        renderTexture(finalTexture, offsetX, offsetY, renderWidth, renderHeight);
    }
//    private void renderImageWindow() {
//        try (MemoryStack stack = stackPush()) {
//            IntBuffer width = stack.mallocInt(1);
//            IntBuffer height = stack.mallocInt(1);
//            glfwGetWindowSize(imageWindow, width, height);
//
//            int[] winWidth = new int[1];
//            int[] winHeight = new int[1];
//            glfwGetFramebufferSize(imageWindow, winWidth, winHeight);
//
//            // 计算缩放比例和偏移
//            float imageAspect = (float)imageWidth / imageHeight;
//            float windowAspect = (float)winWidth[0] / winHeight[0];
//
//            float scale;
//            float renderWidth, renderHeight;
//            float offsetX = 0, offsetY = 0;
//
//            if (imageAspect > windowAspect) {
//                // 图片比窗口更宽 → 以宽度为基准缩放
//                scale = (float)winWidth[0] / imageWidth;
//                renderWidth = winWidth[0];
//                renderHeight = imageHeight * scale;
//                offsetY = (winHeight[0] - renderHeight) / 2; // 垂直居中
//            } else {
//                // 图片比窗口更高 → 以高度为基准缩放
//                scale = (float)winHeight[0] / imageHeight;
//                renderHeight = winHeight[0];
//                renderWidth = imageWidth * scale;
//                offsetX = (winWidth[0] - renderWidth) / 2; // 水平居中
//            }
//
//            // 渲染纹理（带偏移）
//            renderTexture(finalTexture, offsetX, offsetY, renderWidth, renderHeight);
////            // 计算缩放和居中
////            float imageAspect = (float)imageWidth / imageHeight;
////            float windowAspect = (float)windowWidth2 / windowHeight2;
////
////            if (imageAspect > windowAspect) {
////                // 以宽度为基准缩放
////                float scale = (float) windowWidth2 / imageWidth;
////                float h = imageHeight * scale;
////                float w = imageWidth * scale;
////                float yOffset = (windowHeight2 - h) / 2;
////                renderTexture(finalTexture, 0, 0, w, h);
////            } else {
////                // 以高度为基准缩放
////                float scale = (float) windowHeight2 / imageHeight;
////                float w = imageWidth * scale;
////                float h = imageHeight * scale;
////                float xOffset = (windowWidth2 - w) / 2;
////                renderTexture(finalTexture, 0, 0, w, h);
////            }
//////            renderTexture(finalTexture, 0, 0, width.get(0), height.get(0));
//        }
//    }
    /**
     * 渲染时动态调整纹理大小
     * @param textureID 纹理ID
     * @param x 渲染位置X
     * @param y 渲染位置Y
     * @param width 渲染宽度
     * @param height 渲染高度
     */
//    public static void renderTextureScaled(int textureID,
//                                           float x, float y,
//                                           float width, float height) {
//        glEnable(GL_TEXTURE_2D);
//        glBindTexture(GL_TEXTURE_2D, textureID);
//
//        glBegin(GL_QUADS);
//        glTexCoord2f(0, 0); glVertex2f(x, y);
//        glTexCoord2f(1, 0); glVertex2f(x + width, y);
//        glTexCoord2f(1, 1); glVertex2f(x + width, y + height);
//        glTexCoord2f(0, 1); glVertex2f(x, y + height);
//        glEnd();
//    }

    /**
     * 通用RGBA纹理生成方法（严格保持原始像素数据）
     * @param image 源图像（必须包含Alpha通道，建议使用BufferedImage.TYPE_INT_ARGB）
     * @return 生成的OpenGL纹理ID
     */
    private int createRGBATexture(BufferedImage image) {
        // 验证图像类型
        if (image.getType() != BufferedImage.TYPE_INT_ARGB &&
                image.getType() != BufferedImage.TYPE_4BYTE_ABGR) {
            throw new IllegalArgumentException("输入图像必须包含Alpha通道");
        }

        // 准备像素缓冲区
        ByteBuffer buffer = BufferUtils.createByteBuffer(image.getWidth() * image.getHeight() * 4);

        // 直接获取底层数据（比getRGB更快）
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();

        // 按原始顺序填充RGBA数据
        for (int pixel : pixels) {
            buffer.put((byte) ((pixel >> 16) & 0xFF)); // R
            buffer.put((byte) ((pixel >> 8)  & 0xFF)); // G
            buffer.put((byte) (pixel        & 0xFF)); // B
            buffer.put((byte) ((pixel >> 24) & 0xFF)); // A
        }
        buffer.flip();

        // 创建并配置纹理
        int textureID = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureID);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

        // 上传纹理数据（使用GL_RGBA格式）
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, image.getWidth(), image.getHeight(),
                0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);

        return textureID;
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
            originalImage = ImageIO.read(new File(path));
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
//            latestPath1 = pointListListPreview.get(pointListListPreview.size() - 2); // 获取最新路径
//            latestPath2 = pointListListPreview.get(pointListListPreview.size() - 3); // 获取最新路径
//            latestPath3 = pointListListPreview.get(pointListListPreview.size() - 4); // 获取最新路径
//            latestPath4 = pointListListPreview.get(pointListListPreview.size() - 5); // 获取最新路径


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
            float radius = gridWidth * scaleCircle / (scale * 50.0f);
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
            glColor4f(1.0f,1.0f,1.0f,1.0f);
        }

    }

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
            loadImage("src/frog.png");
        }
        if (key == GLFW_KEY_R && action == GLFW_PRESS) {
            loadImage("src/thanos.png");
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
        if(key == GLFW_KEY_ENTER && action == GLFW_PRESS) {
            int size = 0;
            for(int i =0;i<pointListListSave.size();i++){
                size += pointListListSave.get(i).size();
            }
            System.out.println("验证是否可以闭合");
            if(size>=2){
                if(isClosed){
                    shouldOpenImageWindow = true;

                }else{
// 第⼀次，未闭合，将其闭合
//添加计算路径返回point List的代码
                    endList = AStar.Node.convertNodesToPoints(
                            AStar.findPath((int)seedPoints.getLast().x, (int)seedPoints.getLast().y, (int)seedPoints.getFirst().x, (int)seedPoints.getFirst().y, ImageProcess.costMatrix)
                    );
                    System.out.println("计算合并路径完毕");
                    // 打扫干净latestPath
                    latestPath.clear();
                    latestPath.addAll(endList);

                    pointListListSave.add(latestPath);
                    isClosed = true;


                    // 清空预览相关列表，避免重复
                    pointListListPreview.clear();
                    linesPreview.clear();
                    //出图
                    shouldOpenImageWindow = true;
                    System.out.println(shouldOpenImageWindow);
                }
            }
        }
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
            snapMode = !snapMode;
        }
        if (key == GLFW_KEY_C && action == GLFW_PRESS) {
            pathCoolingMode = !pathCoolingMode;
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
                    //Point p = getPosition();
                    Point rawPoint = new Point(currentMouseX, currentMouseY);
                    if(snapMode){
                        rawPoint = snap((int) getPosition().x, (int) getPosition().y,20);  // 获取原始网格点
                    }else{
                        rawPoint = getPosition();
                    }
// 检查是否在坐标系范围内
                    if (rawPoint!=null) {
// 添加最近的⽹格点
                        seedPoints.add(rawPoint);
//此处在测试renewLine()
// renewLine();
                        System.out.println(rawPoint.x + "N" + rawPoint.y);

                        double cost = ImageProcess.getCostMatrix((int) getCurrentMousePos().x, (int) getCurrentMousePos().y);  // 正常位置
                        System.out.println("Cost: " + cost);
// 如果有多个点，考虑闭合
// 检查是否可以闭合
                        if (seedPoints.size() > 2 && !isClosed) {
                            Point firstPoint = seedPoints.get(0);
                            float distance = (float) Math.sqrt(
                                    pow(rawPoint.x - firstPoint.x, 2) +
                                            pow(rawPoint.y - firstPoint.y, 2)
                            );
                            // 动态显示闭合距离
                            System.out.println("当前闭合距离：" + distance + "/" + gridStep*3f);
// 闭合阈值设为⽹格步⻓的3倍
//                            if (distance < gridStep * 1.5f) {
//                                linesPreview.add(new Line((float) p.x, (float) p.y, (float)
//                                        firstPoint.x, (float) firstPoint.y));
//                                isClosed = true;
//                            }
                            //新的方法来实现闭合：
                            if (distance < gridStep * 3f) {
                                // 闭合路径，确保 latestPath 包含闭合线段
                                latestPath.add(seedPoints.get(0)); // 将起点加入路径末尾

                                isClosed = true;
                                // 清空预览相关列表，避免重复
                                pointListListPreview.clear();
                                linesPreview.clear();
                                //出图
                                shouldOpenImageWindow = true;
                                System.out.println(shouldOpenImageWindow);
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
//                    Point prePoint = seedPoints.getLast();
//                    int distance = (int)Math.sqrt(pow(prePoint.x - p.x,2)+pow(prePoint.y
//                            - p.y,2));
//                    float optDist = distance / scale; // 考虑缩放时点击的准确性会下降
//                    System.out.println("距离最近seedpoint：" + optDist);
//                    if(optDist< 10){
//                        seedPoints.removeLast();
//                    }
                    // 现在改成一旦右键就撤回
                    seedPoints.removeLast();
                    // 同步删除对应的保存路径
                    if (!pointListListSave.isEmpty() && !segmentLineCounts.isEmpty()) {
                        // 获取最后添加的线段数量
                        int lastSegmentLines = segmentLineCounts.remove(segmentLineCounts.size() - 1);
                        pointListListSave.remove(pointListListSave.size() - 1);

                        // 从linesSave中移除对应线段
                        int from = linesSave.size() - lastSegmentLines;
                        int to = linesSave.size();
                        if (from >= 0 && to <= linesSave.size()) {
                            linesSave.subList(from, to).clear();
                        }
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
        glDeleteTextures(textureID);
        if (imageWindow != NULL) {
            glDeleteTextures(maskTextureID);
            glDeleteTextures(finalTexture);
            glfwDestroyWindow(imageWindow);
        }
        glfwTerminate();
        Objects.requireNonNull(glfwSetErrorCallback(null)).free();
    }
// ================== 内部类 ==================
    /**
     * 表示2D点的内部类
     */
    public static class Point {
        public double x;
        public double y;
        public Point(double x, double y) {
            this.x = x;
            this.y = y;
        }
        //用来比较pathcooling的，但是没有用。
        @Override
    public boolean equals(Object obj) {
     if (this == obj) return true;
     if (obj == null || getClass() != obj.getClass()) return false;
      Point point = (Point) obj;
          return Double.compare(point.x, x) == 0 && Double.compare(point.y, y) == 0;
}

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
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

    public Point snap(int x, int y, int radius) {
        double minCost = Double.MAX_VALUE;
        Point snapPoint = new Point(x, y);

        for (int i = -radius; i <= radius; i++) {
            for (int j = -radius; j <= radius; j++) {
                int newX = x + i;
                int newY = y + j;

                if (newX >= 0 && newX < gridWidth && newY >= 0 && newY < gridHeight) {
                    double cost = ImageProcess.getCostMatrix(newX, newY);
                    if (cost < minCost) {
                        minCost = cost;
                        snapPoint.x = newX;//去掉final
                        snapPoint.y = newY;
                    }
                }
            }
        }
        return snapPoint;
    }


    /**
     * 更新点集合
     */
    public void addNewPointsPreview(List<Point> points){
        this.pointListListPreview.add(points);
    }

    private List<Integer> segmentLineCounts = new ArrayList<>();

    public void addNewPointsSave(List<Point> points) {
//        this.pointListListSave.add(points);
        if (points != null && !points.isEmpty()) {
            // 生成线段并保存到 linesSave
            int startLineCount = linesSave.size();
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
                isPathClosed = true;
                closedPathIndex = pointListListSave.size() - 1; // 记录闭合路径索引
                System.out.println("路径闭合成功！"); // 添加成功提示
            }
//            if (isClosed && points.size() >= 2) {
//                Point first = points.get(0);
//                Point last = points.get(points.size() - 1);
//                linesSave.add(new Line((float)last.x, (float)last.y, (float)first.x, (float)first.y));
//            }
            // 记录线段数量
            int addedLines = linesSave.size() - startLineCount;
            segmentLineCounts.add(addedLines);
        }
    }

    public BufferedImage convertToMaskWithFill(List<Point> points, int width, int height) {
        BufferedImage mask = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g2d = mask.createGraphics();

        // 初始化为全黑
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, width, height);

        // 填充多边形内部为白色
        g2d.setColor(Color.WHITE);
        Polygon polygon = new Polygon();
        for (Point p : points) {
            polygon.addPoint((int)p.x,(int) p.y);
        }
        g2d.fill(polygon);

        g2d.dispose();
        return mask;
    }

    /**
     * 基于代价冷却 + 时间 & 共线计数检测自动生成新种子。
     */
    private void processPathCooling() {
        // 确保我们已有足够帧的稳定数据
        if (previousPoints.isEmpty()) return;

        // 当前最新一帧的活线路径
        List<Point> latestPath = previousPoints;

        // 从起点向终点依次检查
        for (int idx = 0; idx < latestPath.size(); idx++) {
            Point p = latestPath.get(idx);

            int timeCount = stabilityTime.getOrDefault(p, 0);
            int coalCount = coalescenceCount.getOrDefault(p, 0);

            // 同时满足稳定 & 共线帧数条件
            if (timeCount >= TIME_THRESHOLD && coalCount >= TIME_THRESHOLD) {
                // 取当前像素代价并应用冷却
                double cost = ImageProcess.costMatrix[(int) p.y][(int) p.x];
                cost *= COOLING_RATE;
                ImageProcess.costMatrix[ (int) p.y][(int) p.x] = cost;

                // 如果冷却到阈值以下，则“冻”在这里，生成新种子
                if (cost < COST_THRESHOLD) {
                    // 切出从上一种子到此点的稳定段
                    List<Point> frozenSegment = latestPath.subList(0, idx + 1);
                    // 保存正式段
                    addNewPointsSave(frozenSegment);
                    // 新种子
                    seedPoints.add(p);

                    // 清除预览 & 历史，为下条段重算
                    pointListListPreview.clear();
                    stabilityTime.clear();
                    coalescenceCount.clear();
                    previousPoints.clear();

                    return;
                }
            }
        }
    }

//old
//    private boolean isPointStable(Point p1, Point p2, float threshold) {
//        return Math.sqrt(Math.pow(p1.x - p2.x, 2) + Math.pow(p1.y - p2.y, 2)) <= threshold;
//    }
    private boolean isPointStable(Point p1, Point p2, float threshold) {
        // 增加对特殊情况（如起点）的处理
        if (p1.x == p2.x && p1.y == p2.y) return true; // 完全重合

        double distance = Math.sqrt(Math.pow(p1.x - p2.x, 2) + Math.pow(p1.y - p2.y, 2));

        // 起点要求更严格，中间点可以略宽松
        boolean isStartPoint = seedPoints.size() == 1;
        return distance <= (isStartPoint ? threshold/2 : threshold);
    }
}