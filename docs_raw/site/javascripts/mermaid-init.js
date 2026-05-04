// 监听 MkDocs Material 的页面切换事件，重新渲染 Mermaid
document$.subscribe(function() {
    mermaid.initialize({ startOnLoad: false });
    var nodes = document.querySelectorAll(".mermaid");
    if (nodes.length > 0) {
        mermaid.run({ nodes: nodes });
    }
});
