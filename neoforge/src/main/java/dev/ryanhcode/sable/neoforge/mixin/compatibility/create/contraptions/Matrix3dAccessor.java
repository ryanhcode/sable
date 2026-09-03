package dev.ryanhcode.sable.neoforge.mixin.compatibility.create.contraptions;

import com.simibubi.create.foundation.collision.Matrix3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Mixin into {@link com.simibubi.create.foundation.collision.Matrix3d} to expose matrix elements for sub-level transforms.
 */

@Mixin(Matrix3d.class)
public interface Matrix3dAccessor {
    @Accessor("m00")
    double getM00();

    @Accessor("m00")
    void setM00(double value);

    @Accessor("m01")
    double getM01();

    @Accessor("m01")
    void setM01(double value);

    @Accessor("m02")
    double getM02();

    @Accessor("m02")
    void setM02(double value);

    @Accessor("m10")
    double getM10();

    @Accessor("m10")
    void setM10(double value);

    @Accessor("m11")
    double getM11();

    @Accessor("m11")
    void setM11(double value);

    @Accessor("m12")
    double getM12();

    @Accessor("m12")
    void setM12(double value);

    @Accessor("m20")
    double getM20();

    @Accessor("m20")
    void setM20(double value);

    @Accessor("m21")
    double getM21();

    @Accessor("m21")
    void setM21(double value);

    @Accessor("m22")
    double getM22();

    @Accessor("m22")
    void setM22(double value);

}
