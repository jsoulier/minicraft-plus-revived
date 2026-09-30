#version 330 core

const uint MODE_TEXTURE = 0u;
const uint MODE_MASK = 1u;
const uint MODE_SOLID = 2u;

in vec3 fragPosition;
in vec2 fragTexel;
flat in vec3 fragTint;
flat in uint fragTinted;
flat in vec3 fragColor;
flat in uint fragMode;

uniform sampler2D sheet;

layout(location = 0) out vec4 outColor;
layout(location = 1) out vec2 outPosition;

void main() {
	outPosition = fragPosition.xz;
	if (fragMode == MODE_SOLID) {
		outColor = vec4(fragColor, 1.0);
		return;
	}
	ivec2 size = textureSize(sheet, 0);
	ivec2 sheetTexel = clamp(ivec2(floor(fragTexel)), ivec2(0), size - 1);
	vec4 texelColor = texelFetch(sheet, sheetTexel, 0);
	if (texelColor.a == 0.0) {
		discard;
	}
	bool white = texelColor.rgb == vec3(1.0);
	if (fragTinted == 1u && white) {
		outColor = vec4(fragTint, 1.0);
	} else if (fragMode == MODE_MASK) {
		outColor = vec4(fragColor, 1.0);
	} else {
		outColor = vec4(texelColor.rgb, 1.0);
	}
}
