package dev.ryanhcode.sable.render.dynamic_shade;

import foundry.veil.api.client.render.shader.processor.ShaderPreProcessor;
import io.github.ocelot.glslprocessor.api.GlslInjectionPoint;
import io.github.ocelot.glslprocessor.api.GlslParser;
import io.github.ocelot.glslprocessor.api.GlslSyntaxException;
import io.github.ocelot.glslprocessor.api.node.GlslNode;
import io.github.ocelot.glslprocessor.api.node.GlslNodeList;
import io.github.ocelot.glslprocessor.api.node.GlslTree;
import io.github.ocelot.glslprocessor.api.node.expression.GlslAssignmentNode;
import io.github.ocelot.glslprocessor.api.node.function.GlslInvokeFunctionNode;
import io.github.ocelot.glslprocessor.api.node.variable.GlslVariableNode;
import io.github.ocelot.glslprocessor.lib.anarres.cpp.LexerException;
import net.minecraft.client.renderer.RenderType;

import java.io.IOException;
import java.util.List;

public class SableSubLevelFogPreProcessor implements ShaderPreProcessor {

    @Override
    public void modify(final Context ctx, final GlslTree tree) throws GlslSyntaxException, IOException, LexerException {
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

        tree.getBody().add(GlslInjectionPoint.BEFORE_MAIN, GlslParser.parseExpression("uniform float SableEnableSubLevelFog;"));
        tree.getBody().add(GlslInjectionPoint.BEFORE_MAIN, GlslParser.parseExpression("uniform mat4 SableFogMat;"));

        final GlslNodeList mainFunctionBody = tree.mainFunction().orElseThrow().getBody();
        assert mainFunctionBody != null;
        for (int i = 0; i < mainFunctionBody.size(); i++) {
            final GlslNode node = mainFunctionBody.get(i);

            if (node instanceof final GlslAssignmentNode assignmentNode && assignmentNode.getOperand() == GlslAssignmentNode.Operand.EQUAL) {
                // We can be for sure it's an assignment. Now let's check that it's calling
                // fog_distance
                final GlslNode second = assignmentNode.getSecond();

                if (second instanceof final GlslInvokeFunctionNode invokeNode && invokeNode.getHeader() instanceof final GlslVariableNode variableNode && variableNode.getName().equals("fog_distance")) {
                    final List<GlslNode> replacementNodes = GlslParser.parseExpressionList("if (SableEnableSubLevelFog > 0.0) { vertexDistance = fog_distance((SableFogMat * ModelViewMat * vec4(pos, 1.0)).xyz, FogShape); }");

                    mainFunctionBody.add(i + 1, replacementNodes.getFirst());

                    for (int j = 1; j < replacementNodes.size(); j++) {
                        mainFunctionBody.add(i + 1 + j, replacementNodes.get(j));
                    }

                    break;
                }
            }
        }
    }
}
