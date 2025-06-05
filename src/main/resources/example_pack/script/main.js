// 小示范，在F3+T后打印VehicleCore对象的所有类方法信息
var x = false;
mm.hook("VehicleCore:prePhysicsTick", (core) => {
    if (!x) {
        x = getMethods(core)
        print("resources/example_pack/script/main.js 小示范运行完毕")
    }

})