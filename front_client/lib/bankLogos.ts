
import { t } from '@/lib/i18n';
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
  '603799': { get name() { return t('bankMelliIran'); }, key: 'melli' },
  '589210': { get name() { return t('bankSepah'); }, key: 'sepah' },
  '610433': { get name() { return t('bankMellat'); }, key: 'mellat' },
  '589463': { get name() { return t('refahKargaranBank'); }, key: 'refah' },
  '585983': { get name() { return t('tejaratBank'); }, key: 'tejarat' },
  '627353': { get name() { return t('tejaratBank'); }, key: 'tejarat' },
  '603770': { get name() { return t('keshavarziBank'); }, key: 'keshavarzi' },
  '639217': { get name() { return t('keshavarziBank'); }, key: 'keshavarzi' },
  '628023': { get name() { return t('bankMaskan'); }, key: 'maskan' },
  '627760': { get name() { return t('postBankOfIran'); }, key: 'post' },
  '502908': { get name() { return t('toseeTaavonBank'); }, key: 'tosee_taavon' },
  '627412': { get name() { return t('enBankEghtesadNovin'); }, key: 'eghtesad_novin' },
  '622106': { get name() { return t('parsianBank'); }, key: 'parsian' },
  '639194': { get name() { return t('parsianBank'); }, key: 'parsian' },
  '502229': { get name() { return t('pasargadBank'); }, key: 'pasargad' },
  '639347': { get name() { return t('pasargadBank'); }, key: 'pasargad' },
  '627488': { get name() { return t('karafarinBank'); }, key: 'karafarin' },
  '621986': { get name() { return t('samanBank'); }, key: 'saman' },
  '639346': { get name() { return t('sinaBank'); }, key: 'sina' },
  '639607': { get name() { return t('sarmayehBank'); }, key: 'sarmayeh' },
  '636949': { get name() { return t('hekmatIranianBank'); }, key: 'hekmat_iranian' },
  '502938': { get name() { return t('dayBank'); }, key: 'dey' },
  '505785': { get name() { return t('iranZaminBank'); }, key: 'iran_zamin' },
  '504172': { get name() { return t('resalatQarzAlHasanehBank'); }, key: 'resalat' },
  '606373': { get name() { return t('mehrIranQarzAlHasaneh'); }, key: 'mehr_iran' },
  '627381': { get name() { return t('ansarBank'); }, key: 'ansar' },
  '502806': { get name() { return t('shahrBank'); }, key: 'shahr' },
  '505416': { get name() { return t('tourismBank'); }, key: 'tourism' },
  '603769': { get name() { return t('bankSaderatIran'); }, key: 'saderat' },
  '606256': { get name() { return t('melalCreditInstitution'); }, key: 'melal' },
  '207177': { get name() { return t('exportDevelopmentBank'); }, key: 'edbi' },
  '627648': { get name() { return t('exportDevelopmentBank'); }, key: 'edbi' },
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
