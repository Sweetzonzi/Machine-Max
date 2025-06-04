function engineInvoke(subsystem) {
    if (subsystem.name == "left_front_wheel_driver") {
        print(subsystem.attr.rollingAxis)
    }

}

mm.hook("tick", (subsystem) => {
    engineInvoke(subsystem)
})
mm.hook("pre", (subsystem) => {
    engineInvoke(subsystem)
})
mm.hook("post", (subsystem) => {
    engineInvoke(subsystem)
})