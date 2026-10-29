package xyz.sattar.javid.proqueue.testsupport

import kotlinx.coroutines.flow.Flow
import xyz.sattar.javid.proqueue.domain.model.user.Subscription
import xyz.sattar.javid.proqueue.domain.model.user.User
import xyz.sattar.javid.proqueue.data.localDataSource.user.UserLocalSource

/** Every member throws; a test subclasses it and overrides what it exercises. */
open class StubUserLocalSource : UserLocalSource {
    override suspend fun insertUser(user: User): Unit = TODO("not stubbed")

    override fun getUserById(id: Int): Flow<User?> = TODO("not stubbed")

    override fun getCurrentUser(): Flow<User?> = TODO("not stubbed")

    override suspend fun insertSubscription(subscription: Subscription): Unit = TODO("not stubbed")

    override fun getActiveSubscription(): Flow<Subscription?> = TODO("not stubbed")

    override suspend fun clearUser(): Unit = TODO("not stubbed")

    override suspend fun clearSubscription(): Unit = TODO("not stubbed")
}
