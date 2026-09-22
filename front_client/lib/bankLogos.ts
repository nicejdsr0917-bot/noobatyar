/**
 * lib/bankLogos.ts
 *
 * Detects which Iranian bank issued a card from its first six digits (the
 * "BIN" — bank identification number), so the checkout page can show that
 * bank's own logo next to the card number instead of a generic card icon.
 * This table also exists, kept manually in sync, as the Kotlin twin in
 * mobile_owner's core/utils/IranianBankDetector.kt (the owner app shows the
 * same logo live while typing a card number in Advanced Settings) — there is
 * no shared build step between the two stacks, so a BIN added here needs
 * adding there too.
 *
 * Logos: public/bank-logos/*.png, from the ISC-licensed
 * github.com/amastaneh/IranianBankLogos, whose README states the images are
 * exactly what the Central Bank of Iran itself publishes. Showing a bank's
 * own logo next to a real card number to identify who a payment actually
 * goes to is the same nominative use every Iranian payment product makes of
 * these — it doesn't imply endorsement, it identifies a real transaction.
 *
 * Deliberately NOT a complete list of every Iranian bank. Each entry below is
 * only here because its BIN is well-established, widely published
 * information; a small number of banks whose logo file exists in the source
 * repo (e.g. two ambiguously-named entries and one bi-national bank) were
 * left out rather than guessed at, since a wrong bank next to a real card
 * number is worse than no logo at all. If a bank a business actually uses is
 * missing, add it rather than assume the omission was deliberate forever.
 */

export interface BankInfo {
  /** Persian display name. */
  name: string;
  /** Matches a file in public/bank-logos/<key>.png. */
  key: string;
}

// Sorted by how likely the average business is to bank there — irrelevant to
// correctness (lookup is by exact BIN), but keeps this list skimmable.
const BIN_TABLE: Record<string, BankInfo> = {
  '603799': { name: 'بانک ملی ایران', key: 'melli' },
  '589210': { name: 'بانک سپه', key: 'sepah' },
  '610433': { name: 'بانک ملت', key: 'mellat' },
  '589463': { name: 'بانک رفاه کارگران', key: 'refah' },
  '585983': { name: 'بانک تجارت', key: 'tejarat' },
  '627353': { name: 'بانک تجارت', key: 'tejarat' },
  '603770': { name: 'بانک کشاورزی', key: 'keshavarzi' },
  '639217': { name: 'بانک کشاورزی', key: 'keshavarzi' },
  '628023': { name: 'بانک مسکن', key: 'maskan' },
  '627760': { name: 'پست بانک ایران', key: 'post' },
  '502908': { name: 'بانک توسعه تعاون', key: 'tosee_taavon' },
  '627412': { name: 'بانک اقتصاد نوین', key: 'eghtesad_novin' },
  '622106': { name: 'بانک پارسیان', key: 'parsian' },
  '639194': { name: 'بانک پارسیان', key: 'parsian' },
  '502229': { name: 'بانک پاسارگاد', key: 'pasargad' },
  '639347': { name: 'بانک پاسارگاد', key: 'pasargad' },
  '627488': { name: 'بانک کارآفرین', key: 'karafarin' },
  '621986': { name: 'بانک سامان', key: 'saman' },
  '639346': { name: 'بانک سینا', key: 'sina' },
  '639607': { name: 'بانک سرمایه', key: 'sarmayeh' },
  '636949': { name: 'بانک حکمت ایرانیان', key: 'hekmat_iranian' },
  '502938': { name: 'بانک دی', key: 'dey' },
  '505785': { name: 'بانک ایران زمین', key: 'iran_zamin' },
  '504172': { name: 'بانک قرض‌الحسنه رسالت', key: 'resalat' },
  '606373': { name: 'بانک قرض‌الحسنه مهر ایران', key: 'mehr_iran' },
  '627381': { name: 'بانک انصار', key: 'ansar' },
  '502806': { name: 'بانک شهر', key: 'shahr' },
  '505416': { name: 'بانک گردشگری', key: 'tourism' },
  '603769': { name: 'بانک صادرات ایران', key: 'saderat' },
  '606256': { name: 'موسسه اعتباری ملل', key: 'melal' },
  '207177': { name: 'بانک توسعه صادرات', key: 'edbi' },
  '627648': { name: 'بانک توسعه صادرات', key: 'edbi' },
};

/** Card-number digits → that bank's info, or null once six digits are in and
 *  still unrecognised. Returns null (not "loading") for fewer than six
 *  digits too — callers show nothing rather than a wrong guess mid-typing. */
export function detectBank(cardNumber: string): BankInfo | null {
  const digits = cardNumber.replace(/\D/g, '');
  if (digits.length < 6) return null;
  return BIN_TABLE[digits.slice(0, 6)] ?? null;
}

export function bankLogoSrc(key: string): string {
  return `/bank-logos/${key}.png`;
}
