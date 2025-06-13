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

        // 1. 转换到Lab颜色空间
        double[][][] labPixels = rgbToLab(pixels);

        // 2. 计算颜色梯度（Lab空间三通道）
        double[][] colorGrad = calculateColorGradient(labPixels);

        // 3. 计算灰度梯度（5阶Sobel算子）
        int[][] grayPixels = grayscale(pixels);
        double sigma = width > 800 || height > 600 ? 1.2 : 0.8;
        int[][] blurredPixels = gaussianBlur(grayPixels, sigma);
        double[][] grayGrad = sobelKernel(blurredPixels);

        // 4. 整合颜色与灰度梯度（颜色权重0.6）
        costMatrix = new double[height][width];
        final double COLOR_WEIGHT = 0.3;
        final double GRAY_WEIGHT = 0.7;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double totalGrad = COLOR_WEIGHT * colorGrad[y][x] + GRAY_WEIGHT * grayGrad[y][x];
                costMatrix[y][x] = 1.0 / (1.0 + totalGrad + 1e-8);
            }
        }
    }

    // 改进的高斯模糊处理
    public static int[][] gaussianBlur(int[][] grayPixels, double sigma) {
        int height = grayPixels.length;
        int width = grayPixels[0].length;
        int[][] result = new int[height][width];
        int kernelSize = 5;
        int radius = kernelSize / 2;
        double[][] kernel = new double[kernelSize][kernelSize];
        double sum = 0.0;

        for (int i = -radius; i <= radius; i++) {
            for (int j = -radius; j <= radius; j++) {
                double x = i, y = j;
                kernel[i+radius][j+radius] = Math.exp(-(x*x + y*y) / (2*sigma*sigma))
                        / (2 * Math.PI * sigma * sigma);
                sum += kernel[i+radius][j+radius];
            }
        }

        for (int i = 0; i < kernelSize; i++) {
            for (int j = 0; j < kernelSize; j++) {
                kernel[i][j] /= sum;
            }
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double sumPixel = 0.0;
                for (int i = -radius; i <= radius; i++) {
                    for (int j = -radius; j <= radius; j++) {
                        int ny = y + i;
                        int nx = x + j;
                        if (ny < 0) ny = -ny;
                        if (ny >= height) ny = 2 * (height - 1) - ny;
                        if (nx < 0) nx = -nx;
                        if (nx >= width) nx = 2 * (width - 1) - nx;
                        sumPixel += grayPixels[ny][nx] * kernel[i+radius][j+radius];
                    }
                }
                result[y][x] = (int) Math.round(sumPixel);
            }
        }
        return result;
    }

    // 5阶Sobel算子
    public static double[][] sobelKernel(int[][] grayPixels) {
        int height = grayPixels.length;
        int width = grayPixels[0].length;
        double[][] result = new double[height][width];

        // 5×5 Sobel算子 - X方向
        int[][] sobelX = {
                {-2, -1, 0, 1, 2},
                {-3, -2, 0, 2, 3},
                {-4, -3, 0, 3, 4},
                {-3, -2, 0, 2, 3},
                {-2, -1, 0, 1, 2}
        };

        // 5×5 Sobel算子 - Y方向
        int[][] sobelY = {
                {-2, -3, -4, -3, -2},
                {-1, -2, -3, -2, -1},
                {0, 0, 0, 0, 0},
                {1, 2, 3, 2, 1},
                {2, 3, 4, 3, 2}
        };

        double maxG = 0.0;
        int radius = 2; // 5×5算子的半径为2

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double gx = 0.0, gy = 0.0;

                // 应用5×5 Sobel算子
                for (int i = -radius; i <= radius; i++) {
                    for (int j = -radius; j <= radius; j++) {
                        int ny = y + i;
                        int nx = x + j;

                        // 边界处理
                        if (ny < 0) ny = -ny;
                        if (ny >= height) ny = 2 * (height - 1) - ny;
                        if (nx < 0) nx = -nx;
                        if (nx >= width) nx = 2 * (width - 1) - nx;

                        int grayValue = grayPixels[ny][nx];
                        gx += grayValue * sobelX[i + radius][j + radius];
                        gy += grayValue * sobelY[i + radius][j + radius];
                    }
                }

                double G = Math.sqrt(gx * gx + gy * gy);
                result[y][x] = G;
                maxG = Math.max(maxG, G);
            }
        }

        if (maxG > 0) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    result[y][x] /= (maxG + 1e-8);
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

    // 灰度转换
    public static int[][] grayscale(int[][] pixels) {
        int height = pixels.length;
        int width = pixels[0].length;
        int[][] grayPixels = new int[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = pixels[y][x];
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                int gray = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                grayPixels[y][x] = gray;
            }
        }
        return grayPixels;
    }

    public static double[][][] rgbToLab(int[][] rgbPixels) {
        int height = rgbPixels.length;
        int width = rgbPixels[0].length;
        double[][][] labPixels = new double[height][width][3]; // L, a, b

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = rgbPixels[y][x];
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;

                // RGB转XYZ
                double rNorm = r / 255.0;
                double gNorm = g / 255.0;
                double bNorm = b / 255.0;

                // 重命名变量为xyzX, xyzY, xyzZ
                double xyzX = rNorm * 0.4124 + gNorm * 0.3576 + bNorm * 0.1805;
                double xyzY = rNorm * 0.2126 + gNorm * 0.7152 + bNorm * 0.0722;
                double xyzZ = rNorm * 0.0193 + gNorm * 0.1192 + bNorm * 0.9505;

                // XYZ转Lab
                double X = xyzX / 0.95047; // D65白点
                double Z = xyzZ / 1.08883;
                double fx = X > 0.008856 ? Math.cbrt(X) : (7.787 * X + 16.0 / 116.0);
                double fy = xyzY > 0.008856 ? Math.cbrt(xyzY) : (7.787 * xyzY + 16.0 / 116.0);
                double fz = Z > 0.008856 ? Math.cbrt(Z) : (7.787 * Z + 16.0 / 116.0);

                labPixels[y][x][0] = 116.0 * fy - 16.0; // L*
                labPixels[y][x][1] = 500.0 * (fx - fy); // a*
                labPixels[y][x][2] = 200.0 * (fy - fz); // b*
            }
        }
        return labPixels;
    }

    // 新增：计算Lab空间颜色梯度
    public static double[][] calculateColorGradient(double[][][] labPixels) {
        int height = labPixels.length;
        int width = labPixels[0].length;
        double[][] colorGrad = new double[height][width];
        int[][] sobelX = {{-1, 0, 1}, {-2, 0, 2}, {-1, 0, 1}};
        int[][] sobelY = {{-1, -2, -1}, {0, 0, 0}, {1, 2, 1}};

        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                double lx = 0, ly = 0;
                double ax = 0, ay = 0;
                double bx = 0, by = 0;

                // 计算L, a, b通道的梯度
                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) {
                        lx += labPixels[y+i][x+j][0] * sobelX[i+1][j+1];
                        ly += labPixels[y+i][x+j][0] * sobelY[i+1][j+1];
                        ax += labPixels[y+i][x+j][1] * sobelX[i+1][j+1];
                        ay += labPixels[y+i][x+j][1] * sobelY[i+1][j+1];
                        bx += labPixels[y+i][x+j][2] * sobelX[i+1][j+1];
                        by += labPixels[y+i][x+j][2] * sobelY[i+1][j+1];
                    }
                }

                // 合并三通道梯度（欧氏距离）
                double gradL = Math.sqrt(lx * lx + ly * ly);
                double gradA = Math.sqrt(ax * ax + ay * ay);
                double gradB = Math.sqrt(bx * bx + by * by);
                colorGrad[y][x] = Math.sqrt(gradL * gradL + gradA * gradA + gradB * gradB);
            }
        }
        return normalizeGradient(colorGrad);
    }

    // 梯度归一化
    private static double[][] normalizeGradient(double[][] grad) {
        int height = grad.length;
        int width = grad[0].length;
        double[][] normGrad = new double[height][width];
        double maxGrad = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                maxGrad = Math.max(maxGrad, grad[y][x]);
            }
        }

        if (maxGrad > 0) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    normGrad[y][x] = grad[y][x] / maxGrad;
                }
            }
        }
        return normGrad;
    }
}
