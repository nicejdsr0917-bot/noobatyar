package xyz.sattar.javid.proqueue.testsupport

import kotlinx.coroutines.flow.Flow
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.data.localDataSource.business.BusinessLocalSource

/** Every member throws; a test subclasses it and overrides what it exercises. */
open class StubBusinessLocalSource : BusinessLocalSource {
    override suspend fun upsertBusiness(business: Business): Unit = TODO("not stubbed")

    override suspend fun upsertBusinesses(businesses: List<Business>): Unit = TODO("not stubbed")

    override suspend fun loadAllBusiness(): List<Business> = TODO("not stubbed")

    override fun loadAllBusinessFlow(): Flow<List<Business>> = TODO("not stubbed")

    override suspend fun getBusinessById(businessId: Long): Business? = TODO("not stubbed")

    override suspend fun deleteBusiness(businessId: Long): Unit = TODO("not stubbed")

    override suspend fun clearAllBusinesses(): Unit = TODO("not stubbed")
}
