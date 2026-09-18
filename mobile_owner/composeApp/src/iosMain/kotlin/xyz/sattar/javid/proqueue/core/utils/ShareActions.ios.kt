package xyz.sattar.javid.proqueue.core.utils

import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.popoverPresentationController
import platform.UIKit.UIViewController

actual fun shareText(text: String) {
    val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
    val controller = UIActivityViewController(
        activityItems = listOf(text),
        applicationActivities = null,
    )
    // iPad presents this as a popover and *crashes* without an anchor. There
    // is no single source view here (the share sheet is opened from whichever
    // of the three cards was tapped), so it is anchored to the presenting
    // view itself — which is what a full-width sheet would anchor to anyway.
    controller.popoverPresentationController?.sourceView = root.view
    presentOn(root, controller)
}

private fun presentOn(root: UIViewController, controller: UIActivityViewController) {
    val presenter = root.presentedViewController ?: root
    presenter.presentViewController(controller, animated = true, completion = null)
}
