package xyz.sattar.javid.proqueue.data.remoteDataSource.business.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class InsuranceDto(
    @SerialName("id") val id: Long,
    @SerialName("name") val name: String,
    @SerialName("slug") val slug: String,
    @SerialName("insurance_type") val insuranceType: String = "SUPPLEMENTARY"
)
