package xyz.sattar.javid.proqueue.testsupport

import xyz.sattar.javid.proqueue.domain.model.visitor.Visitor
import xyz.sattar.javid.proqueue.data.localDataSource.visitor.VisitorLocalSource

/** Every member throws; a test subclasses it and overrides what it exercises. */
open class StubVisitorLocalSource : VisitorLocalSource {
    override suspend fun upsertVisitor(visitor: Visitor): Long = TODO("not stubbed")

    override suspend fun upsertVisitors(visitors: List<Visitor>): Unit = TODO("not stubbed")

    override suspend fun getVisitorById(visitorId: Long): Visitor? = TODO("not stubbed")

    override suspend fun getVisitors(limit: Int, offset: Int, query: String?): List<Visitor> = TODO("not stubbed")

    override suspend fun clearAllVisitors(): Unit = TODO("not stubbed")

    override suspend fun deleteVisitor(visitorId: Long): Unit = TODO("not stubbed")
}
