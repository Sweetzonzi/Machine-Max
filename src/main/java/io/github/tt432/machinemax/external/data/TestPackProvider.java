package io.github.tt432.machinemax.external.data;

import static io.github.tt432.machinemax.MachineMax.MOD_ID;

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
                								"down": {"uv": [0, 32], "uv_size": [16, 16]},
                								"up": {"uv": [32, 16], "uv_size": [16, -16]}
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
                    "machine_max:testpack/texture/test_cube_vpack.png"
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

    public static String blueprint() {
        return """
                {
                  "hp": 20.0,
                  "parts": [
                    {
                      "textureIndex": 0,
                      "uuid": "fb4a658d-dc9d-412c-8895-9c9637e4d2fd",
                      "durability": 50.0,
                      "subPartTransforms": {
                        "ae86_wheel_all_terrain": {
                          "position": [
                            -15.372847,
                            -59.187485,
                            31.612873
                          ],
                          "rotation": [
                            0.9763009,
                            -0.016799172,
                            0.08147507,
                            0.19979016
                          ],
                          "linearVel": [
                            -0.051574208,
                            -3.2290816E-5,
                            0.28265092
                          ],
                          "angularVel": [
                            0.35034746,
                            -0.0038909742,
                            0.062637694
                          ]
                        }
                      },
                      "registryKey": "machine_max:ae86_wheel_all_terrain",
                      "subpart": "ae86_wheel_all_terrain",
                      "variant": "right"
                    },
                    {
                      "textureIndex": 0,
                      "uuid": "630c36d2-5c42-4a55-a83e-c3992b7b1404",
                      "durability": 50.0,
                      "subPartTransforms": {
                        "ae86_wheel_all_terrain": {
                          "position": [
                            -18.057646,
                            -59.187515,
                            26.47162
                          ],
                          "rotation": [
                            0.9648111,
                            -0.011893405,
                            0.044256665,
                            0.25891975
                          ],
                          "linearVel": [
                            -0.033523623,
                            -0.0013167411,
                            0.2731036
                          ],
                          "angularVel": [
                            0.3429876,
                            -0.0046978667,
                            0.029372701
                          ]
                        }
                      },
                      "registryKey": "machine_max:ae86_wheel_all_terrain",
                      "subpart": "ae86_wheel_all_terrain",
                      "variant": "left"
                    },
                    {
                      "textureIndex": 0,
                      "uuid": "478c9368-9da9-4e93-a58f-8b9471094981",
                      "durability": 25.0,
                      "subPartTransforms": {
                        "ae86_seat": {
                          "position": [
                            -16.170067,
                            -57.89831,
                            29.19351
                          ],
                          "rotation": [
                            -0.0014343784,
                            -0.083175406,
                            -2.0089855E-4,
                            0.9965339
                          ],
                          "linearVel": [
                            -0.047025576,
                            0.0027481169,
                            0.27913797
                          ],
                          "angularVel": [
                            -1.6900527E-4,
                            -0.0036331657,
                            0.002875739
                          ]
                        }
                      },
                      "registryKey": "machine_max:ae86_seat",
                      "subpart": "ae86_seat",
                      "variant": "default"
                    },
                    {
                      "textureIndex": 0,
                      "uuid": "87c577e0-ae4a-4ca4-ae7c-19404fb0add2",
                      "durability": 50.0,
                      "subPartTransforms": {
                        "ae86_wheel_all_terrain": {
                          "position": [
                            -18.824375,
                            -59.18756,
                            31.032682
                          ],
                          "rotation": [
                            0.9566448,
                            -0.023201615,
                            0.07983068,
                            0.2791408
                          ],
                          "linearVel": [
                            -0.05082639,
                            -0.0017470419,
                            0.26919675
                          ],
                          "angularVel": [
                            0.3324166,
                            -0.0024318853,
                            0.06350585
                          ]
                        }
                      },
                      "registryKey": "machine_max:ae86_wheel_all_terrain",
                      "subpart": "ae86_wheel_all_terrain",
                      "variant": "left"
                    },
                    {
                      "textureIndex": 0,
                      "uuid": "ffe8f84a-a1bb-413e-9f9d-a48301c53e32",
                      "durability": 50.0,
                      "subPartTransforms": {
                        "ae86_wheel_all_terrain": {
                          "position": [
                            -14.606006,
                            -59.187508,
                            27.051876
                          ],
                          "rotation": [
                            0.9871038,
                            -0.009282568,
                            0.062016256,
                            0.14728859
                          ],
                          "linearVel": [
                            -0.035757557,
                            0.0021484196,
                            0.28432107
                          ],
                          "angularVel": [
                            0.3517315,
                            -0.011266961,
                            0.04412765
                          ]
                        }
                      },
                      "registryKey": "machine_max:ae86_wheel_all_terrain",
                      "subpart": "ae86_wheel_all_terrain",
                      "variant": "right"
                    },
                    {
                      "textureIndex": 0,
                      "uuid": "29aa4eb8-74ba-4595-8a95-fd00bfdf8a62",
                      "durability": 100.0,
                      "subPartTransforms": {
                        "ae86_chassis_all_terrain": {
                          "position": [
                            -16.683659,
                            -58.398945,
                            28.855127
                          ],
                          "rotation": [
                            -0.0014347766,
                            -0.08317477,
                            -1.8289244E-4,
                            0.99653393
                          ],
                          "linearVel": [
                            -0.044462584,
                            0.0012538135,
                            0.27742064
                          ],
                          "angularVel": [
                            -1.5141873E-4,
                            -0.0036720766,
                            0.0026221923
                          ]
                        }
                      },
                      "registryKey": "machine_max:ae86_chassis_all_terrain",
                      "subpart": "ae86_chassis_all_terrain",
                      "variant": "default"
                    }
                  ],
                  "connections": [
                    {
                      "PartUuidA": "87c577e0-ae4a-4ca4-ae7c-19404fb0add2",
                      "SubPartNameA": "ae86_wheel_all_terrain",
                      "AttachPointConnectorName": "chassis_connection",
                      "PartUuidS": "29aa4eb8-74ba-4595-8a95-fd00bfdf8a62",
                      "SubPartNameS": "ae86_chassis_all_terrain",
                      "SpecialConnectorName": "left_back_wheel"
                    },
                    {
                      "PartUuidA": "630c36d2-5c42-4a55-a83e-c3992b7b1404",
                      "SubPartNameA": "ae86_wheel_all_terrain",
                      "AttachPointConnectorName": "chassis_connection",
                      "PartUuidS": "29aa4eb8-74ba-4595-8a95-fd00bfdf8a62",
                      "SubPartNameS": "ae86_chassis_all_terrain",
                      "SpecialConnectorName": "left_front_wheel"
                    },
                    {
                      "PartUuidA": "ffe8f84a-a1bb-413e-9f9d-a48301c53e32",
                      "SubPartNameA": "ae86_wheel_all_terrain",
                      "AttachPointConnectorName": "chassis_connection",
                      "PartUuidS": "29aa4eb8-74ba-4595-8a95-fd00bfdf8a62",
                      "SubPartNameS": "ae86_chassis_all_terrain",
                      "SpecialConnectorName": "right_front_wheel"
                    },
                    {
                      "PartUuidA": "fb4a658d-dc9d-412c-8895-9c9637e4d2fd",
                      "SubPartNameA": "ae86_wheel_all_terrain",
                      "AttachPointConnectorName": "chassis_connection",
                      "PartUuidS": "29aa4eb8-74ba-4595-8a95-fd00bfdf8a62",
                      "SubPartNameS": "ae86_chassis_all_terrain",
                      "SpecialConnectorName": "right_back_wheel"
                    },
                    {
                      "PartUuidA": "478c9368-9da9-4e93-a58f-8b9471094981",
                      "SubPartNameA": "ae86_seat",
                      "AttachPointConnectorName": "chassis_connection",
                      "PartUuidS": "29aa4eb8-74ba-4595-8a95-fd00bfdf8a62",
                      "SubPartNameS": "ae86_chassis_all_terrain",
                      "SpecialConnectorName": "driver_seat"
                    }
                  ],
                  "vehicle_name": "Vehicle",
                  "tooltip": "machine_max:testpack/content/test.html",
                  "uuid": "29aa4eb8-74ba-4595-8a95-fd00bfdf8a62",
                  "pos": [
                    -16.61766990025838,
                    -58.841248194376625,
                    29.02701187133789
                  ]
                }
                """;
    }

    public static String test_cube_vpack_png_base64() {
        return "iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAAAAXNSR0IArs4c6QA" +
                "AAltJREFUeJztmj1Ow0AQhd9GLqi4AuIKXAVER4EIHWWEUCiRohS5QYIo6BBn4QpR6KiQq" +
                "EITUySr2Ov17uyPk80yX0NiM7bf25nxLEJMgRIB3J5NQ8KB88+w+McnERJeAEDfM3gmP5x" +
                "6XmE+234eDtxiRxO/eyr0olwllOHATdBo4m5YC0XtW7mpBmHIqrJsPV++024qLjQHpQk2YR" +
                "HFo2FAJLQCNxhNspkQWTy0JSDENhM8MImnnG8thw7Eo7UHBJoQjGpCR+JhLAFpgqkfaL" +
                "D1AWsGSKomdCQe1reAYyZQxFEb5a6wN0HHTLCZQDagmvYdlgBtDth1T1AFu84JDtAHIYI" +
                "JttUlrX7bandkQvQ5IKjGbalOHZYcqBtgq3PLeXKH10EVFtmENPYCroIilkOB6q7Ol3nwFTprcj" +
                "aK38Bnn/W/g+L7H2H7+VAKALi68Qt+fV7/vMalV/wL3vxuHJE0esAeqb0Fjol/HfuBPmt75QlW" +
                "YmGMpfzOLtHOAW0C4WASNmIBJCVYpVECJvGU84cG9wD1ACXFKVlQrfXU6r4KZ4B6IEa" +
                "Nqyu+EotksyBqBkihrTdL0ISGAbYeQOkROpGpCZdo5wCXd/2hUzMgRv2bVjrFLOC3ACq7Ol9" +
                "S2NX5Utx9HQVe4v6gZ+N1D3jwNGG8jPs0e8DcA8bLLESa+PdNkA3Y9wPsGzag9o3S8DJris" +
                "0MMAnMTDwaBsh5QCdUHvOdGRKlmQEmgZmJh7EJVrMgw9SX6A3QrXSGqw9jBlQFZyoe" +
                "1v8QyVi4ZG1AxjXOMAzDMAzDMAzDMAyj8gcpH7hLudrjwgAAAABJRU5ErkJggg==";
    }

    public static String zh_cn() {
        return """
                {
                  "item.machine_max.testpack.blueprint.test_blue_print.json": "测试蓝图",
                  "machine_max.tab.blueprint": "自定义蓝图",
                  "machine_max.item.test_cube_vpack": "外部包测试方块"
                }
                """;
    }
    public static String en_us() {
        return """
                {
                  "item.machine_max.testpack.blueprint.test_blue_print.json": "Test BluePrint",
                  "machine_max.tab.blueprint": "Custom BluePrints",
                  "machine_max.item.test_cube_vpack": "External-Pack Test Cube"
                }
                """;
    }
    public static String content_txt() {
        return "\n" +
                "<c.9BE868><f.test_font>This </f.test_font></c.9BE868><f.test_font>is a demonstration blueprint, an off-road vehicle</f.test_font>\n" +
                "\n" +
                "\n" +
                "<italic> 这是一个演示蓝图，一台越野车             </italic>Maker:<c.my_red> XXX</c.my_red>\n";
    }

    public static String test_font_json() {
        return """
                {
                  "providers": [
                    {
                      "type": "ttf",
                      "file": "machine_max:bell.ttf",
                      "size": 12,
                      "shift": [0, 0],
                      "oversample": 24.0
                    }
                  ]
                }
                """;
    }

    public static String color_palette_json() {
        return """
                {
                  "colors" : {
                    "my_red" : "FF0004"
                  }
                }
                """;
    }
}
