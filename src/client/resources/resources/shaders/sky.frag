#version 330 core

uniform vec2 resolution;
uniform float tanHalfFov;
uniform vec2 forward;
uniform float time;

out vec4 outColor;

const float TAU = 6.28318530718;
const float SUNRISE = 0.125;
const float BANDS = 6.0;
const float GLOW_BANDS = 3.0;

const vec3 DAY_ZENITH = vec3(0.24, 0.47, 0.86);
const vec3 DAY_HORIZON = vec3(0.62, 0.78, 0.94);
const vec3 NIGHT_ZENITH = vec3(0.01, 0.01, 0.05);
const vec3 NIGHT_HORIZON = vec3(0.04, 0.06, 0.14);
const vec3 SUNSET = vec3(0.96, 0.48, 0.22);
const vec3 SUN = vec3(1.0, 0.95, 0.78);
const vec3 MOON = vec3(0.85, 0.88, 0.95);

float hash(vec3 cell) {
	vec3 p = fract(cell * 0.3183099 + 0.1);
	p *= 17.0;
	return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float band(float value, float steps) {
	return floor(value * steps) / steps;
}

void main() {
	vec2 ndc = gl_FragCoord.xy / resolution * 2.0 - 1.0;
	float aspect = resolution.x / resolution.y;
	vec3 forward3 = vec3(forward.x, 0.0, forward.y);
	vec3 right = vec3(-forward.y, 0.0, forward.x);
	vec3 up = vec3(0.0, 1.0, 0.0);
	vec3 ray = normalize(forward3 + right * ndc.x * tanHalfFov * aspect + up * ndc.y * tanHalfFov);
	float phase = (time - SUNRISE) * TAU;
	vec3 sun = normalize(vec3(cos(phase), sin(phase), 0.25));
	vec3 moon = -sun;
	float day = smoothstep(-0.15, 0.25, sun.y);
	float elevation = clamp(ray.y, 0.0, 1.0);
	float gradient = band(sqrt(elevation), BANDS);
	vec3 dayColor = mix(DAY_HORIZON, DAY_ZENITH, gradient);
	vec3 nightColor = mix(NIGHT_HORIZON, NIGHT_ZENITH, gradient);
	vec3 color = mix(nightColor, dayColor, day);
	float sunDot = max(dot(ray, sun), 0.0);
	float twilight = 1.0 - smoothstep(0.0, 0.35, abs(sun.y));
	float horizon = 1.0 - smoothstep(0.0, 0.45, elevation);
	float sunset = band(twilight * horizon * (0.35 + 0.65 * sunDot), BANDS);
	color = mix(color, SUNSET, sunset);
	float glow = band(pow(sunDot, 48.0), GLOW_BANDS);
	color += SUN * glow * 0.35 * day;
	float sunAlpha = step(0.9992, dot(ray, sun));
	color = mix(color, SUN, sunAlpha);
	float moonAlpha = step(0.99945, dot(ray, moon));
	color = mix(color, MOON, moonAlpha * (1.0 - day));
	float star = step(0.996, hash(floor(ray * 120.0)));
	color += vec3(star * (1.0 - day) * smoothstep(0.02, 0.2, ray.y));
	outColor = vec4(color, 1.0);
}
