// Sable Adreno X1-85 Compatible Light Shader
// Replaces Veil's uniform float[6] VeilBlockFaceBrightness with scalar uniforms
// to avoid 276ms stalls on Qualcomm's OpenGL-to-D3D12 translation layer

#define MINECRAFT_LIGHT_POWER (0.6)
#define MINECRAFT_AMBIENT_LIGHT (0.4)

// Scalar uniforms instead of array - avoids driver JIT compilation stalls
uniform float VeilBlockFaceBrightness_0;  // DOWN
uniform float VeilBlockFaceBrightness_1;  // UP
uniform float VeilBlockFaceBrightness_2;  // NORTH
uniform float VeilBlockFaceBrightness_3;  // SOUTH
uniform float VeilBlockFaceBrightness_4;  // WEST
uniform float VeilBlockFaceBrightness_5;  // EAST

vec4 minecraft_mix_light(vec3 lightDir0, vec3 lightDir1, vec3 normal, vec4 color) {
    lightDir0 = normalize(lightDir0);
    lightDir1 = normalize(lightDir1);
    float light0 = max(0.0, dot(lightDir0, normal));
    float light1 = max(0.0, dot(lightDir1, normal));
    float lightAccum = min(1.0, (light0 + light1) * MINECRAFT_LIGHT_POWER + MINECRAFT_AMBIENT_LIGHT);
    return vec4(color.rgb * lightAccum, color.a);
}

vec4 minecraft_mix_light(vec3 lightDir0, vec3 lightDir1, vec3 normal) {
    lightDir0 = normalize(lightDir0);
    lightDir1 = normalize(lightDir1);
    float light0 = max(0.0, dot(lightDir0, normal));
    float light1 = max(0.0, dot(lightDir1, normal));
    float lightAccum = min(1.0, (light0 + light1) * MINECRAFT_LIGHT_POWER + MINECRAFT_AMBIENT_LIGHT);
    return vec4(lightAccum, lightAccum, lightAccum, 1.0);
}

vec2 minecraft_sample_lightmap_coords(ivec2 uv) {
    return clamp(uv / 256.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0));
}

vec4 minecraft_sample_lightmap(sampler2D lightMap, ivec2 uv) {
    return texture(lightMap, minecraft_sample_lightmap_coords(uv));
}

float block_brightness(vec3 worldNormal) {
    // Use scalar uniforms for Adreno compatibility
    float darkFromD = pow(clamp(-worldNormal.y, 0.0, 1.0), 3) * VeilBlockFaceBrightness_0;
    float darkFromU = pow(clamp(worldNormal.y, 0.0, 1.0), 3) * VeilBlockFaceBrightness_1;
    float darkFromN = pow(clamp(-worldNormal.z, 0.0, 1.0), 2) * VeilBlockFaceBrightness_2;
    float darkFromS = pow(clamp(worldNormal.z, 0.0, 1.0), 2) * VeilBlockFaceBrightness_3;
    float darkFromW = pow(clamp(-worldNormal.x, 0.0, 1.0), 2) * VeilBlockFaceBrightness_4;
    float darkFromE = pow(clamp(worldNormal.x, 0.0, 1.0), 2) * VeilBlockFaceBrightness_5;

    return (darkFromD + darkFromU + darkFromN + darkFromS + darkFromW + darkFromE);
}

float attenuate_no_cusp(float distance, float radius) {
    float s = distance / radius;

    if (s >= 1.0) {
        return 0.0;
    }

    float oneMinusS = 1.0 - s;
    return oneMinusS * oneMinusS * oneMinusS;
}
