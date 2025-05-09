package project1;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;



public class ImageProcess {

    // 静态全局缓存 cost 矩阵
    static double[][] costMatrix;

    // 主流程方法：读取图像并生成 cost 矩阵
    public static void processImageToSobel(String imagePath) throws IOException {
        // 读取图像
        BufferedImage image = ImageIO.read(new File(imagePath));
        int width = image.getWidth();
        int height = image.getHeight();

        // 获取原始像素
        int[][] pixels = new int[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[y][x] = image.getRGB(x, y);
            }
        }

        // 生成灰度矩阵和 Sobel 矩阵
        int[][] grayPixels = grayscale(pixels);
        double[][] sobelMatrix = sobelKernel(grayPixels);

        // 计算并缓存 cost 矩阵
        costMatrix = new double[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double G = sobelMatrix[y][x];
                costMatrix[y][x] = 1.0 / (1 + G);
            }
        }
    }

    // Cost 函数直接访问缓存
    public static double getCostMatrix(int x, int y) {
        // 边界或未初始化时返回 1.0
        if (costMatrix == null || x < 0 || x >= costMatrix[0].length || y < 0 || y >= costMatrix.length) {
            return 1.0;
        }
        return costMatrix[y][x];
    }

    // 灰度化处理方法
    public static int[][] grayscale(int[][] pixels) {
        int height = pixels.length;
        int width = pixels[0].length;
        int[][] result = new int[height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = pixels[y][x];
                Color color = new Color(argb, true);
                // 计算灰度值
                int gray = (int) (color.getRed() * 0.299 + color.getGreen() * 0.587 + color.getBlue() * 0.114);
                result[y][x] = gray;
            }
        }

        return result;
    }

    //保存灰度矩阵
    /*public static void saveGrayMatrixToTextFile(int[][] matrix, String filePath) throws IOException {
        FileWriter writer = new FileWriter(filePath);
        for (int[] row : matrix) {
            for (int value : row) {
                writer.write(value + " ");
            }
            writer.write("\n");
        }
        writer.close();
        System.out.println("Gray matrix saved to " + filePath);
    }*/

    public static double[][] sobelKernel(int[][] pixels) {
        int height = pixels.length;
        int width = pixels[0].length;
        double[][] result = new double[height][width];


        // 定义Sobel算子
        int[][] sobelX = {
                {-1, 0, 1},
                {-2, 0, 2},
                {-1, 0, 1}
        };
        int[][] sobelY = {
                {-1, -2, -1},
                {0, 0, 0},
                {1, 2, 1}
        };

        // 遍历图像的每个像素，跳过边缘像素
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                // 初始化梯度值
                int gx = 0;
                int gy = 0;
                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) {
                        gx += pixels[y + i][x + j] * sobelX[i + 1][j + 1];
                        gy += pixels[y + i][x + j] * sobelY[i + 1][j + 1];
                    }
                }

                double G = Math.sqrt(gx * gx + gy * gy);
                result[y][x] = G;
//                maxG = Math.max(maxG, G);
            }
        }

        // 计算每个像素的 fG 值，fG似乎没有用上
//        for (int y = 1; y < height - 1; y++) {
//            for (int x = 1; x < width - 1; x++) {
//                double G = result[y][x];
//                result[y][x] = (maxG - G) / maxG;
//            }
//        }
        return result;
    }




    //保存sobel矩阵
    public static void saveSobelMatrixToTextFile(double[][] matrix, String filePath) throws IOException {
        FileWriter writer = new FileWriter(filePath);
        for (double[] row : matrix) {
            for (double value : row) {
                writer.write(value + " ");
            }
            writer.write("\n");
        }
        writer.close();
        System.out.println("Sobel matrix saved to " + filePath);
    }

}