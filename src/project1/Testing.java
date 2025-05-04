package project1;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;

public class Testing {
    public static void main(String[] args) {
        // 设置图像尺寸
        int width = 256;
        int height = 256;

        // 创建BufferedImage对象，使用TYPE_INT_RGB类型（24位RGB，无alpha通道）
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        // 获取可写的光栅对象
        WritableRaster raster = image.getRaster();

        // 生成渐变图像
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // 设置像素值：R=x, G=y, B=(x+y)/2
                int[] pixel = {x, y, (x + y) / 2}; // R,G,B分量
                raster.setPixel(x, y, pixel);
            }
        }

        // 保存图像到文件
        try {
            File outputFile = new File("gradient.png");
            ImageIO.write(image, "png", outputFile);
            System.out.println("渐变图像已成功保存到: " + outputFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("保存图像时出错: " + e.getMessage());
        }
    }
}