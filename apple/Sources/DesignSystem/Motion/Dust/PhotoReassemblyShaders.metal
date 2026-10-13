#include <metal_stdlib>
using namespace metal;

// Fuyao Photos reassembly; independent of the pinned Telegram dust shaders.
struct PhotoReassemblyUniforms {
    float4 sourceRect;
    float4 targetRect;
    float4 canvasAndGrid;
    float4 animation;
    float4 colorAndPadding;
};

struct PhotoReassemblyVertex {
    float4 position [[position]];
    float2 uv;
    float colorFraction;
};

constant float2 reassemblyCorners[6] = {
    float2(0, 0), float2(1, 0), float2(0, 1),
    float2(1, 0), float2(1, 1), float2(0, 1)
};

float2 reassemblyOffset(uint index) {
    uint hash = index * 747796405u + 2891336453u;
    hash = (hash ^ (hash >> 16)) * 2246822519u;
    hash ^= hash >> 13;
    float angle = 2.0f * M_PI_F * float(hash & 65535u) / 65535.0f;
    float radius = 0.35f + 0.65f * float((hash >> 16) & 65535u) / 65535.0f;
    return float2(cos(angle), sin(angle)) * radius;
}

vertex PhotoReassemblyVertex photoReassemblyVertex(
    constant PhotoReassemblyUniforms &uniforms [[buffer(0)]],
    uint vertexID [[vertex_id]],
    uint particleID [[instance_id]]
) {
    float2 grid = uniforms.canvasAndGrid.zw;
    uint columns = uint(grid.x);
    float2 cell = float2(particleID % columns, particleID / columns);
    float2 corner = reassemblyCorners[vertexID];
    float2 centerUV = (cell + 0.5f) / grid;
    float t = clamp(uniforms.animation.x, 0.0f, 1.0f);
    float travel = smoothstep(uniforms.animation.z, 1.0f, t);
    float spread = t <= 0.0f || t >= 1.0f ? 0.0f : sin(M_PI_F * t);
    float2 sourceCenter = uniforms.sourceRect.xy + centerUV * uniforms.sourceRect.zw;
    float2 targetCenter = uniforms.targetRect.xy + centerUV * uniforms.targetRect.zw;
    float2 center = mix(sourceCenter, targetCenter, travel)
        + reassemblyOffset(particleID) * uniforms.animation.y * spread;
    float2 size = mix(uniforms.sourceRect.zw, uniforms.targetRect.zw, travel) / grid
        * (1.0f - 0.55f * spread);
    float2 position = center + (corner - 0.5f) * size;
    PhotoReassemblyVertex out;
    out.position = float4(position.x / uniforms.canvasAndGrid.x * 2.0f - 1.0f,
                          1.0f - position.y / uniforms.canvasAndGrid.y * 2.0f, 0, 1);
    out.uv = (cell + corner) / grid;
    out.colorFraction = smoothstep(uniforms.animation.w, uniforms.colorAndPadding.x, t);
    return out;
}

fragment half4 photoReassemblyFragment(
    PhotoReassemblyVertex in [[stage_in]],
    texture2d<half, access::sample> sourceTexture [[texture(0)]],
    texture2d<half, access::sample> targetTexture [[texture(1)]]
) {
    constexpr sampler imageSampler(coord::normalized, address::clamp_to_edge, filter::linear);
    half4 source = sourceTexture.sample(imageSampler, in.uv);
    half4 target = targetTexture.sample(imageSampler, in.uv);
    return mix(source, target, half(in.colorFraction));
}
