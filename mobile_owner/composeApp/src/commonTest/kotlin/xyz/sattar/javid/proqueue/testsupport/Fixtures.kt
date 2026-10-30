package xyz.sattar.javid.proqueue.testsupport

import xyz.sattar.javid.proqueue.domain.model.appointment.Appointment
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentWithDetails
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.domain.model.visitor.Visitor

fun testBusiness(id: Long = 1, title: String = "Shop $id") = Business(
    id = id, title = title, category = "OTHER", categoryLabel = "x", phone = "0", address = "a",
    logoPath = "", defaultServiceDuration = 30, workStartHour = 9, workEndHour = 17,
    notificationEnabled = false, notificationTypes = ""
)

fun testVisitor(id: Long = 1, name: String = "visitor-$id") = Visitor(id, name, "0912000000$id", 0)

fun testAppointment(id: Long, businessId: Long, visitorId: Long = id, date: Long = 0, status: String = "WAITING") =
    Appointment(id, businessId, visitorId, date, 15, status, null, createdAt = 0, updatedAt = 0)

fun testAppointmentWithDetails(id: Long, business: Business, visitor: Visitor = testVisitor(id), date: Long = 0) =
    AppointmentWithDetails(testAppointment(id, business.id, visitor.id, date), visitor, business)
