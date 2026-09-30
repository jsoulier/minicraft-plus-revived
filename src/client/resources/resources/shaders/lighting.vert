#version 330 core

const vec2 corners[3] = vec2[3](
	vec2(-1.0, -1.0),
	vec2(3.0, -1.0),
	vec2(-1.0, 3.0)
);

void main() {
	gl_Position = vec4(corners[gl_VertexID], 0.0, 1.0);
}
