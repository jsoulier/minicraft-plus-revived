#version 330 core

uniform sampler2D light;
uniform sampler2D position;
uniform bool isFirstPerson;
uniform float alpha;

out vec4 outColor;

void main() {
	ivec2 size = textureSize(light, 0);
	ivec2 fragCoord = ivec2(gl_FragCoord.xy);
	ivec2 lightTexel = ivec2(fragCoord.x, size.y - 1 - fragCoord.y);
	if (isFirstPerson) {
		vec2 worldPosition = texelFetch(position, fragCoord, 0).xy;
		lightTexel = ivec2(floor(worldPosition));
	}
	float brightness = 0.0;
	bool inside = all(greaterThanEqual(lightTexel, ivec2(0))) && all(lessThan(lightTexel, size));
	if (inside) {
		brightness = 1.0 - texelFetch(light, lightTexel, 0).a;
	}
	float shade = mix(1.0, brightness, alpha);
	outColor = vec4(vec3(shade), 1.0);
}
