package xyz.sattar.javid.proqueue.core.utils

/**
 * Hands [text] to the platform's own share sheet — Android's chooser, iOS's
 * UIActivityViewController — so the owner sends their booking link with
 * whichever app they already talk to that customer in.
 *
 * Deliberately plain text rather than a URL type: every Iranian messenger the
 * owners actually use (Bale, Eitaa, Rubika, WhatsApp) accepts a text share and
 * auto-links a URL inside it, while a url-typed share is quietly dropped by
 * some of them. It also lets the link travel with a sentence around it —
 * see [xyz.sattar.javid.proqueue.feature.share] for the copy.
 */
expect fun shareText(text: String)
