package xyz.sattar.javid.proqueue.domain.model.business

import xyz.sattar.javid.proqueue.core.i18n.Str


/**
 * Business categories, mirroring `Business.CATEGORY_GROUPS` in
 * backend/business/models.py.
 *
 * This is the *offline fallback* only. The picker fetches the live vocabulary
 * from `GET business/categories/` (see BusinessRepository.getCategoryGroups)
 * and falls back here when the server is unreachable or predates that
 * endpoint, so a category added on the server no longer needs an app release.
 *
 * [fromString] falls back to [OTHER] rather than throwing, so a category this
 * build has never heard of degrades to "سایر" instead of crashing.
 */
enum class BusinessCategory(
    val value: String,
    private val persianNameRef: () -> String,
    val group: BusinessCategoryGroup) {
    // ── پزشکی و تخصص‌ها ──
    DOCTOR("DOCTOR", Str::doctors_clinics, BusinessCategoryGroup.MEDICAL),
    GENERAL_PRACTITIONER("GENERAL_PRACTITIONER", Str::general_practitioner, BusinessCategoryGroup.MEDICAL),
    INTERNAL_MEDICINE("INTERNAL_MEDICINE", Str::internal_medicine, BusinessCategoryGroup.MEDICAL),
    CARDIOLOGY("CARDIOLOGY", Str::cardiology, BusinessCategoryGroup.MEDICAL),
    DERMATOLOGY("DERMATOLOGY", Str::dermatology_hair_beauty_doctor, BusinessCategoryGroup.MEDICAL),
    ORTHOPEDICS("ORTHOPEDICS", Str::orthopedics, BusinessCategoryGroup.MEDICAL),
    PEDIATRICS("PEDIATRICS", Str::pediatrics, BusinessCategoryGroup.MEDICAL),
    GYNECOLOGY("GYNECOLOGY", Str::obstetrics_gynecology, BusinessCategoryGroup.MEDICAL),
    ENT("ENT", Str::ent, BusinessCategoryGroup.MEDICAL),
    OPHTHALMOLOGY("OPHTHALMOLOGY", Str::ophthalmology, BusinessCategoryGroup.MEDICAL),
    NEUROLOGY("NEUROLOGY", Str::neurology, BusinessCategoryGroup.MEDICAL),
    UROLOGY("UROLOGY", Str::urology, BusinessCategoryGroup.MEDICAL),
    ENDOCRINOLOGY("ENDOCRINOLOGY", Str::endocrinology_diabetes, BusinessCategoryGroup.MEDICAL),
    GASTROENTEROLOGY("GASTROENTEROLOGY", Str::gastroenterology_hepatology, BusinessCategoryGroup.MEDICAL),
    ONCOLOGY("ONCOLOGY", Str::oncology, BusinessCategoryGroup.MEDICAL),
    SURGERY("SURGERY", Str::surgery, BusinessCategoryGroup.MEDICAL),
    PSYCHIATRY("PSYCHIATRY", Str::psychiatry, BusinessCategoryGroup.MEDICAL),
    INFERTILITY("INFERTILITY", Str::fertility_ivf, BusinessCategoryGroup.MEDICAL),
    // ── دندان‌پزشکی ──
    DENTIST("DENTIST", Str::dentistry, BusinessCategoryGroup.DENTAL),
    ORTHODONTICS("ORTHODONTICS", Str::orthodontics, BusinessCategoryGroup.DENTAL),
    DENTAL_IMPLANT("DENTAL_IMPLANT", Str::implants_oral_surgery, BusinessCategoryGroup.DENTAL),
    PEDIATRIC_DENTISTRY("PEDIATRIC_DENTISTRY", Str::pediatric_dentistry, BusinessCategoryGroup.DENTAL),
    // ── سلامت و درمان ──
    PSYCHOLOGY("PSYCHOLOGY", Str::psychology_counseling, BusinessCategoryGroup.HEALTH),
    PHYSIOTHERAPY("PHYSIOTHERAPY", Str::physiotherapy_rehabilitation, BusinessCategoryGroup.HEALTH),
    NUTRITION("NUTRITION", Str::nutrition_diet_therapy, BusinessCategoryGroup.HEALTH),
    LABORATORY("LABORATORY", Str::laboratory, BusinessCategoryGroup.HEALTH),
    RADIOLOGY("RADIOLOGY", Str::radiology_ultrasound, BusinessCategoryGroup.HEALTH),
    OPTOMETRY("OPTOMETRY", Str::optometry_eyewear, BusinessCategoryGroup.HEALTH),
    SPEECH_THERAPY("SPEECH_THERAPY", Str::speech_therapy, BusinessCategoryGroup.HEALTH),
    OCCUPATIONAL_THERAPY("OCCUPATIONAL_THERAPY", Str::occupational_therapy, BusinessCategoryGroup.HEALTH),
    MIDWIFERY("MIDWIFERY", Str::midwifery_womens_health, BusinessCategoryGroup.HEALTH),
    NURSING("NURSING", Str::nursing_home_care, BusinessCategoryGroup.HEALTH),
    PHARMACY("PHARMACY", Str::pharmacy, BusinessCategoryGroup.HEALTH),
    VETERINARY("VETERINARY", Str::veterinary, BusinessCategoryGroup.HEALTH),
    // ── آرایش و زیبایی ──
    BEAUTY_SALON("BEAUTY_SALON", Str::hair_beauty_salons, BusinessCategoryGroup.BEAUTY),
    WOMENS_SALON("WOMENS_SALON", Str::womens_beauty_salon, BusinessCategoryGroup.BEAUTY),
    BARBERSHOP("BARBERSHOP", Str::barbershop, BusinessCategoryGroup.BEAUTY),
    HAIR_SALON("HAIR_SALON", Str::hair_salon_color_keratin, BusinessCategoryGroup.BEAUTY),
    HAIR_TRANSPLANT("HAIR_TRANSPLANT", Str::hair_eyebrow_transplant, BusinessCategoryGroup.BEAUTY),
    MAKEUP("MAKEUP", Str::makeup_artist, BusinessCategoryGroup.BEAUTY),
    BRIDAL("BRIDAL", Str::bridal_event_services, BusinessCategoryGroup.BEAUTY),
    NAIL_SALON("NAIL_SALON", Str::nail_salon, BusinessCategoryGroup.BEAUTY),
    EYEBROW_LASH("EYEBROW_LASH", Str::brows_lashes_microblading, BusinessCategoryGroup.BEAUTY),
    SKIN_LASER("SKIN_LASER", Str::skin_laser, BusinessCategoryGroup.BEAUTY),
    TATTOO("TATTOO", Str::tattoo_micropigmentation, BusinessCategoryGroup.BEAUTY),
    MASSAGE_SPA("MASSAGE_SPA", Str::massage_spa, BusinessCategoryGroup.BEAUTY),
    SLIMMING("SLIMMING", Str::weight_loss_fitness, BusinessCategoryGroup.BEAUTY),
    // ── خدمات حرفه‌ای ──
    CONSULTANT("CONSULTANT", Str::consulting, BusinessCategoryGroup.PROFESSIONAL),
    LAWYER("LAWYER", Str::legal_services_advice, BusinessCategoryGroup.PROFESSIONAL),
    ACCOUNTING("ACCOUNTING", Str::accounting_tax, BusinessCategoryGroup.PROFESSIONAL),
    REAL_ESTATE("REAL_ESTATE", Str::real_estate, BusinessCategoryGroup.PROFESSIONAL),
    INSURANCE("INSURANCE", Str::insurance, BusinessCategoryGroup.PROFESSIONAL),
    IMMIGRATION("IMMIGRATION", Str::immigration_visas, BusinessCategoryGroup.PROFESSIONAL),
    // ── آموزش و ورزش ──
    TUTORING("TUTORING", Str::tutoring_private_lessons, BusinessCategoryGroup.EDUCATION),
    LANGUAGE_SCHOOL("LANGUAGE_SCHOOL", Str::language_school, BusinessCategoryGroup.EDUCATION),
    MUSIC_SCHOOL("MUSIC_SCHOOL", Str::music_lessons, BusinessCategoryGroup.EDUCATION),
    GYM("GYM", Str::gym_personal_trainer, BusinessCategoryGroup.EDUCATION),
    YOGA_PILATES("YOGA_PILATES", Str::yoga_pilates, BusinessCategoryGroup.EDUCATION),
    DRIVING_SCHOOL("DRIVING_SCHOOL", Str::driving_school, BusinessCategoryGroup.EDUCATION),
    // ── خدمات فنی و تعمیرات ──
    AUTO_SERVICE("AUTO_SERVICE", Str::car_repair_services, BusinessCategoryGroup.TRADES),
    CAR_WASH("CAR_WASH", Str::car_wash_detailing, BusinessCategoryGroup.TRADES),
    HOME_SERVICE("HOME_SERVICE", Str::home_services_repairs, BusinessCategoryGroup.TRADES),
    DEVICE_REPAIR("DEVICE_REPAIR", Str::phone_electronics_repair, BusinessCategoryGroup.TRADES),
    TAILORING("TAILORING", Str::tailoring_fashion_design, BusinessCategoryGroup.TRADES),
    // ── سایر ──
    PHOTOGRAPHY("PHOTOGRAPHY", Str::photography_studio, BusinessCategoryGroup.OTHER),
    EVENT_SERVICES("EVENT_SERVICES", Str::event_venues_services, BusinessCategoryGroup.OTHER),
    PET_GROOMING("PET_GROOMING", Str::pet_grooming_care, BusinessCategoryGroup.OTHER),
    OTHER("OTHER", Str::other, BusinessCategoryGroup.OTHER);
    val persianName: String get() = persianNameRef()


    companion object {
        fun fromString(value: String?): BusinessCategory {
            return entries.find { it.value == value } ?: OTHER
        }

        /** Categories bucketed by [group], in declaration order. */
        fun grouped(): Map<BusinessCategoryGroup, List<BusinessCategory>> =
            entries.groupBy { it.group }
    }
}

/** Presentation-only bucket for the category picker; see [BusinessCategory]. */
enum class BusinessCategoryGroup(private val persianNameRef: () -> String) {
    MEDICAL(Str::medical_specialties),
    DENTAL(Str::dentistry),
    HEALTH(Str::health_care),
    BEAUTY(Str::hair_beauty),
    PROFESSIONAL(Str::professional_services),
    EDUCATION(Str::education_sports),
    TRADES(Str::technical_services_repairs),
    OTHER(Str::other),;

    val persianName: String get() = persianNameRef()
}
