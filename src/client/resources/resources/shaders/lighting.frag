#version 330 core

uniform sampler2D lights;
uniform sampler2D position;
uniform int lightCount;
uniform bool isFirstPerson;
uniform float alpha;
uniform ivec2 ditherOffset;
uniform int screenHeight;

out vec4 outColor;

const int DITHER[16] = int[16](0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5);

void main() {
	ivec2 fragCoord = ivec2(gl_FragCoord.xy);
	vec2 point = vec2(fragCoord.x, screenHeight - 1 - fragCoord.y) + 0.5;
	if (isFirstPerson) {
		point = texelFetch(position, fragCoord, 0).xy;
	}
	float brightness = 0.0;
	for (int i = 0; i < lightCount; i++) {
		vec3 light = texelFetch(lights, ivec2(i, 0), 0).xyz;
		float lightDistance = length(point - light.xy);
		if (lightDistance < light.z) {
			float fraction = lightDistance / light.z;
			float strength = 1.0 - fraction * fraction * fraction * fraction;
			brightness = 1.0 - (1.0 - brightness) * (1.0 - strength);
		}
	}
	ivec2 pixel = ivec2(floor(point));
	int grade = int(brightness * 255.0);
	int threshold = DITHER[((pixel.x + ditherOffset.x) & 3) + ((pixel.y + ditherOffset.y) & 3) * 4];
	float darkness = (255.0 - float(grade)) / 255.0;
	if (grade / 10 > threshold) {
		darkness = 0.0;
	}
	float shade = 1.0 - darkness * alpha;
	outColor = vec4(vec3(shade), 1.0);
}
