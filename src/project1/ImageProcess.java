package project1;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class ImageProcess {

    static double[][] costMatrix;

    public static void processImageToSobel(String imagePath) throws IOException {
        BufferedImage image = ImageIO.read(new File(imagePath));
        int width = image.getWidth();
        int height = image.getHeight();

        int[][] pixels = new int[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[y][x] = image.getRGB(x, y);
            }
        }

        int[][] grayPixels = grayscale(pixels);
        int[][] blurredPixels = gaussianBlur(grayPixels); // 高斯模糊预处理
        double[][] sobelMatrix = sobelKernel(blurredPixels);

        costMatrix = new double[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double normalizedG = sobelMatrix[y][x];
                costMatrix[y][x] = 1.0 / (1 + normalizedG); // 使用归一化后的G
            }
        }
    }

    // 高斯模糊处理
    public static int[][] gaussianBlur(int[][] grayPixels) {
        int height = grayPixels.length;
        int width = grayPixels[0].length;
        int[][] result = new int[height][width];
        double[][] kernel = {
                {1.0/16, 2.0/16, 1.0/16},
                {2.0/16, 4.0/16, 2.0/16},
                {1.0/16, 2.0/16, 1.0/16}
        };

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double sum = 0.0;
                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) {
                        int ny = Math.max(0, Math.min(y + i, height - 1));
                        int nx = Math.max(0, Math.min(x + j, width - 1));
                        sum += grayPixels[ny][nx] * kernel[i + 1][j + 1];
                    }
                }
                result[y][x] = (int) Math.round(sum);
            }
        }
        return result;
    }

    // 改进的Sobel算子处理
    public static double[][] sobelKernel(int[][] grayPixels) {
        int height = grayPixels.length;
        int width = grayPixels[0].length;
        double[][] result = new double[height][width];
        int[][] sobelX = {{-1, 0, 1}, {-2, 0, 2}, {-1, 0, 1}};
        int[][] sobelY = {{-1, -2, -1}, {0, 0, 0}, {1, 2, 1}};
        double maxG = 0.0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double gx = 0.0, gy = 0.0;
                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) {
                        int ny = Math.max(0, Math.min(y + i, height - 1));
                        int nx = Math.max(0, Math.min(x + j, width - 1));
                        int grayValue = grayPixels[ny][nx];
                        gx += grayValue * sobelX[i + 1][j + 1];
                        gy += grayValue * sobelY[i + 1][j + 1];
                    }
                }
                double G = Math.sqrt(gx * gx + gy * gy);
                result[y][x] = G;
                maxG = Math.max(maxG, G);
            }
        }

        // 归一化到[0,1]
        if (maxG > 0) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    result[y][x] /= maxG;
                }
            }
        }

        return result;
    }

    public static double getCostMatrix(int x, int y) {
        if (costMatrix == null || x < 0 || x >= costMatrix[0].length || y < 0 || y >= costMatrix.length) {
            return 1.0;
        }
        return costMatrix[y][x];
    }

    // 其他方法如grayscale保持不变
    public static int[][] grayscale(int[][] pixels) {
        // 原有实现不变
        return pixels;
    }
}