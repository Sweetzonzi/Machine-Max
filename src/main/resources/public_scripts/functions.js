function Nbt() {
    return mm.nbt()
}
function print(arg) {
    mm.print(arg)
}
function equals(tick, at) {
    return tick === at
}
function duration(tick, from, to) {
    return tick >= from && tick <= to
}

function getMethods(obj) {
    try {
        // 获取对象的Class对象
        var clazz = obj.getClass();
        // 获取所有声明的方法（包括私有方法）
        var methods = clazz.getDeclaredMethods();
        // 遍历并打印方法名
        print("--------- "+mm.getSimpleName(clazz.getName())+" ---------")
        for (var i = 0; i < methods.length; i++) {
            var paramStrings = "";
            for (var ii = 0; ii < methods[i].parameterTypes.length; ii++) {
                paramStrings += methods[i].parameterTypes[ii]
                paramStrings += ii === (methods[i].parameterTypes.length - 1) ? "" : "， "
            }
            paramStrings = paramStrings !== "" ? "  ||  ParamTypes: ("+paramStrings+")" : ""
            print("Method: " + methods[i].getName() + "[ returnType: "+methods[i].returnType+paramStrings+" ]");
        }
        print("- End -----------------------------")
        return true
    } catch (e) {

    }
    return false
}

function getFields(obj) {
    try {
        var clazz = obj.getClass();
        // 获取所有声明的字段（包括私有字段）
        var fields = clazz.getDeclaredFields();
        // 遍历并打印字段名
        print("--------- "+mm.getSimpleName(clazz.getName())+" ---------")
        for (var i = 0; i < fields.length; i++) {
            print("[ Field: " + fields[i].getName() + "  ||  Type: " + fields[i].type+" ]");
        }
        print("- End ---------------------------")
        return true
    } catch (e) {

    }
    return false
}