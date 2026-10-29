package xyz.sattar.javid.proqueue.data.remoteDataSource.visitor

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.network.PaginatedResponseDto
import xyz.sattar.javid.proqueue.core.network.toApiResponse
import xyz.sattar.javid.proqueue.data.remoteDataSource.visitor.model.CreateVisitorRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.visitor.model.VisitorDto

/**
 * Contract the repositories depend on; [VisitorApiService] is the Ktor
 * implementation, tests substitute a fake.
 */
interface VisitorApi {
    suspend fun getVisitors(
        page: Int, 
        pageSize: Int, 
        query: String? = null
    ): ApiResponse<PaginatedResponseDto<VisitorDto>>

    suspend fun getVisitorById(id: Long): ApiResponse<VisitorDto>

    suspend fun createVisitor(request: CreateVisitorRequestDto): ApiResponse<VisitorDto>

    suspend fun updateVisitor(id: Long, request: CreateVisitorRequestDto): ApiResponse<VisitorDto>

    suspend fun deleteVisitor(id: Long): ApiResponse<Unit>

    suspend fun getVisitorMessages(
        visitorId: Long,
        businessId: Long,
        page: Int = 1,
        pageSize: Int = 20
    ): ApiResponse<PaginatedResponseDto<xyz.sattar.javid.proqueue.data.remoteDataSource.visitor.model.MessageDto>>
}
