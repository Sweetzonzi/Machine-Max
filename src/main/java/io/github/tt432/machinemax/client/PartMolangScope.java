package io.github.tt432.machinemax.client;

import io.github.tt432.eyelib.capability.EyelibAttachableData;
import io.github.tt432.eyelib.molang.MolangScope;
import io.github.tt432.machinemax.common.part.AbstractPart;
import lombok.Getter;
import org.ode4j.math.DMatrix3;
import org.ode4j.math.DQuaternion;
import org.ode4j.math.DVector3;
import org.ode4j.ode.internal.Rotation;

public class PartMolangScope {

    AbstractPart part;
    @Getter
    public final MolangScope scope = new MolangScope();
    @Getter
    private float worldX=0;
    @Getter
    private float worldY=0;
    @Getter
    private float worldZ=0;
    @Getter
    private float x=0;
    @Getter
    private float y=0;
    @Getter
    private float z=0;
    @Getter
    private float pitch=0;//rotX
    @Getter
    private float yaw=0;//rotY
    @Getter
    private float roll=0;//rotZ

    public PartMolangScope(AbstractPart part) {
        this.part = part;
        scope.setOwner(part);
        scope.setParent(part.getAttachedEntity().getData(EyelibAttachableData.RENDER_DATA).getScope());
        //TODO:注意！使用时必须使用variable前缀，不可使用v.的缩写！
        scope.set("variable.part_world_x", this::getWorldX);
        scope.set("variable.part_world_y", this::getWorldY);
        scope.set("variable.part_world_z", this::getWorldZ);
        scope.set("variable.part_rel_x", this::getX);
        scope.set("variable.part_rel_y", this::getY);
        scope.set("variable.part_rel_z", this::getZ);
        scope.set("variable.part_pitch", this::getPitch);
        scope.set("variable.part_yaw", this::getYaw);
        scope.set("variable.part_roll", this::getRoll);
    }

}
