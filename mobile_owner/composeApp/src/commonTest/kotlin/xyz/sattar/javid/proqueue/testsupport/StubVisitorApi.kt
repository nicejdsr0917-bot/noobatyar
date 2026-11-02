package xyz.sattar.javid.proqueue.testsupport

import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.network.PaginatedResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.visitor.VisitorApi
import xyz.sattar.javid.proqueue.data.remoteDataSource.visitor.model.*

/** Every member throws; a test subclasses it and overrides what it exercises. */
open class StubVisitorApi : VisitorApi {
    override suspend fun getVisitors(page: Int, pageSize: Int, query: String?): ApiResponse<PaginatedResponseDto<VisitorDto>> = TODO("not stubbed")
    override suspend fun getVisitorById(id: Long): ApiResponse<VisitorDto> = TODO("not stubbed")
    override suspend fun createVisitor(request: CreateVisitorRequestDto): ApiResponse<VisitorDto> = TODO("not stubbed")
    override suspend fun updateVisitor(id: Long, request: CreateVisitorRequestDto): ApiResponse<VisitorDto> = TODO("not stubbed")
    override suspend fun deleteVisitor(id: Long): ApiResponse<Unit> = TODO("not stubbed")
    override suspend fun getVisitorMessages(visitorId: Long, businessId: Long, page: Int, pageSize: Int): ApiResponse<PaginatedResponseDto<MessageDto>> = TODO("not stubbed")
}
