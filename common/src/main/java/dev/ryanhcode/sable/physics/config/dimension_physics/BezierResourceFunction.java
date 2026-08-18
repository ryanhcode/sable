package dev.ryanhcode.sable.physics.config.dimension_physics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.ryanhcode.sable.util.SableCodecUtil;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

public class BezierResourceFunction {
    public static final Codec<BezierResourceFunction> CODEC = BezierPoint.CODEC.listOf().flatXmap(
            (bezierPoints -> DataResult.success(new BezierResourceFunction(bezierPoints))),
            (bezierResourceFunction -> DataResult.success(bezierResourceFunction.getPoints()))
    );

    public static final StreamCodec<ByteBuf, BezierResourceFunction> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, BezierPoint.STREAM_CODEC), BezierResourceFunction::getPoints,
            BezierResourceFunction::new
    );

    private final List<BezierPoint> points;

    public BezierResourceFunction(final List<BezierPoint> points) {
        this.points = points;
    }

    public BezierResourceFunction() {
        this.points = new ArrayList<>();
    }

    public List<BezierPoint> getPoints() {
        return this.points;
    }

    public void addPoint(final BezierPoint point) {
        this.points.add(point);
    }

    public int pointSize() {
        return this.points.size();
    }

    public double evaluateFunction(final double position) {
        if (this.points.isEmpty())
            return 1;
        if (this.points.size() == 1)
            return this.points.get(0).value;
        int index = -1;
        for (final BezierPoint point : this.points) {
            if (position < point.altitude())
                break;
            index++;
        }
        if (index == -1)
            return this.points.get(0).value;
        if (index >= this.points.size() - 1)
            return this.points.get(this.points.size() - 1).value;

        final BezierPoint point1 = this.points.get(index);
        final BezierPoint point2 = this.points.get(index + 1);

        final double relativeX = point2.altitude - point1.altitude;
        final double relativeY = point2.value - point1.value;
        final double slope1 = point1.slope;
        final double slope2 = point2.slope;
        final double t = (position - point1.altitude) / relativeX;

        final double cubicFactor = (slope1 + slope2) * relativeX - 2 * relativeY;
        final double quadraticFactor = 3 * relativeY - (2 * slope1 + slope2) * relativeX;
        final double linearFactor = relativeX * slope1;

        return Math.max(((cubicFactor * t + quadraticFactor) * t + linearFactor) * t + point1.value, 0);
    }

    public record BezierPoint(double altitude, double value, double slope) {
        public static final Codec<BezierPoint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.DOUBLE.fieldOf("altitude").forGetter(BezierPoint::altitude),
                SableCodecUtil.positiveDouble(true).fieldOf("value").forGetter(BezierPoint::value),
                Codec.DOUBLE.fieldOf("slope").forGetter(BezierPoint::slope)
        ).apply(instance, BezierPoint::new));

        public static final StreamCodec<ByteBuf, BezierPoint> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, BezierPoint::altitude,
                ByteBufCodecs.DOUBLE, BezierPoint::value,
                ByteBufCodecs.DOUBLE, BezierPoint::slope,
                BezierPoint::new
        );
    }
}
