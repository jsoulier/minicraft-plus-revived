package minicraft.gfx;

import minicraft.saveload.Load;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;

import static org.lwjgl.opengl.GL33C.*;

class Shader {
	private final int program;

	Shader(String name) {
		program = glCreateProgram();
		int vertShader = compile(GL_VERTEX_SHADER, load(name + ".vert"));
		int fragShader = compile(GL_FRAGMENT_SHADER, load(name + ".frag"));
		glAttachShader(program, vertShader);
		glAttachShader(program, fragShader);
		glLinkProgram(program);
		if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
			throw new IllegalStateException(glGetProgramInfoLog(program));
		}
		glDeleteShader(vertShader);
		glDeleteShader(fragShader);
	}

	void use() {
		glUseProgram(program);
	}

	int getUniform(String name) {
		return glGetUniformLocation(program, name);
	}

	private static String load(String name) {
		ArrayList<String> lines;
		try {
			lines = Load.loadFile("/resources/shaders/" + name);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return String.join("\n", lines);
	}

	private static int compile(int type, String source) {
		int shader = glCreateShader(type);
		glShaderSource(shader, source);
		glCompileShader(shader);
		if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
			throw new IllegalStateException(glGetShaderInfoLog(shader));
		}
		return shader;
	}
}
