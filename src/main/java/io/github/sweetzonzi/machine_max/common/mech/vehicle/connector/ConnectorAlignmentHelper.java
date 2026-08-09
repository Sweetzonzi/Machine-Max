package io.github.sweetzonzi.machine_max.common.mech.vehicle.connector;

import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.util.MMMath;
import io.github.sweetzonzi.machine_max.util.data.Axis;

public final class ConnectorAlignmentHelper {
    public static final float DEFAULT_MAX_POS_ERROR = 1e-4f;
    public static final float DEFAULT_MAX_DIRECTION_ERROR = (float) Math.toRadians(1e-4f);

    private ConnectorAlignmentHelper() {
    }

    public static boolean isAlignedForAttach(
            AbstractConnector advancedConnector,
            SimpleConnector simpleConnector,
            float maxPositionError,
            float maxDirectionError
    ) {
        float posError = getPositionError(advancedConnector, simpleConnector);
        float directionError = getOppositeDirectionError(advancedConnector, simpleConnector);
        return posError < maxPositionError && directionError < maxDirectionError;
    }

    public static float getPositionError(AbstractConnector connector1, AbstractConnector connector2) {
        return MMMath.relPointWorldPos(connector1.offsetFromMassCenter.getTranslation(), connector1.subPart.body)
                .subtract(MMMath.relPointWorldPos(connector2.offsetFromMassCenter.getTranslation(), connector2.subPart.body))
                .length();
    }

    public static float getOppositeDirectionError(AbstractConnector connector1, AbstractConnector connector2) {
        Vector3f direction1 = getConnectorWorldDirection(connector1);
        Vector3f direction2 = getConnectorWorldDirection(connector2);
        float alignedDot = Math.clamp(-direction1.dot(direction2), -1f, 1f);
        return (float) Math.acos(alignedDot);
    }

    public static Vector3f getConnectorWorldDirection(AbstractConnector connector) {
        Vector3f localDirection = Axis.axisToVector(connector.attr.getDirection());
        var worldRotation = connector.subPart.body.getPhysicsRotation(null).mult(connector.offsetFromMassCenter.getRotation());
        Vector3f worldDirection = worldRotation.toRotationMatrix().mult(localDirection, new Vector3f());
        if (worldDirection.lengthSquared() > 1e-6f) {
            worldDirection = worldDirection.normalize();
        }
        return worldDirection;
    }
}
