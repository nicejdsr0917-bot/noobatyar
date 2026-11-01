from django.test import SimpleTestCase

from business.public_policy import _mask_phone
from business.social_links import SocialLinkError, normalize_social_links


class NormalizeSocialLinksTests(SimpleTestCase):
    def test_pasted_links_become_handles(self):
        out = normalize_social_links({
            'instagram': 'https://www.instagram.com/salon.setareh/?igsh=abc',
            'telegram': '@setareh_salon',
            'eitaa': 'eitaa.com/setareh_salon',
            'bale': 'https://ble.ir/setareh',
            'rubika': 'rubika.ir/setareh.salon',
        })
        self.assertEqual(out, {
            'instagram': 'salon.setareh',
            'telegram': 'setareh_salon',
            'eitaa': 'setareh_salon',
            'bale': 'setareh',
            'rubika': 'setareh.salon',
        })

    def test_whatsapp_accepts_persian_digits_and_wa_me(self):
        self.assertEqual(normalize_social_links({'whatsapp': '۰۹۱۲ ۱۲۳ ۴۵۶۷'}), {'whatsapp': '09121234567'})
        self.assertEqual(normalize_social_links({'whatsapp': 'https://wa.me/989121234567'}), {'whatsapp': '09121234567'})

    def test_whatsapp_rejects_landline(self):
        with self.assertRaises(SocialLinkError):
            normalize_social_links({'whatsapp': '02112345678'})

    def test_website_gets_scheme(self):
        self.assertEqual(normalize_social_links({'website': 'setareh.ir'}), {'website': 'https://setareh.ir'})

    def test_website_rejects_other_schemes(self):
        for bad in ('javascript:alert(1)', 'ftp://x.ir', 'not a site'):
            with self.subTest(bad=bad), self.assertRaises(SocialLinkError):
                normalize_social_links({'website': bad})

    def test_blank_values_dropped(self):
        self.assertEqual(normalize_social_links({'instagram': '  ', 'telegram': None}), {})
        self.assertEqual(normalize_social_links(None), {})

    def test_bad_handle_and_unknown_kind(self):
        with self.assertRaises(SocialLinkError):
            normalize_social_links({'telegram': 'ab'})
        with self.assertRaises(SocialLinkError):
            normalize_social_links({'instagram': 'سالن'})
        with self.assertRaises(SocialLinkError):
            normalize_social_links({'myspace': 'x'})
        with self.assertRaises(SocialLinkError):
            normalize_social_links(['instagram'])

    def test_masked_with_phone(self):
        data = {'phone': '0912', 'extra_phones': ['0913'], 'social_links': {'telegram': 'abcde'}}
        _mask_phone(data)
        self.assertEqual(data['social_links'], {})
