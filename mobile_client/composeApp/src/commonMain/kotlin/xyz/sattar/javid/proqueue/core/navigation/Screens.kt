package xyz.sattar.javid.proqueue.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

/**
 * Routes of the consumer app. The owner app's screens (visitors, calendar,
 * subscription, …) live only in mobile_owner — this app is browse-and-book.
 */
sealed class AppScreens {
    @Serializable
    object Explore : AppScreens()

    @Serializable
    object Saved : AppScreens()

    @Serializable
    object More : AppScreens()

    @Serializable
    data class ClientBusinessDetail(val businessId: Long) : AppScreens()

    @Serializable
    object AboutUs : AppScreens()
}

sealed interface MainTab {
    val title: String
    val iconSelected: ImageVector
    val iconUnSelected: ImageVector
    val route: AppScreens

    data object Explore : MainTab {
        override val title = "کاوش"
        override val iconSelected = Icons.Rounded.Explore
        override val iconUnSelected = Icons.Outlined.Explore
        override val route = AppScreens.Explore
    }

    data object Saved : MainTab {
        override val title = "نشان‌شده‌ها"
        override val iconSelected = Icons.Rounded.Bookmark
        override val iconUnSelected = Icons.Outlined.BookmarkBorder
        override val route = AppScreens.Saved
    }

    data object More : MainTab {
        override val title = "بیشتر"
        override val iconSelected = Icons.Rounded.GridView
        override val iconUnSelected = Icons.Outlined.GridView
        override val route = AppScreens.More
    }
}
