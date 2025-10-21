Here's the translation of your game development process documentation into English:

2.1 Basic Flow

public void startGame() { 
	init();  // Initialize all required components
	gameLoop();  // Input handling, updates, rendering
	dispose();  // Cleanup
}


Part 1: init()

Initialize configurations, create window, set callbacks (responses to mouse/keyboard events)

Variables: Images, mouse, point/line storage variables

Callbacks:
1. Press 'L' (case insensitive) at startup to load image: loadImage("src/example.png");
	![[Pasted image 20250430162506.png|200]]
2. Ctrl + Mouse scroll: Page zoom
3. Window drag resizing: Handle window size changes
4. Modes:
   1. Normal mode: Left-click to mark points (mouseButtonCallback implements snapping to nearest coordinate point, similar to alignment assist)
   2. Hand mode: Press 'H' to toggle, allows moving the image (Completed)

Part 2: loop()

GUI has three layers: Bottom image layer, coordinate system (corresponding to image pixels) (Incomplete), point/line drawing layer
while (!glfwWindowShouldClose(window)) {  
1. Image loading: `private void loadImage(String path)`	
2. Set projection matrix → Switch to model-view matrix (Various image scaling handling code)
3. Drawing
    drawAll();  (Implemented inverted circle highlighting, no changes to lines)
4. Display zoom ratio (Incomplete, considering removal)
5. Detect closed shapes and render (Incomplete)
}


2.2

Added test methods at the end to evaluate performance and stability when randomly adding points.

Abandoned displaying zoom ratio.

Considering extracting edge detection as a separate feature.

2.3

Added grid display functionality, toggle with 'G' key.

Note: I've maintained all technical terms and code elements while making the English flow naturally. The markdown structure and image reference remain unchanged. Let me know if you'd like any adjustments to the translation.