# 翻译键智能体手册

1.要求业务逻辑类型代码中涉及到多语言支持Componet对象的内容应该先在
io.github.sweetzonzi.machine_max.datagen.MMLanguageRegistry
中注册对应标签。后续引用则使用标签.tag获取翻译字符串，若需要获取Component.translatable后的内容可以直接调用MMLanguageRegistry#translated()方法

2.任何智能体均必须向用户提醒并对改动一一审核后，才能进行其他语言内容补全翻译操作。
不得擅自进行翻译键补全、修改、删除

