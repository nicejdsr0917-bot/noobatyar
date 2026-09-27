package xyz.sattar.javid.proqueue.core.utils

/**
 * The Web Share API where the browser has one (Safari on iOS, Chrome on
 * Android) — the same system sheet the native apps open. Desktop browsers
 * mostly don't, so there the text goes to the clipboard instead: pasting it
 * into Bale or WhatsApp Web is what the owner would do next anyway.
 */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun(
    """(text) => {
        if (navigator.share) {
            navigator.share({ text }).catch(() => {});
        } else if (navigator.clipboard) {
            navigator.clipboard.writeText(text).catch(() => {});
        }
    }"""
)
private external fun jsShareText(text: String)

actual fun shareText(text: String) = jsShareText(text)
