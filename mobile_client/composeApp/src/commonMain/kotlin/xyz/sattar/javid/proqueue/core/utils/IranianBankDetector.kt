package xyz.sattar.javid.proqueue.core.utils

import org.jetbrains.compose.resources.DrawableResource
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.bank_ansar
import proqueue.composeapp.generated.resources.bank_dey
import proqueue.composeapp.generated.resources.bank_edbi
import proqueue.composeapp.generated.resources.bank_eghtesad_novin
import proqueue.composeapp.generated.resources.bank_hekmat_iranian
import proqueue.composeapp.generated.resources.bank_iran_zamin
import proqueue.composeapp.generated.resources.bank_karafarin
import proqueue.composeapp.generated.resources.bank_keshavarzi
import proqueue.composeapp.generated.resources.bank_maskan
import proqueue.composeapp.generated.resources.bank_mehr_iran
import proqueue.composeapp.generated.resources.bank_melal
import proqueue.composeapp.generated.resources.bank_mellat
import proqueue.composeapp.generated.resources.bank_melli
import proqueue.composeapp.generated.resources.bank_parsian
import proqueue.composeapp.generated.resources.bank_pasargad
import proqueue.composeapp.generated.resources.bank_post
import proqueue.composeapp.generated.resources.bank_refah
import proqueue.composeapp.generated.resources.bank_resalat
import proqueue.composeapp.generated.resources.bank_saderat
import proqueue.composeapp.generated.resources.bank_saman
import proqueue.composeapp.generated.resources.bank_sarmayeh
import proqueue.composeapp.generated.resources.bank_sepah
import proqueue.composeapp.generated.resources.bank_sina
import proqueue.composeapp.generated.resources.bank_shahr
import proqueue.composeapp.generated.resources.bank_tejarat
import proqueue.composeapp.generated.resources.bank_tosee_taavon
import proqueue.composeapp.generated.resources.bank_tourism

/**
 * Detects which Iranian bank issued a card from its first six digits (the
 * "BIN" — bank identification number), so the owner sees that bank's own
 * logo appear live while typing their card number in Advanced Settings —
 * the same confirmation every Iranian banking/payment app gives that the
 * number is going where it looks like it's going.
 *
 * This table also exists, kept manually in sync, as the TypeScript twin in
 * front_client's lib/bankLogos.ts (the checkout page shows the same logo
 * next to the business's card number). There is no shared build step between
 * Kotlin and that Next.js app, so a BIN added here needs adding there too.
 *
 * Logos: composeResources/drawable/bank_*.png, from the ISC-licensed
 * github.com/amastaneh/IranianBankLogos, whose README states the images are
 * exactly what the Central Bank of Iran itself publishes. Showing a bank's
 * own logo next to a real card number to identify who a payment actually
 * goes to is the same nominative use every Iranian payment product makes of
 * these — it doesn't imply endorsement, it identifies a real transaction.
 *
 * Deliberately NOT a complete list of every Iranian bank — see the matching
 * comment in bankLogos.ts for which entries were left out and why (a small
 * number of ambiguously-named logos and one bi-national bank whose BIN
 * wasn't confidently known; a wrong bank next to a real card number is worse
 * than no logo at all).
 */
data class BankInfo(val name: String, val logo: DrawableResource)

private val BIN_TABLE: Map<String, BankInfo> = mapOf(
    "603799" to BankInfo("بانک ملی ایران", Res.drawable.bank_melli),
    "589210" to BankInfo("بانک سپه", Res.drawable.bank_sepah),
    "610433" to BankInfo("بانک ملت", Res.drawable.bank_mellat),
    "589463" to BankInfo("بانک رفاه کارگران", Res.drawable.bank_refah),
    "585983" to BankInfo("بانک تجارت", Res.drawable.bank_tejarat),
    "627353" to BankInfo("بانک تجارت", Res.drawable.bank_tejarat),
    "603770" to BankInfo("بانک کشاورزی", Res.drawable.bank_keshavarzi),
    "639217" to BankInfo("بانک کشاورزی", Res.drawable.bank_keshavarzi),
    "628023" to BankInfo("بانک مسکن", Res.drawable.bank_maskan),
    "627760" to BankInfo("پست بانک ایران", Res.drawable.bank_post),
    "502908" to BankInfo("بانک توسعه تعاون", Res.drawable.bank_tosee_taavon),
    "627412" to BankInfo("بانک اقتصاد نوین", Res.drawable.bank_eghtesad_novin),
    "622106" to BankInfo("بانک پارسیان", Res.drawable.bank_parsian),
    "639194" to BankInfo("بانک پارسیان", Res.drawable.bank_parsian),
    "502229" to BankInfo("بانک پاسارگاد", Res.drawable.bank_pasargad),
    "639347" to BankInfo("بانک پاسارگاد", Res.drawable.bank_pasargad),
    "627488" to BankInfo("بانک کارآفرین", Res.drawable.bank_karafarin),
    "621986" to BankInfo("بانک سامان", Res.drawable.bank_saman),
    "639346" to BankInfo("بانک سینا", Res.drawable.bank_sina),
    "639607" to BankInfo("بانک سرمایه", Res.drawable.bank_sarmayeh),
    "636949" to BankInfo("بانک حکمت ایرانیان", Res.drawable.bank_hekmat_iranian),
    "502938" to BankInfo("بانک دی", Res.drawable.bank_dey),
    "505785" to BankInfo("بانک ایران زمین", Res.drawable.bank_iran_zamin),
    "504172" to BankInfo("بانک قرض‌الحسنه رسالت", Res.drawable.bank_resalat),
    "606373" to BankInfo("بانک قرض‌الحسنه مهر ایران", Res.drawable.bank_mehr_iran),
    "627381" to BankInfo("بانک انصار", Res.drawable.bank_ansar),
    "502806" to BankInfo("بانک شهر", Res.drawable.bank_shahr),
    "505416" to BankInfo("بانک گردشگری", Res.drawable.bank_tourism),
    "603769" to BankInfo("بانک صادرات ایران", Res.drawable.bank_saderat),
    "606256" to BankInfo("موسسه اعتباری ملل", Res.drawable.bank_melal),
    "207177" to BankInfo("بانک توسعه صادرات", Res.drawable.bank_edbi),
    "627648" to BankInfo("بانک توسعه صادرات", Res.drawable.bank_edbi),
)

/** [cardNumber] may contain spaces/dashes from a visual transformation — only
 *  digits are matched. Returns null for fewer than six digits too: callers
 *  show nothing rather than a guess while the owner is still typing. */
fun detectIranianBank(cardNumber: String): BankInfo? {
    val digits = cardNumber.filter { it.isDigit() }
    if (digits.length < 6) return null
    return BIN_TABLE[digits.take(6)]
}
