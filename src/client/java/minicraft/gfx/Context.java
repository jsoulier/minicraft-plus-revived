package minicraft.gfx;

import minicraft.core.Renderer;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.awt.AWTGLCanvas;
import org.lwjgl.opengl.awt.GLData;

import java.lang.ref.ReferenceQueue;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;

import static org.lwjgl.opengl.GL33C.*;

public class Context extends AWTGLCanvas {
	private static final float FOV = (float) Math.toRadians(70);
	private static final float WORLD_NEAR = 1;
	private static final float WORLD_FAR = 16 * 30;
	private static final float TILE_SIZE = 16;
	private static final int BYTES_PER_VERTEX = 32;
	private static final int VERTICES_PER_QUAD = 6;
	private static final int MODE_TEXTURE = 0;
	private static final int MODE_MASK = 1;
	private static final int MODE_SOLID = 2;

	// TODO: Doesn't really belong here but I'm not sure where's a better spot
	public enum SpriteMode {
		GROUND,
		IMPOSTER,
		WALL,
		BILLBOARD,
	}

	private static class Batch {
		ByteBuffer vertices = BufferUtils.createByteBuffer(BYTES_PER_VERTEX * VERTICES_PER_QUAD * 1024);
		final ArrayList<MinicraftImage> textures = new ArrayList<>();
		private final Shader shader;
		private final int vao, vbo;

		Batch(Shader shader) {
			this.shader = shader;
			vao = glGenVertexArrays();
			glBindVertexArray(vao);
			vbo = glGenBuffers();
			glBindBuffer(GL_ARRAY_BUFFER, vbo);
			glVertexAttribPointer(0, 3, GL_FLOAT, false, BYTES_PER_VERTEX, 0);
			glVertexAttribPointer(1, 2, GL_FLOAT, false, BYTES_PER_VERTEX, 12);
			glVertexAttribPointer(2, GL_BGRA, GL_UNSIGNED_BYTE, true, BYTES_PER_VERTEX, 20);
			glVertexAttribIPointer(3, 1, GL_UNSIGNED_BYTE, BYTES_PER_VERTEX, 28);
			glVertexAttribPointer(4, GL_BGRA, GL_UNSIGNED_BYTE, true, BYTES_PER_VERTEX, 24);
			glVertexAttribIPointer(5, 1, GL_UNSIGNED_BYTE, BYTES_PER_VERTEX, 29);
			for (int i = 0; i < 6; i++) {
				glEnableVertexAttribArray(i);
			}
			glBindVertexArray(0);
		}

		void push(MinicraftImage texture,
				  float x00, float y00, float z00, float x10, float y10, float z10,
		          float x11, float y11, float z11, float x01, float y01, float z01,
		          float u0, float v0, float u1, float v1, int tint, boolean tinted, int color, int mode) {
			if (vertices.remaining() < BYTES_PER_VERTEX * VERTICES_PER_QUAD) {
				ByteBuffer newVertices = BufferUtils.createByteBuffer(vertices.capacity() * 2);
				vertices.flip();
				newVertices.put(vertices);
				vertices = newVertices;
			}
			vertex(x00, y00, z00, u0, v0, tint, tinted, color, mode);
			vertex(x10, y10, z10, u1, v0, tint, tinted, color, mode);
			vertex(x11, y11, z11, u1, v1, tint, tinted, color, mode);
			vertex(x00, y00, z00, u0, v0, tint, tinted, color, mode);
			vertex(x11, y11, z11, u1, v1, tint, tinted, color, mode);
			vertex(x01, y01, z01, u0, v1, tint, tinted, color, mode);
			textures.add(texture);
		}

		// TODO: For the depthBatch, we could bin by texture instead of trying to preserve ordering
		void draw() {
			if (textures.isEmpty()) {
				return;
			}
			vertices.flip();
			shader.use();
			glActiveTexture(GL_TEXTURE0);
			glBindVertexArray(vao);
			glBindBuffer(GL_ARRAY_BUFFER, vbo);
			glBufferData(GL_ARRAY_BUFFER, vertices, GL_STREAM_DRAW);
			int start = 0;
			for (int i = 1; i <= textures.size(); i++) {
				if (i < textures.size() && textures.get(i) == textures.get(start)) {
					continue;
				}
				MinicraftImage image = textures.get(start);
				if (image != null) {
					image.texture.bind();
				} else {
					glBindTexture(GL_TEXTURE_2D, 0);
				}
				glDrawArrays(GL_TRIANGLES, start * VERTICES_PER_QUAD, (i - start) * VERTICES_PER_QUAD);
				start = i;
			}
			glBindVertexArray(0);
			vertices.clear();
			textures.clear();
		}

		private void vertex(float x, float y, float z, float u, float v, int tint, boolean tinted, int color, int mode) {
			vertices.putFloat(x);
			vertices.putFloat(y);
			vertices.putFloat(z);
			vertices.putFloat(u);
			vertices.putFloat(v);
			vertices.putInt(tint);
			vertices.putInt(color);
			vertices.put((byte) (tinted ? 1 : 0));
			vertices.put((byte) mode);
			vertices.putShort((short) 0);
		}
	}

	private final int width = Screen.w, height = Screen.h;
	private final Matrix4f projection = new Matrix4f();
	private final Matrix4f view = new Matrix4f();
	private final Matrix4f viewProjection = new Matrix4f();
	private final float[] matrix = new float[16];

	private int framebuffer, depthRenderbuffer, offscreenFramebuffer;
	private Texture colorTexture, positionTexture, offscreenTexture, lightTexture;
	private Shader spriteShader, lightingShader;
	private int emptyVao;
	private int spriteViewProjection, lightingFirstPerson, lightingAlpha;

	private final ReferenceQueue<MinicraftImage> textureDeletionQueue = new ReferenceQueue<>();
	private final HashSet<TextureReference> textureReferences = new HashSet<>();
	private Batch orderedBatch, depthBatch;
	private int viewX, viewY, viewWidth, viewHeight;
	private boolean firstPerson;
	private float forwardX, forwardY;
	private SpriteMode spriteMode = SpriteMode.GROUND;
	private float positionX, positionY;
	private int groundOffset;

	public Context() {
		super(getGLData());
	}

	private static GLData getGLData() {
		GLData data = new GLData();
		data.majorVersion = 3;
		data.minorVersion = 3;
		data.profile = GLData.Profile.CORE;
		data.forwardCompatible = true;
		data.doubleBuffer = true;
		data.swapInterval = 0;
		return data;
	}

	public void draw(int x, int y, int width, int height) {
		viewX = x;
		viewY = y;
		viewWidth = width;
		viewHeight = height;
		render();
	}

	public void read(int[] pixels) {
		runInContext(() -> {
			int[] rows = new int[width * height];
			glBindFramebuffer(GL_READ_FRAMEBUFFER, framebuffer);
			glReadPixels(0, 0, width, height, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, rows);
			glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					pixels[x + y * width] = rows[x + (height - 1 - y) * width] & 0xFFFFFF;
				}
			}
		});
	}

	@Override
	public void initGL() {
		GL.createCapabilities();

		colorTexture = new Texture(width, height, GL_RGBA8);
		positionTexture = new Texture(width, height, GL_RG32F);
		depthRenderbuffer = glGenRenderbuffers();
		glBindRenderbuffer(GL_RENDERBUFFER, depthRenderbuffer);
		glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, width, height);
		framebuffer = glGenFramebuffers();
		glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
		glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorTexture.id, 0);
		glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, positionTexture.id, 0);
		glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depthRenderbuffer);
		glDrawBuffers(new int[] {GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1});

		offscreenTexture = new Texture(width, height, GL_RGBA8);
		offscreenFramebuffer = glGenFramebuffers();
		glBindFramebuffer(GL_FRAMEBUFFER, offscreenFramebuffer);
		glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, offscreenTexture.id, 0);
		glBindFramebuffer(GL_FRAMEBUFFER, 0);

		lightTexture = new Texture(width, height, GL_RGBA8);

		spriteShader = new Shader("sprite");
		lightingShader = new Shader("lighting");
		spriteShader.use();
		glUniform1i(spriteShader.getUniform("sheet"), 0);
		spriteViewProjection = spriteShader.getUniform("viewProjection");
		lightingShader.use();
		glUniform1i(lightingShader.getUniform("light"), 1);
		glUniform1i(lightingShader.getUniform("position"), 2);
		lightingFirstPerson = lightingShader.getUniform("isFirstPerson");
		lightingAlpha = lightingShader.getUniform("alpha");
		glUseProgram(0);

		orderedBatch = new Batch(spriteShader);
		depthBatch = new Batch(spriteShader);
		emptyVao = glGenVertexArrays();
	}

	void begin() {
		glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
		glViewport(0, 0, width, height);
		glDisable(GL_DEPTH_TEST);
		glDisable(GL_BLEND);
		resetCamera();
	}

	void end() {
		flush();
		glBindFramebuffer(GL_FRAMEBUFFER, 0);
		TextureReference reference;
		while ((reference = (TextureReference) textureDeletionQueue.poll()) != null) {
			reference.texture.delete();
			textureReferences.remove(reference);
		}
	}

	void clear(int color) {
		flush();
		glClearBufferfv(GL_COLOR, 0, new float[] {Color.red(color), Color.green(color), Color.blue(color), 1});
		glClearBufferfv(GL_COLOR, 1, new float[] {0, 0, 0, 0});
		glClear(GL_DEPTH_BUFFER_BIT);
	}

	void setCamera(int x, int y, float dirX, float dirY, float eyeHeight) {
		flush();
		firstPerson = true;
		forwardX = dirX;
		forwardY = dirY;
		spriteMode = SpriteMode.GROUND;
		projection.setPerspective(FOV, (float) width / height, WORLD_NEAR, WORLD_FAR);
		view.setLookAt(x, eyeHeight, y, x + dirX, eyeHeight, y + dirY, 0, 1, 0);
		projection.mul(view, viewProjection);
		setViewProjection();
		glClear(GL_DEPTH_BUFFER_BIT);
	}

	void resetCamera() {
		flush();
		firstPerson = false;
		spriteMode = SpriteMode.GROUND;
		viewProjection.setOrtho(0, width, height, 0, -1, 1);
		setViewProjection();
	}

	private void setViewProjection() {
		viewProjection.get(matrix);
		spriteShader.use();
		glUniformMatrix4fv(spriteViewProjection, false, matrix);
	}

	void beginOffscreen() {
		flush();
		glBindFramebuffer(GL_FRAMEBUFFER, offscreenFramebuffer);
		glClearColor(0, 0, 0, 1);
		glClear(GL_COLOR_BUFFER_BIT);
	}

	void endOffscreen(int srcX, int srcY, int dstX, int dstY, int w, int h) {
		flush();
		glBindFramebuffer(GL_READ_FRAMEBUFFER, offscreenFramebuffer);
		glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
		glBlitFramebuffer(srcX, height - srcY - h, srcX + w, height - srcY, dstX, height - dstY - h, dstX + w, height - dstY, GL_COLOR_BUFFER_BIT, GL_NEAREST);
		glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
	}

	void setSpriteMode(SpriteMode spriteMode, int x, int y, int groundOffset) {
		this.spriteMode = spriteMode;
		this.groundOffset = groundOffset;
		positionX = x;
		positionY = y;
	}

	void sprite(int x, int y, int u, int v, int w, int h, boolean mirrorX, boolean mirrorY, int tint, boolean fullBright, int inColor, MinicraftImage sheet) {
		prepareTexture(sheet);
		float u0 = u;
		float u1 = u + w;
		if (mirrorX) {
			u0 = u + w;
			u1 = u;
		}
		float v0 = v;
		float v1 = v + h;
		if (mirrorY) {
			v0 = v + h;
			v1 = v;
		}
		boolean tinted = tint != -1;
		int color = inColor;
		if (fullBright) {
			color = Color.WHITE;
		}
		int mode = MODE_TEXTURE;
		if (fullBright || inColor != 0) {
			mode = MODE_MASK;
		}
		quad(sheet, x, y, x + w, y + h, u0, v0, u1, v1, tint, tinted, color, mode);
	}

	void fillRect(int x, int y, int w, int h, int color) {
		if (w <= 0 || h <= 0) {
			return;
		}
		quad(null, x, y, x + w, y + h, 0, 0, 0, 0, 0, false, color, MODE_SOLID);
	}

	void drawRect(int x, int y, int w, int h, int color) {
		if (w < 0 || h < 0) {
			return;
		}
		fillRect(x, y, w + 1, 1, color);
		fillRect(x, y + h, w + 1, 1, color);
		fillRect(x, y, 1, h + 1, color);
		fillRect(x + w, y, 1, h + 1, color);
	}

	void drawLine(int x0, int y0, int x1, int y1, int color) {
		float dx = x1 - x0;
		float dy = y1 - y0;
		float length = (float) Math.sqrt(dx * dx + dy * dy);
		float directionX = 1;
		float directionY = 0;
		if (length > 0) {
			directionX = dx / length;
			directionY = dy / length;
		}
		float halfDirectionX = directionX * 0.5f;
		float halfDirectionY = directionY * 0.5f;
		float halfNormalX = -directionY * 0.5f;
		float halfNormalY = directionX * 0.5f;
		float startX = x0 + 0.5f - halfDirectionX;
		float startY = y0 + 0.5f - halfDirectionY;
		float endX = x1 + 0.5f + halfDirectionX;
		float endY = y1 + 0.5f + halfDirectionY;
		orderedBatch.push(null,
			startX + halfNormalX, startY + halfNormalY, 0,
			endX + halfNormalX, endY + halfNormalY, 0,
			endX - halfNormalX, endY - halfNormalY, 0,
			startX - halfNormalX, startY - halfNormalY, 0,
			0, 0, 0, 0, 0, false, color, MODE_SOLID);
	}

	void drawLineSpecial(int x, int y, int w, int h) {
		flush();
		glEnable(GL_BLEND);
		glBlendFunc(GL_ONE_MINUS_DST_COLOR, GL_ZERO);
		fillRect(x, y, w, h, Color.WHITE);
		flush();
		glDisable(GL_BLEND);
	}

	void overlay(int[] pixels, float alpha) {
		flush();
		glActiveTexture(GL_TEXTURE1);
		lightTexture.upload(pixels);
		glActiveTexture(GL_TEXTURE0);
		lightingShader.use();
		glUniform1f(lightingAlpha, alpha);
		if (firstPerson) {
			glUniform1i(lightingFirstPerson, 1);
			glActiveTexture(GL_TEXTURE2);
			positionTexture.bind();
			glActiveTexture(GL_TEXTURE0);
			glDrawBuffers(GL_COLOR_ATTACHMENT0);
		} else {
			glUniform1i(lightingFirstPerson, 0);
		}
		glEnable(GL_BLEND);
		glBlendFunc(GL_ZERO, GL_SRC_COLOR);
		glBindVertexArray(emptyVao);
		glDrawArrays(GL_TRIANGLES, 0, 3);
		glBindVertexArray(0);
		glDisable(GL_BLEND);
		if (firstPerson) {
			glDrawBuffers(new int[] {GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1});
		}
		spriteShader.use();
	}

	private void quad(MinicraftImage texture, float x0, float y0, float x1, float y1,
		              float u0, float v0, float u1, float v1, int tint, boolean tinted, int color, int mode) {
		if (!firstPerson) {
			orderedBatch.push(texture,
				x0, y0, 0,
				x1, y0, 0,
				x1, y1, 0,
				x0, y1, 0,
				u0, v0, u1, v1, tint, tinted, color, mode);
		} else if (spriteMode == SpriteMode.GROUND) {
			orderedBatch.push(texture,
				x0, 0, y0,
				x1, 0, y0,
				x1, 0, y1,
				x0, 0, y1,
				u0, v0, u1, v1, tint, tinted, color, mode);
		} else if (spriteMode == SpriteMode.WALL) {
			float left = x0 - positionX;
			float right = x1 - positionX;
			float top = positionY + groundOffset - y0;
			float base = positionY + groundOffset - y1;
			float north = positionY;
			float south = positionY + TILE_SIZE;
			float west = positionX;
			float east = positionX + TILE_SIZE;
			depthBatch.push(texture,
				east - left, top, north,
				east - right, top, north,
				east - right, base, north,
				east - left, base, north,
				u0, v0, u1, v1, tint, tinted, color, mode);
			depthBatch.push(texture,
				west + left, top, south,
				west + right, top, south,
				west + right, base, south,
				west + left, base, south,
				u0, v0, u1, v1, tint, tinted, color, mode);
			depthBatch.push(texture,
				west, top, north + left,
				west, top, north + right,
				west, base, north + right,
				west, base, north + left,
				u0, v0, u1, v1, tint, tinted, color, mode);
			depthBatch.push(texture,
				east, top, south - left,
				east, top, south - right,
				east, base, south - right,
				east, base, south - left,
				u0, v0, u1, v1, tint, tinted, color, mode);
		} else if (spriteMode == SpriteMode.IMPOSTER) {
			float diagonal = (float) Math.sqrt(0.5);
			float left = (x0 - positionX) * diagonal;
			float right = (x1 - positionX) * diagonal;
			float top = positionY + groundOffset - y0;
			float base = positionY + groundOffset - y1;
			depthBatch.push(texture,
				positionX + left, top, positionY + left,
				positionX + right, top, positionY + right,
				positionX + right, base, positionY + right,
				positionX + left, base, positionY + left,
				u0, v0, u1, v1, tint, tinted, color, mode);
			depthBatch.push(texture,
				positionX + left, top, positionY - left,
				positionX + right, top, positionY - right,
				positionX + right, base, positionY - right,
				positionX + left, base, positionY - left,
				u0, v0, u1, v1, tint, tinted, color, mode);
		} else {
			float left = x0 - positionX;
			float right = x1 - positionX;
			float top = positionY + groundOffset - y0;
			float base = positionY + groundOffset - y1;
			float rightX = -forwardY;
			float rightY = forwardX;
			depthBatch.push(texture,
				positionX + rightX * left, top, positionY + rightY * left,
				positionX + rightX * right, top, positionY + rightY * right,
				positionX + rightX * right, base, positionY + rightY * right,
				positionX + rightX * left, base, positionY + rightY * left,
				u0, v0, u1, v1, tint, tinted, color, mode);
		}
	}

	private void flush() {
		orderedBatch.draw();
		if (!depthBatch.textures.isEmpty()) {
			glEnable(GL_DEPTH_TEST);
			depthBatch.draw();
			glDisable(GL_DEPTH_TEST);
		}
	}

	private void prepareTexture(MinicraftImage image) {
		if (image.texture == null) {
			image.texture = new Texture(image.width, image.height, GL_RGBA8);
			textureReferences.add(new TextureReference(image, textureDeletionQueue));
		}
		if (image.dirty) {
			orderedBatch.draw();
			image.texture.upload(image.pixels);
			image.dirty = false;
		}
	}

	@Override
	public void paintGL() {
		Renderer.screen.flush(this);
		double scale = getGraphicsConfiguration().getDefaultTransform().getScaleX();
		int framebufferWidth = (int) Math.round(getWidth() * scale);
		int framebufferHeight = (int) Math.round(getHeight() * scale);
		int w = (int) Math.round(viewWidth * scale);
		int h = (int) Math.round(viewHeight * scale);
		int x = (int) Math.round(viewX * scale);
		int y = framebufferHeight - (int) Math.round(viewY * scale) - h;
		glBindFramebuffer(GL_FRAMEBUFFER, 0);
		glViewport(0, 0, framebufferWidth, framebufferHeight);
		glClearColor(0, 0, 0, 1);
		glClear(GL_COLOR_BUFFER_BIT);
		glBindFramebuffer(GL_READ_FRAMEBUFFER, framebuffer);
		glBlitFramebuffer(0, 0, width, height, x, y, x + w, y + h, GL_COLOR_BUFFER_BIT, GL_NEAREST);
		glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
		swapBuffers();
	}
}
