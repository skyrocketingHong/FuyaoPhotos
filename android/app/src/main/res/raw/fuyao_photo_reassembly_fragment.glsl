#version 300 es
precision highp float;

in vec2 textureUV;
uniform sampler2D sourceTexture;
uniform sampler2D targetTexture;
uniform float colorProgress;
out vec4 fragColor;

void main() {
    fragColor = mix(texture(sourceTexture, textureUV), texture(targetTexture, textureUV), colorProgress);
}
