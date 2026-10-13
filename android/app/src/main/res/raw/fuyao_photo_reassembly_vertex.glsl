#version 300 es
// Fuyao Photos close-to-placeholder transition; separate from the pinned Nagram dust shader.
precision highp float;
precision highp int;

uniform vec2 viewportSize;
uniform vec4 sourceRect;
uniform vec4 targetRect;
uniform vec2 gridSize;
uniform float travel;
uniform float scatter;
uniform float scatterRadius;
uniform float cellScale;

out vec2 textureUV;

uint particleHash(uint index) {
    uint h = index * 747796405u + 2891336453u;
    h = (h ^ (h >> 16u)) * 2246822519u;
    return h ^ (h >> 13u);
}

void main() {
    const vec2 corners[6] = vec2[6](
        vec2(-0.5, -0.5), vec2(0.5, -0.5), vec2(-0.5, 0.5),
        vec2(-0.5, 0.5), vec2(0.5, -0.5), vec2(0.5, 0.5)
    );
    float index = float(gl_InstanceID);
    vec2 cell = vec2(mod(index, gridSize.x), floor(index / gridSize.x));
    vec2 centerUV = (cell + 0.5) / gridSize;
    vec2 local = corners[gl_VertexID];
    uint hash = particleHash(uint(gl_InstanceID));
    float angle = 6.28318530718 * float(hash & 65535u) / 65535.0;
    float distance = 0.35 + 0.65 * float((hash >> 16u) & 65535u) / 65535.0;
    vec2 drift = vec2(cos(angle), sin(angle)) * distance * scatterRadius * scatter;
    vec2 center = mix(sourceRect.xy + centerUV * sourceRect.zw,
        targetRect.xy + centerUV * targetRect.zw, travel) + drift;
    vec2 particleSize = mix(sourceRect.zw, targetRect.zw, travel) / gridSize * cellScale;
    vec2 position = center + local * particleSize;
    textureUV = (cell + local + 0.5) / gridSize;
    gl_Position = vec4(position.x / viewportSize.x * 2.0 - 1.0,
        1.0 - position.y / viewportSize.y * 2.0, 0.0, 1.0);
}
