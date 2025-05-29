package io.github.sweetzonzi.machinemax.external.js;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

public interface EventToJS {
    Object call(Context cx, Scriptable scope);
}
