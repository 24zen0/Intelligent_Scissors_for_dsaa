package project1;

import java.io.IOException;

public class main {
    public static void main(String[] args) {
        try {
            // 初始化缓存
            ImageProcess.processImageToSobel("/Users/zengyitao/IdeaProjects/Data_Structure_and_Algorithm_Analysis/Intelligent_Scissors_for_dsaa/src/img2.png");

            // 查询任意位置的 cost 值
            double cost1 = ImageProcess.getCostMatrix(50, 50);  // 正常位置
            double cost2 = ImageProcess.getCostMatrix(-1, 100); // 越界，返回 1.0

            System.out.println("Cost1: " + cost1 + ", Cost2: " + cost2);
//            System.out.println("%d",sizeof(ImageProcess.costMatrix) / sizeof(ImageProcess.costMatrix[0]));


        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}