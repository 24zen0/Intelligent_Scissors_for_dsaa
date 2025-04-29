import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class ImageProcess {

    public static void main(String[] args) {
        try {
            // 读取图像文件
            File input = new File("D:\\DSAA B\\DSAA B\\Project\\Image\\img1.png");
            BufferedImage image = ImageIO.read(input);

            int width = image.getWidth();
            int height = image.getHeight();

            // 存储像素点的二维数组
            int[][] pixels = new int[height][width];

            // 遍历图像的每个像素点，并存储到数组中
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    pixels[y][x] = image.getRGB(x, y);
                }
            }

            int[][] processedPixels = grayscale(pixels);
            double[][] sobelPixels = sobelKernel(processedPixels);

            //saveGrayMatrixToTextFile(processedPixels, "D:\\DSAA B\\DSAA B\\Project\\Image\\gray_matrix.txt");
            //saveSobelMatrixToTextFile(sobelPixels, "D:\\DSAA B\\DSAA B\\Project\\Image\\sobel_matrix.txt");

            String sobelMatrixString = getSobelMatrixAsString(sobelPixels);
            //测试，可删
            System.out.println("Sobel矩阵:");
            System.out.println(sobelMatrixString);


        } catch (IOException e) {
            System.out.println("Error reading or writing the image: " + e.getMessage());
        }

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
    public static void saveGrayMatrixToTextFile(int[][] matrix, String filePath) throws IOException {
        FileWriter writer = new FileWriter(filePath);
        for (int[] row : matrix) {
            for (int value : row) {
                writer.write(value + " ");
            }
            writer.write("\n");
        }
        writer.close();
        System.out.println("Gray matrix saved to " + filePath);
    }

    public static double[][] sobelKernel(int[][] pixels) {
        int height = pixels.length;
        int width = pixels[0].length;
        double[][] result = new double[height][width];
        double maxG = 0;

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
                maxG = Math.max(maxG, G);
            }
        }

        // 计算每个像素的 fG 值
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                double G = result[y][x];
                result[y][x] = (maxG - G) / maxG;
            }
        }
        return result;
    }

    public static String getSobelMatrixAsString(double[][] matrix) {
        StringBuilder sb = new StringBuilder();
        for (double[] row : matrix) {
            for (double value : row) {
                sb.append(value).append(" ");
            }
            sb.append("\n");
        }
        return sb.toString();
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