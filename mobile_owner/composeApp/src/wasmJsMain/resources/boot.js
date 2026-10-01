// Kept in its own file rather than inline in index.html so the panel can be
// served with a script-src CSP that allows only files from its own origin
// (docs/OWNER_WEB_PLAN.md section 15). Loaded synchronously right where the
// inline script used to be, so the timer still starts as the HTML parses.
// Boot-timer for the loading screen above: main.kt (wasmJsMain) calls
// window.__nbHideLoading() once Compose has mounted into #compose-root
// and started rendering. This enforces the loader staying on screen
// for at least MIN_MS regardless of how fast the wasm module actually
// loaded, so a warm-cache reload doesn't just flash the mark for a
// frame — every refresh shows the same, deliberate boot moment.
(function () {
    // Enough for the dot to pop in and tick-tock once, so a warm-cache
    // reload still shows a deliberate moment rather than a flash.
    var MIN_MS = 1600;
    // Strike (0.55s) + check (0.5s) after .nb-done, before fading.
    var FINISH_MS = 1150;
    var bootStart = Date.now();
    window.__nbHideLoading = function () {
        var elapsed = Date.now() - bootStart;
        var remaining = Math.max(0, MIN_MS - elapsed);
        setTimeout(function () {
            var el = document.getElementById("compose-loading");
            if (!el) return;
            el.classList.add("nb-done");
            setTimeout(function () { el.classList.add("nb-hidden"); }, FINISH_MS);
        }, remaining);
    };
})();
