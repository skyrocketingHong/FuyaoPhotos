#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Adapted from Motionary's glass cap; see LICENSES/Motionary-MIT.txt.
[[ stitchable ]]
half4 fuyaoColorLens(float2 position, SwiftUI::Layer layer, float2 size) {
    float radius = max(size.x * 0.5, 1.0);
    float2 center = size * 0.5;
    float2 d = position - center;
    float distance = length(d);
    float t = min(distance / radius, 1.0);
    float z = sqrt(max(1.0 - t * t, 0.0));
    float2 direction = distance > 0.001 ? d / distance : float2(0.0);
    float core = distance * mix(0.5, 1.0, t * t);
    float rim = (1.0 - z) * radius * 0.28;
    float spread = 0.12 * (1.0 - z);
    half4 g = layer.sample(center + direction * (core - rim));
    half4 r = layer.sample(center + direction * (core - rim * (1.0 - spread)));
    half4 b = layer.sample(center + direction * (core - rim * (1.0 + spread)));
    half4 lens = half4(r.r, g.g, b.b, max(g.a, max(r.a, b.a)));
    float3 normal = normalize(float3(d / radius, z));
    float specular = pow(max(dot(normal, normalize(float3(-0.45, -0.6, 0.66))), 0.0), 36.0);
    lens.rgb = lens.rgb * half(0.9 + 0.1 * z) + half(specular * 0.5) * lens.a;
    return lens;
}
