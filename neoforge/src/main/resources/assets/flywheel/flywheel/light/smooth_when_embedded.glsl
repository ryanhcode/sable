void flw_shaderLight() {
    #ifdef FLW_EMBEDDED
    ivec3 renderOrigin = flw_renderOrigin;

    if (flw_vertexLightingSceneId != 0) {
        renderOrigin = ivec3(0);
    }

    FlwLightAo light;
    if (flw_light(flw_vertexLightingSceneId, flw_vertexLightingPos.xyz, flw_vertexNormal, renderOrigin, light)) {
        flw_fragLight = max(flw_fragLight, light.light);

        if (flw_material.ambientOcclusion) {
            flw_fragColor.rgb *= light.ao;
        }
    }

    flw_fragLight.y *= flw_skyLightScale;
    #endif
}
