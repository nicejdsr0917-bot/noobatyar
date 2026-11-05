package xyz.sattar.javid.proqueue.domain.model.business

/** Whether this business lists [slug] among its insurances; false for no insurance chosen. */
fun Business.acceptsInsurance(slug: String?): Boolean = slug != null && insurances.any { it.slug == slug }

data class Insurance(
    val id: Long = 0,
    val name: String,
    val slug: String,
    val insuranceType: String = "SUPPLEMENTARY",
    /** Site-relative (or absolute) URL; null until the admin uploads one. */
    val logoPath: String? = null
)
