#version 330 core

uniform vec2 resolution;
uniform float tanHalfFov;
uniform vec2 forward;
uniform float time;
uniform vec2 cameraPosition;
uniform float windTime;

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

const float CLOUD_HEIGHT = 96.0;
const float CLOUD_CELL = 24.0;
const float CLOUD_COVERAGE = 0.4;
const vec2 WIND = vec2(6.0, 2.0);
const vec3 DAY_CLOUD = vec3(0.96, 0.97, 1.0);
const vec3 NIGHT_CLOUD = vec3(0.12, 0.13, 0.2);
const vec3 SUNSET_CLOUD = vec3(1.0, 0.66, 0.5);

// https://www.shadertoy.com/view/4sfGzS
float hash(vec3 p)
{
	p  = fract( p*0.3183099+.1 );
	p *= 17.0;
	return fract( p.x*p.y*p.z*(p.x+p.y+p.z) );
}

// https://www.shadertoy.com/view/lsf3WH
float hash(vec2 p)
{
	p = 50.0*fract( p*0.3183099 + vec2(0.71,0.113));
	return -1.0+2.0*fract( p.x*p.y*(p.x+p.y) );
}

// https://www.shadertoy.com/view/lsf3WH
float noise( in vec2 p )
{
	vec2 i = floor( p );
	vec2 f = fract( p );

	vec2 u = f*f*(3.0-2.0*f);

	return mix( mix( hash( i + vec2(0.0,0.0) ),
	                 hash( i + vec2(1.0,0.0) ), u.x),
	            mix( hash( i + vec2(0.0,1.0) ),
	                 hash( i + vec2(1.0,1.0) ), u.x), u.y);
}

float band(float value, float steps) {
	return floor(value * steps) / steps;
}

void main() {
	vec2 ndc = gl_FragCoord.xy / resolution * 2.0 - 1.0;
	float screenAspect = resolution.x / resolution.y;
	vec3 cameraForward = vec3(forward.x, 0.0, forward.y);
	vec3 cameraRight = vec3(-forward.y, 0.0, forward.x);
	vec3 cameraUp = vec3(0.0, 1.0, 0.0);
	vec3 viewRay = normalize(cameraForward + cameraRight * ndc.x * tanHalfFov * screenAspect + cameraUp * ndc.y * tanHalfFov);
	float sunPhase = (time - SUNRISE) * TAU;
	vec3 sunDirection = normalize(vec3(cos(sunPhase), sin(sunPhase), 0.25));
	vec3 moonDirection = -sunDirection;
	float dayAmount = smoothstep(-0.15, 0.25, sunDirection.y);
	float viewElevation = clamp(viewRay.y, 0.0, 1.0);
	float skyGradient = band(sqrt(viewElevation), BANDS);
	vec3 daySkyColor = mix(DAY_HORIZON, DAY_ZENITH, skyGradient);
	vec3 nightSkyColor = mix(NIGHT_HORIZON, NIGHT_ZENITH, skyGradient);
	vec3 skyColor = mix(nightSkyColor, daySkyColor, dayAmount);
	float sunFacing = max(dot(viewRay, sunDirection), 0.0);
	float twilightAmount = 1.0 - smoothstep(0.0, 0.35, abs(sunDirection.y));
	float horizonAmount = 1.0 - smoothstep(0.0, 0.45, viewElevation);
	float sunsetAmount = band(twilightAmount * horizonAmount * (0.35 + 0.65 * sunFacing), BANDS);
	skyColor = mix(skyColor, SUNSET, sunsetAmount);
	float sunGlow = band(pow(sunFacing, 48.0), GLOW_BANDS);
	skyColor += SUN * sunGlow * 0.35 * dayAmount;
	float sunDiscAlpha = step(0.9992, dot(viewRay, sunDirection));
	skyColor = mix(skyColor, SUN, sunDiscAlpha);
	float moonDiscAlpha = step(0.99945, dot(viewRay, moonDirection));
	skyColor = mix(skyColor, MOON, moonDiscAlpha * (1.0 - dayAmount));
	float starAlpha = step(0.996, hash(floor(viewRay * 120.0)));
	skyColor += vec3(starAlpha * (1.0 - dayAmount) * smoothstep(0.02, 0.2, viewRay.y));
	float cloudRayHeight = max(viewRay.y, 0.001);
	vec2 cloudPoint = cameraPosition + viewRay.xz / cloudRayHeight * CLOUD_HEIGHT + WIND * windTime;
	vec2 cloudCell = floor(cloudPoint / CLOUD_CELL);
	float largeCloudNoise = noise(cloudCell / 6.0) * 0.5 + 0.5;
	float smallCloudNoise = noise(cloudCell / 2.5) * 0.5 + 0.5;
	float cloudDensity = largeCloudNoise * 0.65 + smallCloudNoise * 0.35;
	float cloudHorizonFade = step(0.08, viewRay.y);
	float cloudAlpha = step(1.0 - CLOUD_COVERAGE, cloudDensity) * cloudHorizonFade;
	vec3 cloudColor = mix(NIGHT_CLOUD, DAY_CLOUD, band(dayAmount, BANDS));
	cloudColor = mix(cloudColor, SUNSET_CLOUD, band(twilightAmount, BANDS) * 0.6);
	skyColor = mix(skyColor, cloudColor, cloudAlpha * 0.9);
	outColor = vec4(skyColor, 1.0);
}
