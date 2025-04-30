package io.github.tt432.machinemax.external;

public class TestPackProvider {
    public static String part() {
        return """
                {
                	"format_version": "1.12.0",
                	"minecraft:geometry": [
                		{
                			"description": {
                				"identifier": "geometry.test_cube_vpack",
                				"texture_width": 64,
                				"texture_height": 64,
                				"visible_bounds_width": 3,
                				"visible_bounds_height": 13.5,
                				"visible_bounds_offset": [0, 6.25, 0]
                			},
                			"bones": [
                				{
                					"name": "Root",
                					"pivot": [0, 0, 0],
                					"cubes": [
                						{
                							"origin": [-8, -8, -8],
                							"size": [16, 16, 16],
                							"uv": {
                								"north": {"uv": [0, 0], "uv_size": [16, 16]},
                								"east": {"uv": [0, 16], "uv_size": [16, 16]},
                								"south": {"uv": [16, 0], "uv_size": [16, 16]},
                								"west": {"uv": [16, 16], "uv_size": [16, 16]},
                								"up": {"uv": [0, 32], "uv_size": [16, 16]},
                								"down": {"uv": [32, 16], "uv_size": [16, -16]}
                							}
                						}
                					],
                					"locators": {
                						"AttachPoint_North": [0, 0, -8],
                						"attachPoint_South": [0, 0, 8],
                						"AttachPoint_Top": [0, 8, 0],
                						"AttachPoint_Bottom": [0, -8, 0],
                						"AttachPoint_West": [8, 0, 0],
                						"AttachPoint_East": [-8, 0, 0],
                						"MassCenter": [0, 0, 0]
                					}
                				}
                			]
                		}
                	]
                }
                """;
    }

    public static String part_type() {
        return """
                {
                  "name": "test_cube_vpack",//部件名称，必须与文件名相同，必须与文件名相同，必须与文件名相同
                  "variants": {//部件变体列表，可有多个变体名和模型路径的键值对
                    "default": "machine_max:testpack/part/test_cube_vpack.geo.json"//若无变体，可缩减，即"variants": "machine_max:part/test_cube.geo"
                  },
                  "textures": [//部件贴图列表，不同变体模型必须共享一张贴图，否则请创建新的部件而非部件变体
                    "machine_max:textures/part/test_cube.png"
                  ],
                  "basic_durability": 10.0,//部件基础生命值
                  "sub_parts": {//零件(子部件)列表
                    "cube": {//零件(子部件)名称
                      "mass": 1000.0,//零件质量(kg)
                      "hit_boxes": {//碰撞形状属性，包括骨骼名称、形状类型、材质、厚度等
                        "Root": {//骨骼名称，会寻找名称匹配的骨骼，并根据其中的方块创建不同形状的碰撞体
                          "type": "box"//碰撞形状类型，支持box、sphere、cylinder
                        }
                      },
                      "connectors": {//子部件用于与其他零件连接的对接口列表
                        "north": {//接口名称
                          "locator": "AttachPoint_North",//Locator名称，会寻找名称匹配的Locator，以其位置与姿态作为对接口的位置和姿态
                          "type": "AttachPoint", //对接口类型，支持AttachPoint和Special
                          "collide_between_parts": true //是否允许子部件之间碰撞
                        },
                        "east": {
                          "locator": "AttachPoint_East",
                          "type": "AttachPoint",
                          "collide_between_parts": true
                        },
                        "south": {
                          "locator": "attachPoint_South",
                          "type": "AttachPoint",
                          "collide_between_parts": true
                        },
                        "west": {
                          "locator": "AttachPoint_West",
                          "type": "AttachPoint",
                          "collide_between_parts": true
                        },
                        "top": {
                          "locator": "AttachPoint_Top",
                          "type": "AttachPoint",
                          "collide_between_parts": true
                        },
                        "bottom": {
                          "locator": "AttachPoint_Bottom",
                          "type": "AttachPoint",
                          "collide_between_parts": true
                        }
                      },
                      "aero_dynamic": {}
                    }
                  }
                }
                """;
    }
}
