package xyz.sattar.javid.proqueue.domain.model.business

data class Insurance(
    val id: Long = 0,
    val name: String,
    val slug: String,
    val insuranceType: String = "SUPPLEMENTARY"
)
