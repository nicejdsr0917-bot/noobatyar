package xyz.sattar.javid.proqueue.testsupport

import xyz.sattar.javid.proqueue.domain.MessageRepository
import xyz.sattar.javid.proqueue.domain.model.message.Message

/** Every member throws; a test subclasses it and overrides what it exercises. */
open class StubMessageRepository : MessageRepository {
    override suspend fun insertMessage(message: Message): Boolean = TODO("not stubbed")
    override suspend fun getAppointmentMessages(appointmentId: Long): List<Message> = TODO("not stubbed")
    override suspend fun getMessagesForVisitorAndBusiness(visitorId: Long, businessId: Long): List<Message> = TODO("not stubbed")
    override suspend fun deleteMessage(id: Long): Boolean = TODO("not stubbed")
    override suspend fun deleteMessagesByVisitorId(visitorId: Long): Boolean = TODO("not stubbed")
    override suspend fun getRemoteMessages(visitorId: Long, businessId: Long, businessTitle: String): List<Message> = TODO("not stubbed")
}
