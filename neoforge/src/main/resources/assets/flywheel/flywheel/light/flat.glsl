void flw_shaderLight() {
    vec2 embeddedLight;

    uint sceneId = 0;
    vec4 vertexLightingPos;
    ivec3 renderOrigin;

    #ifdef FLW_EMBEDDED
    renderOrigin = flw_renderOrigin;
    sceneId = flw_vertexLightingSceneId;
    vertexLightingPos = flw_vertexLightingPos;

    if (sceneId != 0) {
        renderOrigin = ivec3(0);
    }
    #else
    renderOrigin = flw_renderOrigin;
    vertexLightingPos = flw_vertexPos;
    #endif

    if (flw_lightFetch(sceneId, ivec3(floor(vertexLightingPos.xyz)) + renderOrigin, embeddedLight)) {
        flw_fragLight = max(flw_fragLight, embeddedLight);
    }

    #ifdef FLW_EMBEDDED
    flw_fragLight.y *= flw_skyLightScale;
    #endif
}
