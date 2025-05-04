2.1 ： 
# 基本流程
```java
public void startGame() { 
	init();  // 所有要用的东西
	gameLoop();  // 输入、更新、渲染
	dispose();  // 收拾残局
	}
```

### part 1: init()
初始化配置，创建窗口，设置回调（鼠标按下/键盘按下时什么反应等）
变量：图片、鼠标、点线存储的相关变量
callback:
1. 开局按下L（不分大小写）加载图片`loadImage("src/example.png");`
	![[Pasted image 20250430162506.png|200]]
2. ctrl + 鼠标滚轮scroll : 页面缩放
3. 拖拽窗口大小 ：窗口大小变化
4. 模式
	1. 正常模式：单击左键，标点（`mouseButtonCallback`实现了查找距离鼠标最近的坐标点，类似辅助对齐）
	2. handmode：按H切换，可挪动图片==完成==
### part 2: loop()
GUIs\设置了三个图层：最下层图片，坐标系（与图片像素对应）（未完成），点线绘制
while (!glfwWindowShouldClose(window)) {  
1. 图片加载`private void loadImage(String path)`	
2. 设置投影矩阵  切换到模型视图矩阵  (一堆处理图片缩放的代码)
3. 绘图
    drawAll();  （实现了圈反色，线没有改动）
4. 显示缩放比例（未完成，考虑放弃）
5. 检测线条封闭并出图（未完成）
}

2.2 末尾加入测试method，用来检测随机加入点时程序的性能与稳定性；
放弃缩放比例显示

考虑将边缘识别单独做出来。

2.3 添加显示网格功能，按下G切换