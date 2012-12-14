window.____MODULE_NAME___base = "../__MODULE_NAME__/";
(function(){
    var f = window.__MODULE_FUNC__;
    if (f) {
        f();
    }
    else {
        var s = document.createElement("script");
        s.setAttribute("src", window.____MODULE_NAME___base + "__MODULE_NAME__.js?x=" + (Math.random()));
        document.getElementsByTagName("head")[0].appendChild(s);
    }
})();
