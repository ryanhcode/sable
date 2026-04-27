package dev.ryanhcode.sable.compatibility;

import dev.ryanhcode.sable.mixinterface.compatibility.iris.ExtendedShaderExtension;
import net.minecraft.client.renderer.ShaderInstance;

public class SableIrisCompat {

    public static void refreshModelMatrices(final ShaderInstance shader) {
        if (shader instanceof final ExtendedShaderExtension ext) {
            ext.sable$refreshModelMatrices();
        }
    }

}
