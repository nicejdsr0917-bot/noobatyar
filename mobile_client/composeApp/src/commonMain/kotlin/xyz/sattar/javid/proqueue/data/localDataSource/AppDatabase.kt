package xyz.sattar.javid.proqueue.data.localDataSource

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import xyz.sattar.javid.proqueue.data.localDataSource.appointment.AppointmentEntity
import xyz.sattar.javid.proqueue.data.localDataSource.business.BusinessEntity
import xyz.sattar.javid.proqueue.data.localDataSource.message.MessageEntity
import xyz.sattar.javid.proqueue.data.localDataSource.visitor.VisitorEntity
import xyz.sattar.javid.proqueue.data.localDataSource.user.UserEntity
import xyz.sattar.javid.proqueue.data.localDataSource.user.SubscriptionEntity

internal const val dbFileName = "proQueue.db"

@Database(
    entities = [
        BusinessEntity::class,
        VisitorEntity::class,
        AppointmentEntity::class,
        MessageEntity::class,
        UserEntity::class,
        SubscriptionEntity::class
    ],
    // Both platforms build with fallbackToDestructiveMigration, so this drops
    // the local cache and refetches — no real migration path needed, just a
    // version number higher than anything already shipped.
    // 10: BusinessEntity moderation columns (moderationStatus/Note/etc, this
    //     branch) AND, independently on develop, BusinessEntity.noticeEnabled/
    //     noticeMessage/reminderDelivery — both landed as "version 10" on
    //     their own branch and collided on merge.
    // 11: BusinessEntity.isLocked (billing lock, separate from moderation).
    // 12: AppointmentEntity.selectedServices (service-catalog chips picked
    //     when recording an appointment).
    // 13: BusinessEntity.services / allowClientAddService (the business's own
    //     service menu, defined once and reused by the client booking page).
    // 14: merge of the two branches above — no schema change of its own,
    //     just the next version number since 10/11 and 13 were assigned
    //     independently on two branches and can't both be "the" version 10/11.
    // 15: BusinessEntity.enableReminderSms — the owner app can now switch the
    //     client's reminder on and off instead of it being backend-only.
    // 17: Client discovery columns on BusinessEntity (lat/lng, province, city,
    //     neighborhood, isClaimed, distanceKm, onlineBookingRequestsCount, insurancesJson).
    // The consumer app keeps nothing in Room any more (no DAOs); the entities
    // stay listed so the on-disk schema (and version 17) is untouched and
    // installed apps open the existing file without any migration.
    version = 17
)
@ConstructedBy(DbFactory::class)
abstract class AppDatabase : RoomDatabase() {
}
