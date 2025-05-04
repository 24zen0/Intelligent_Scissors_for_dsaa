package project1;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class IntelligentScissors {
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
            //image processing
//            int[][] processedPixels = grayscale(pixels);
//            double[][] sobelPixels = sobelKernel(processedPixels);
//
//            saveGrayMatrixToTextFile(processedPixels, "D:\\DSAA B\\DSAA B\\Project\\Image\\gray_matrix.txt");
//            saveSobelMatrixToTextFile(sobelPixels, "D:\\DSAA B\\DSAA B\\Project\\Image\\sobel_matrix.txt");

        } catch (IOException e) {
            System.out.println("Error reading or writing the image: " + e.getMessage());
        }
        //initialize the gray matrix,selecting seeds

    }
}
