package minicraft.gfx;

import static org.lwjgl.opengl.GL33C.*;

class Texture {
	final int id;
	final int width, height;

	Texture(int width, int height, int format) {
		this.width = width;
		this.height = height;
		id = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, id);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		glTexImage2D(GL_TEXTURE_2D, 0, format, width, height, 0, GL_BGRA, GL_UNSIGNED_BYTE, (int[]) null);
	}

	void bind() {
		glBindTexture(GL_TEXTURE_2D, id);
	}

	void upload(int[] pixels) {
		bind();
		glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, pixels);
	}

	void delete() {
		glDeleteTextures(id);
	}
}
