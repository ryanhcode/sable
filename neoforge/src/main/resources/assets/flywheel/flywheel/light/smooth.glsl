void flw_shaderLight() {
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

    FlwLightAo light;
    if (flw_light(sceneId, vertexLightingPos.xyz, flw_vertexNormal, renderOrigin, light)) {
        flw_fragLight = max(flw_fragLight, light.light);

        if (flw_material.ambientOcclusion) {
            flw_fragColor.rgb *= light.ao;
        }
    }

    #ifdef FLW_EMBEDDED
    flw_fragLight.y *= flw_skyLightScale;
    #endif
}
