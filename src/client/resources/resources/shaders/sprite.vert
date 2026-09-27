#version 330 core

layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec2 inTexel;
layout(location = 2) in vec4 inTint;
layout(location = 3) in uint inTinted;
layout(location = 4) in vec4 inColor;
layout(location = 5) in uint inMode;

uniform mat4 viewProjection;

out vec3 fragPosition;
out vec2 fragTexel;
flat out vec3 fragTint;
flat out uint fragTinted;
flat out vec3 fragColor;
flat out uint fragMode;

void main() {
	fragPosition = inPosition;
	fragTexel = inTexel;
	fragTint = inTint.rgb;
	fragTinted = inTinted;
	fragColor = inColor.rgb;
	fragMode = inMode;
	gl_Position = viewProjection * vec4(inPosition, 1.0);
}
