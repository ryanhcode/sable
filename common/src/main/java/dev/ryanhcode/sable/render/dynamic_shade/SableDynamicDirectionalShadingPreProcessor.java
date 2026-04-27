package dev.ryanhcode.sable.render.dynamic_shade;

import dev.ryanhcode.sable.Sable;
import foundry.veil.Veil;
import foundry.veil.api.client.render.shader.processor.ShaderPreProcessor;
import io.github.ocelot.glslprocessor.api.GlslInjectionPoint;
import io.github.ocelot.glslprocessor.api.GlslParser;
import io.github.ocelot.glslprocessor.api.GlslSyntaxException;
import io.github.ocelot.glslprocessor.api.node.GlslNode;
import io.github.ocelot.glslprocessor.api.node.GlslNodeList;
import io.github.ocelot.glslprocessor.api.node.GlslTree;
import io.github.ocelot.glslprocessor.api.node.expression.GlslAssignmentNode;
import io.github.ocelot.glslprocessor.api.node.expression.GlslOperationNode;
import io.github.ocelot.glslprocessor.api.node.function.GlslInvokeFunctionNode;
import io.github.ocelot.glslprocessor.api.node.variable.GlslVariableNode;
import io.github.ocelot.glslprocessor.lib.anarres.cpp.LexerException;
import net.minecraft.client.renderer.RenderType;

import java.io.IOException;
import java.util.List;

public class SableDynamicDirectionalShadingPreProcessor implements ShaderPreProcessor {

    @Override
    public void modify(final Context ctx, final GlslTree tree) throws GlslSyntaxException, IOException, LexerException {
        if (!SableDynamicDirectionalShading.isEnabled()) {
            return;
        }

        if (ctx instanceof final MinecraftContext minecraftContext) {
            final List<RenderType> renderTypes = RenderType.chunkBufferLayers();

            boolean anyMatches = false;

            for (final RenderType renderType : renderTypes) {
                if (ctx.isVertex() && minecraftContext.shaderInstance().equals("rendertype_%s".formatted(renderType.name))) {
                    anyMatches = true;
                }
            }

            if (!anyMatches) {
                return;
            }
        } else {
            return;
        }

        // ADRENO X1-85 COMPATIBILITY FIX
        //
        // The original Veil light.glsl uses:
        //   uniform float[6] VeilBlockFaceBrightness;
        //   with VeilBlockFaceBrightness[i] array access
        //
        // On Qualcomm Adreno X1-85 (Windows on ARM), array uniforms cause 276ms stalls
        // because the OpenGL-to-D3D12 translation layer has to JIT recompile the shader.
        //
        // SOLUTION: Inline the light.glsl content with scalar uniforms instead of arrays:
        //   uniform float VeilBlockFaceBrightness_0..5;
        //   This avoids the driver stall.
        //
        // Note: We use SOURCE to include our patched light.glsl
        ctx.include(tree, Sable.sablePath("shaders/include/light_adreno_compatible"), IncludeOverloadStrategy.SOURCE);

        tree.getBody().add(GlslInjectionPoint.BEFORE_MAIN, GlslParser.parseExpression("uniform float SableEnableNormalLighting;"));
        tree.getBody().add(GlslInjectionPoint.BEFORE_MAIN, GlslParser.parseExpression("uniform float SableSkyLightScale;"));

        // Add NormalMat if we're lacking it
        if (tree.field("NormalMat").isEmpty()) {
            tree.getBody().add(GlslInjectionPoint.BEFORE_MAIN, GlslParser.parseExpression("uniform mat3 NormalMat;"));
        }

        final List<GlslNode> body = tree.mainFunction().orElseThrow().getBody();
        body.add(GlslParser.parseExpression("vertexColor.rgb *= mix(vec3(1.0), vec3(block_brightness(inverse(NormalMat) * (ModelViewMat * vec4(Normal, 0.0)).xyz)), SableEnableNormalLighting);"));

        final GlslNodeList mainFunctionBody = tree.mainFunction().orElseThrow().getBody();
        assert mainFunctionBody != null;
        for (int i = 0; i < mainFunctionBody.size(); i++) {
            final GlslNode node = mainFunctionBody.get(i);

            if (node instanceof final GlslAssignmentNode assignmentNode && assignmentNode.getOperand() == GlslAssignmentNode.Operand.EQUAL) {
                // We can be for sure it's an assignment. Now let's check that it's calling
                // minecraft_sample_lightmap
                final GlslNode second = assignmentNode.getSecond();

                if (second instanceof final GlslOperationNode operationNode && operationNode.getOperand() == GlslOperationNode.Operand.MULTIPLY) {
                    if (operationNode.getSecond() instanceof final GlslInvokeFunctionNode invokeNode && invokeNode.getHeader() instanceof final GlslVariableNode variableNode && variableNode.getName().equals("minecraft_sample_lightmap")) {
                        final List<GlslNode> replacementNodes = GlslParser.parseExpressionList("vertexColor = Color * minecraft_sample_lightmap(Sampler2, ivec2(UV2 * vec2(1.0, SableSkyLightScale)));");

                        mainFunctionBody.set(i, replacementNodes.getFirst());

                        for (int j = 1; j < replacementNodes.size(); j++) {
                            mainFunctionBody.add(i + j, replacementNodes.get(j));
                        }

                        break;
                    }
                }
            }
        }
    }
}
