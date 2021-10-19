package engine;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL.createCapabilities;
import static org.lwjgl.opengl.GL43C.*;
import static org.lwjgl.opengl.GLUtil.setupDebugMessageCallback;
import static org.lwjgl.system.MemoryUtil.*;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;

public class Main {

    
    private static int frameNumber = 0;
    private static final String QUAD_PROGRAM_VS_SOURCE = Shader.readFromFile("src/shaders/quad.vert");
    private static final String QUAD_PROGRAM_FS_SOURCE = Shader.readFromFile("src/shaders/quad.frag");
    private static final String COMPUTE_SHADER_SOURCE = Shader.readFromFile("src/shaders/sim.comp");


  public static void main(String[] args) {
    System.out.println("initializing...");
    // Initialize GLFW and create window
    if (!glfwInit())
        throw new IllegalStateException("Unable to initialize GLFW");
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
    glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
    glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
    glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);
    glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, GLFW_TRUE);
    long window = glfwCreateWindow(Constants.WINDOW_WIDTH, Constants.WINDOW_HEIGHT, "loading..." + frameNumber, NULL, NULL);
    if (window == NULL)
        throw new AssertionError("Failed to create the GLFW window");
    Input.setKeybinds(window);
    //glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_HIDDEN);

    // Make context current and install debug message callback
    glfwMakeContextCurrent(window);
    createCapabilities();
    setupDebugMessageCallback();

    // Create VAO
    glBindVertexArray(glGenVertexArrays());

    // Create framebuffer texture to render into
    int framebuffer = glGenTextures();
    glBindTexture(GL_TEXTURE_2D, framebuffer);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexStorage2D(GL_TEXTURE_2D, 1, GL_RGBA8, Constants.WINDOW_WIDTH, Constants.WINDOW_HEIGHT);
    glBindImageTexture(0, framebuffer, 0, false, 0, GL_WRITE_ONLY, GL_RGBA8);

    // Create program to render framebuffer texture as fullscreen quad
    System.out.print("creating fullscreen quad...");
    int quadProgram = glCreateProgram();
    int quadProgramVs = glCreateShader(GL_VERTEX_SHADER);
    int quadProgramFs = glCreateShader(GL_FRAGMENT_SHADER);
    glShaderSource(quadProgramVs, QUAD_PROGRAM_VS_SOURCE);
    glShaderSource(quadProgramFs, QUAD_PROGRAM_FS_SOURCE);
    glCompileShader(quadProgramVs);
    glCompileShader(quadProgramFs);
    glAttachShader(quadProgram, quadProgramVs);
    glAttachShader(quadProgram, quadProgramFs);
    glLinkProgram(quadProgram);
    System.out.println(" done!");

    // Create ray tracing compute shader
    int computeProgram = glCreateProgram();
    int computeProgramShader = glCreateShader(GL_COMPUTE_SHADER);
    glShaderSource(computeProgramShader, COMPUTE_SHADER_SOURCE);
    glCompileShader(computeProgramShader);
    glAttachShader(computeProgram, computeProgramShader);
    glLinkProgram(computeProgram);

    // Determine number of work groups to dispatch
    int numGroupsX = (int) Math.ceil((double)Constants.WINDOW_WIDTH / 8);
    int numGroupsY = (int) Math.ceil((double)Constants.WINDOW_HEIGHT / 8);

    // Make window visible and loop until window should be closed
    glfwShowWindow(window);


    int ssbo = 0;
    ssbo = glGenBuffers();
    int bindIndex = 7;
    int blockIndex = glGetProgramResourceIndex(computeProgram, GL_SHADER_STORAGE_BLOCK, "shaderStorage");
    glShaderStorageBlockBinding(computeProgram, blockIndex, bindIndex);
    glBindBuffer(GL_SHADER_STORAGE_BUFFER, ssbo);

    IntBuffer curBuffer = BufferUtils.createIntBuffer(Constants.WINDOW_HEIGHT * Constants.WINDOW_WIDTH);
    IntBuffer nextBuffer = BufferUtils.createIntBuffer(Constants.WINDOW_HEIGHT * Constants.WINDOW_WIDTH);
    for(int i=0; i < Constants.WINDOW_HEIGHT*Constants.WINDOW_WIDTH; i++){
      double rand = Math.random();
      if(rand > 0.5) curBuffer.put(i, 1);
      else curBuffer.put(i, 0);
    }
    // for(int i=0; i < Constants.WINDOW_HEIGHT; i++){
    //   for(int j=0; j < Constants.WINDOW_WIDTH; j++){
    //     if(i >= 500) curBuffer.put(i*Constants.WINDOW_WIDTH + j, 1);
    //     else curBuffer.put(i*Constants.WINDOW_WIDTH + j, 0);
    //   }
    // }
    
    
    glBindBufferRange(GL_SHADER_STORAGE_BUFFER, bindIndex, ssbo, 0, 3);
    glBufferData(GL_SHADER_STORAGE_BUFFER, curBuffer, GL_DYNAMIC_DRAW);
    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, bindIndex, ssbo);
    //glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
    

    int renderMode = 0;
    int lastOffset = 0;
    double frameTime = 0.0d;
    while (!glfwWindowShouldClose(window)) {
      glfwPollEvents();
      glfwSetWindowTitle(window, "sim-engine v0.1 | Gen " + frameNumber);
      if(!Input.keyDown(Input.SIM_STEP)){
        for(int i=0; i < Constants.WINDOW_HEIGHT; i++){
          for(int j=0; j < Constants.WINDOW_WIDTH; j++){
            int index = i*Constants.WINDOW_WIDTH + j;
            int curVal = curBuffer.get(index);
            int neighbors = 0;
            int rows = Constants.WINDOW_HEIGHT;
            int cols = Constants.WINDOW_WIDTH;
            if(curBuffer.get(cols*getModVal(i+1, rows) + j) == 1) neighbors++;
            if(curBuffer.get(cols*getModVal(i-1, rows) + j) == 1) neighbors++;
            if(curBuffer.get(cols*i + getModVal(j+1, cols)) == 1) neighbors++;
            if(curBuffer.get(cols*i + getModVal(j-1, cols)) == 1) neighbors++;
            if(curBuffer.get(cols*getModVal(i+1, rows) + getModVal(j+1, cols)) == 1) neighbors++;
            if(curBuffer.get(cols*getModVal(i+1, rows) + getModVal(j-1, cols)) == 1) neighbors++;
            if(curBuffer.get(cols*getModVal(i-1, rows) + getModVal(j+1, cols)) == 1) neighbors++;
            if(curBuffer.get(cols*getModVal(i-1, rows) + getModVal(j-1, cols)) == 1) neighbors++;
            boolean alive = false;
            if(curVal == 1){
              if(neighbors == 2 || neighbors == 3) alive = true;
            }else{
              if(neighbors == 3) alive = true;
            }
            if(alive) nextBuffer.put(index, 1);
            else nextBuffer.put(index, 0);
          }
        }
        IntBuffer temp = curBuffer;
        curBuffer = nextBuffer;
        nextBuffer = temp;
        frameNumber++;
      }
      
      
      //nextBuffer.clear();
      glBufferData(GL_SHADER_STORAGE_BUFFER, curBuffer, GL_DYNAMIC_DRAW);
      glUseProgram(computeProgram);
      glDispatchCompute(numGroupsX, numGroupsY, 1);
      glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);

      //Update frame
      glUniform1i(5, Constants.WINDOW_WIDTH);
      glUniform1i(6, Constants.WINDOW_HEIGHT); 
      
      // Display framebuffer texture
      glUseProgram(quadProgram);
      glDrawArrays(GL_TRIANGLES, 0, 3);
      glfwSwapBuffers(window);
    }
  }

  static int getModVal(int x, int N){
    int adj = x/N;
    int out = ((x+adj*N)%N);
    if(out < 0) out += N;
    return out;
  }
}