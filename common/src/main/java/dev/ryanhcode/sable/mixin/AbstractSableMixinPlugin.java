package dev.ryanhcode.sable.mixin;

import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.annotation.MixinModVersionConstraint;
import dev.ryanhcode.sable.platform.SableLoaderPlatform;
import foundry.veil.Veil;
import foundry.veil.api.compat.SodiumCompat;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.util.Annotations;

import java.util.List;
import java.util.Set;

public abstract class AbstractSableMixinPlugin implements IMixinConfigPlugin {
    public static final Logger LOGGER = LogUtils.getLogger();
    private final Object2BooleanMap<String> modLoadedCache = new Object2BooleanOpenHashMap<>();
    private boolean sodiumPresent;

    @Override
    public void onLoad(final String mixinPackage) {
        this.sodiumPresent = SodiumCompat.isLoaded();

        LOGGER.info("Using {} renderer mixins", this.sodiumPresent ? "Sodium" : "Vanilla");
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName) {
        // TODO: Housekeeping
        if (mixinClassName.startsWith("dev.ryanhcode.sable.mixin.sublevel_render.impl")) {
            return this.sodiumPresent ? mixinClassName.startsWith("dev.ryanhcode.sable.mixin.sublevel_render.impl.sodium") : mixinClassName.startsWith("dev.ryanhcode.sable.mixin.sublevel_render.impl.vanilla");
        }

        if (mixinClassName.startsWith("dev.ryanhcode.sable.mixin.compatibility.") ||
                mixinClassName.startsWith("dev.ryanhcode.sable.neoforge.mixin.compatibility.") ||
                mixinClassName.startsWith("dev.ryanhcode.sable.fabric.mixin.compatibility.")
        ) {
            final String[] parts = mixinClassName.split("\\.");
            if (parts.length < 5) {
                return true;
            }

            final String modId = parts[3].equals("mixin") ? parts[5] : parts[6];
            
            final boolean isModLoaded = this.modLoadedCache.computeIfAbsent(modId, x -> Veil.platform().isModLoaded(modId));
            return isModLoaded && MixinConstraints.handleClassAnnotation(mixinClassName, modId);
        }

        return true;
    }

    @Override
    public void acceptTargets(final Set<String> myTargets, final Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(final String targetClassName, final ClassNode targetClass, final String mixinClassName, final IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(final String targetClassName, final ClassNode targetClass, final String mixinClassName, final IMixinInfo mixinInfo) {
    }
    
    // Constraint handling
	static class MixinConstraints {
        private static final Object2ObjectMap<String, String> MOD_VERSION_CACHE = new Object2ObjectOpenHashMap<>();
        
        // Looks for if there's a @MixinModVersionConstraint annotation which declares a range for when a mixin should be loaded
        static boolean handleClassAnnotation(final String mixinClassName, final String modId) {
            try {
                final List<AnnotationNode> nodes = MixinService.getService().getBytecodeProvider().getClassNode(mixinClassName).visibleAnnotations;
                if (nodes == null)
                    return true;
                
                return shouldApply(nodes, modId);
            } catch (final Throwable e) {
                throw new RuntimeException(e);
            }
        }

        static boolean shouldApply(final List<AnnotationNode> nodes, final String modId) throws InvalidVersionSpecificationException {
            for (final AnnotationNode node : nodes) {
                if (node.desc.equals(Type.getDescriptor(MixinModVersionConstraint.class))) {
                    final String range = Annotations.getValue(node, "value");
                    final VersionRange versionRange = VersionRange.createFromVersionSpec(range);

                    final String modVersion = MOD_VERSION_CACHE.computeIfAbsent(modId, x -> SableLoaderPlatform.INSTANCE.getModVersion(modId));
                    final ArtifactVersion artifactVersion = new DefaultArtifactVersion(modVersion);

                    return versionRange.containsVersion(artifactVersion);
                }
            }
            
            return true;
        }
    }
}
