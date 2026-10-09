"""
accounting/pricing.py

What a buyer actually pays for a plan or pack, line by line.

A plan's/pack's ``price`` is what Noobatyar keeps. Tax and the payment
channel's cut are added on top, so the net is the same whichever way the
owner pays; the app shows these lines in a confirm dialog before the
gateway or store opens (``checkout_quote`` view).

* zibal — 10% VAT on the price, then Zibal's fee (1%, floor 2,000 / ceiling
  20,000 toman, plus 10% VAT on the fee) grossed up so that after Zibal takes
  it the price + VAT remain.
* myket — Myket takes 10% VAT out of the store price, then keeps 15% of the
  rest and 85% of the Shaparak fee comes out of our share
  (https://myket.ir/kb/pages/developers-share-fa/). The store price is set by
  hand in the Myket panel per SKU and must equal ``total`` here — run
  ``manage.py myket_prices`` to list them.

All amounts are toman; totals are rounded up to a whole 1,000.
"""

import math

VAT_RATE = 0.10

ZIBAL_FEE_RATE = 0.01
ZIBAL_FEE_MIN = 2_000
ZIBAL_FEE_MAX = 20_000

MYKET_DEVELOPER_SHARE = 0.85
SHAPARAK_FEE_RATE = 0.0002
SHAPARAK_FEE_MIN = 120
SHAPARAK_FEE_MAX = 4_000

ROUND_TO = 1_000

# Nothing paid is sold below this: under it Zibal's 2,000-toman minimum fee and
# the rounding eat most of the price. Enforced on Plan/AddOnPack.clean().
MIN_SELLABLE_PRICE = 10_000

CHANNEL_ZIBAL = "zibal"
CHANNEL_MYKET = "myket"
CHANNELS = (CHANNEL_ZIBAL, CHANNEL_MYKET)


def _round_up(amount: float) -> int:
    return int(math.ceil(amount / ROUND_TO) * ROUND_TO)


def zibal_fee(gross: int) -> int:
    """What Zibal keeps from a ``gross`` toman payment, VAT on the fee included."""
    fee = min(ZIBAL_FEE_MAX, max(ZIBAL_FEE_MIN, gross * ZIBAL_FEE_RATE))
    return int(math.ceil(fee * (1 + VAT_RATE)))


def myket_net(gross: int) -> float:
    """What Myket settles to us from a ``gross`` toman store price."""
    shaparak = min(SHAPARAK_FEE_MAX, max(SHAPARAK_FEE_MIN, gross * SHAPARAK_FEE_RATE))
    return gross / (1 + VAT_RATE) * MYKET_DEVELOPER_SHARE - shaparak * MYKET_DEVELOPER_SHARE


def quote(base: int, channel: str) -> dict:
    """Line items and total for selling something priced ``base`` via ``channel``."""
    base = int(base)
    if base <= 0:
        return {"channel": channel, "base": base, "vat": 0, "fee": 0, "total": 0}

    if channel == CHANNEL_MYKET:
        total = _round_up((base / MYKET_DEVELOPER_SHARE + SHAPARAK_FEE_MIN) * (1 + VAT_RATE))
        while myket_net(total) < base:
            total += ROUND_TO
        vat = round(total - total / (1 + VAT_RATE))
    else:
        vat = round(base * VAT_RATE)
        need = base + vat
        total = need
        while total - zibal_fee(total) < need:
            total = need + zibal_fee(total)
        total = _round_up(total)

    return {
        "channel": channel,
        "base": base,
        "vat": vat,
        "fee": total - base - vat,
        "total": total,
    }


def plan_base(plan) -> int:
    return plan.discount_price if plan.discount_price is not None else plan.price
