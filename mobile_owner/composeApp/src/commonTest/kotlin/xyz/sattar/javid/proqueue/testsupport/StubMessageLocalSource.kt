package xyz.sattar.javid.proqueue.testsupport

import xyz.sattar.javid.proqueue.domain.model.message.Message
import xyz.sattar.javid.proqueue.data.localDataSource.message.MessageLocalSource

/** Every member throws; a test subclasses it and overrides what it exercises. */
open class StubMessageLocalSource : MessageLocalSource {
    override suspend fun insertMessage(message: Message): Unit = TODO("not stubbed")

    override suspend fun getAppointmentMessages(appointmentId: Long): List<Message> = TODO("not stubbed")

    override suspend fun getMessagesForVisitorAndBusiness(visitorId: Long, businessId: Long): List<Message> = TODO("not stubbed")

    override suspend fun deleteMessage(id: Long): Int = TODO("not stubbed")

    override suspend fun deleteMessagesByVisitorId(visitorId: Long): Int = TODO("not stubbed")

    override suspend fun clearAll(): Unit = TODO("not stubbed")
}
