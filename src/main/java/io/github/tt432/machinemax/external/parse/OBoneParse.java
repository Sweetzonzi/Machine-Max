package io.github.tt432.machinemax.external.parse;

import cn.solarmoon.spark_core.animation.model.origin.OBone;
import cn.solarmoon.spark_core.animation.model.origin.OCube;
import cn.solarmoon.spark_core.animation.model.origin.OLocator;
import cn.solarmoon.spark_core.animation.model.origin.OModel;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.github.tt432.machinemax.external.MMDynamicRes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2i;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OBoneParse {
    private static Vec3 toRadians(Vec3 vec3) {
        return new Vec3(Math.toRadians(vec3.x), Math.toRadians(vec3.y), Math.toRadians(vec3.z));
    }
    public static void register(ResourceLocation id, JsonElement json) {
        JsonArray target = json.getAsJsonObject()
                .getAsJsonArray("minecraft:geometry")
                .asList()
                .get(0)
                .getAsJsonObject()
                .getAsJsonArray("bones");

        JsonObject texture = json.getAsJsonObject()
                .getAsJsonArray("minecraft:geometry")
                .asList()
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("description");

        Vector2i coord = new Vector2i(
                GsonHelper.getAsInt(texture, "texture_width"),
                GsonHelper.getAsInt(texture, "texture_height")
        );

        Map<String, OBone> bones = OBone.getMAP_CODEC()
                .decode(JsonOps.INSTANCE, target)
                .result()
                .orElseThrow()
                .getFirst()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,  // Changed from Map.Entry::getKey
                        entry -> {
                            OBone it = entry.getValue();

                            // Process cubes
                            List<OCube> cubes = it.getCubes().stream()
                                    .map(cube -> new OCube(
                                            new Vec3(
                                                    -cube.getOriginPos().x() - cube.getSize().x(),
                                                    cube.getOriginPos().y(),
                                                    cube.getOriginPos().z()
                                            ).scale(1.0/16.0),
                                            cube.getSize().scale(1.0/16.0),
                                            cube.getPivot().multiply(-1.0, 1.0, 1.0).scale(1.0/16.0),
                                            toRadians(cube.getRotation().multiply(-1.0, -1.0, 1.0)),
                                            cube.getInflate()/16,
                                            cube.getUvUnion(),
                                            cube.getMirror(),
                                            coord.x(),
                                            coord.y()
                                    ))
                                    .collect(Collectors.toCollection(ArrayList::new));

                            // Process locators
                            Map<String, OLocator> locators = it.getLocators().entrySet()
                                    .stream()
                                    .collect(Collectors.toMap(
                                            Map.Entry::getKey,
                                            locatorEntry -> new OLocator(
                                                    locatorEntry.getValue().getOffset()
                                                            .scale(1.0/16.0)
                                                            .multiply(-1.0, 1.0, 1.0),
                                                    locatorEntry.getValue().getRotation()
                                                            .multiply(-1.0, -1.0, 1.0)
                                                            .scale(1.0/16.0)
                                            )
                                    ));

                            return new OBone(
                                    it.getName(),
                                    it.getParentName(),
                                    it.getPivot().multiply(-1.0, 1.0, 1.0).scale(1.0/16.0),
                                    toRadians(it.getRotation().multiply(-1.0, -1.0, 1.0)),
                                    new LinkedHashMap<>(locators),
                                    cubes
                            );
                        },
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
        OModel model = new OModel(coord.x(), coord.y(), new LinkedHashMap<>(bones));
        MMDynamicRes.O_MODELS.put(id, model);
        OModel.getORIGINS().put(id, model);
    }

    public static void clear() {
        MMDynamicRes.O_MODELS.forEach((id, oModel) -> {
            OModel.getORIGINS().remove(id, oModel);
        });
        MMDynamicRes.O_MODELS.clear();
    }
}