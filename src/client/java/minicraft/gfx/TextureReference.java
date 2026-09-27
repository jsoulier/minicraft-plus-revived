package minicraft.gfx;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;

class TextureReference extends PhantomReference<MinicraftImage> {
	final Texture texture;

	TextureReference(MinicraftImage image, ReferenceQueue<MinicraftImage> queue) {
		super(image, queue);
		texture = image.texture;
	}
}
