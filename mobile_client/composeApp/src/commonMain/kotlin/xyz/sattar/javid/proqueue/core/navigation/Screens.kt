package xyz.sattar.javid.proqueue.core.navigation

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Bookmark
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.Compass
import com.composables.icons.lucide.CircleUserRound
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

    /** [resumeAppointmentId] > 0 opens the payment step of an existing hold. */
    @Serializable
    data class Booking(
        val businessId: Long,
        val resumeAppointmentId: Long = 0,
        /** A day and slot already picked on the business page (ISO date, slot timestamp). */
        val date: String = "",
        val slotAt: Long = 0,
    ) : AppScreens()

    @Serializable
    data class AppointmentDetail(val appointmentId: Long) : AppScreens()

    @Serializable
    data class BusinessMap(val businessId: Long) : AppScreens()

    @Serializable
    object MyAppointments : AppScreens()

    /** [bookBusinessId] > 0 continues into that business's booking once signed in. */
    @Serializable
    data class Login(val bookBusinessId: Long = 0) : AppScreens()
}

sealed interface MainTab {
    val title: String
    val iconSelected: ImageVector
    val iconUnSelected: ImageVector
    val route: AppScreens

    data object Explore : MainTab {
        override val title = "کاوش"
        override val iconSelected = Lucide.Compass
        override val iconUnSelected = Lucide.Compass
        override val route = AppScreens.Explore
    }

    data object Appointments : MainTab {
        override val title = "نوبت‌های من"
        override val iconSelected = Lucide.CalendarDays
        override val iconUnSelected = Lucide.CalendarDays
        override val route = AppScreens.MyAppointments
    }

    data object Saved : MainTab {
        override val title = "نشان‌شده‌ها"
        override val iconSelected = Lucide.Bookmark
        override val iconUnSelected = Lucide.Bookmark
        override val route = AppScreens.Saved
    }

    /** The profile tab; the tab bar draws the visitor's photo instead of these icons. */
    data object More : MainTab {
        override val title = "پروفایل"
        override val iconSelected = Lucide.CircleUserRound
        override val iconUnSelected = Lucide.CircleUserRound
        override val route = AppScreens.More
    }
}
