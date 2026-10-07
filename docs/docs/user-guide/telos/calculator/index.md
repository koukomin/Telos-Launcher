# Telos Calculator

A calculator with a scientific mode, a VAT tab, a unit and currency converter and a history with date and time.

::: tip At a glance
Standard and scientific keys, percent that works like on a pocket calculator, VAT that you can add or remove at any
rate, conversion of length, mass, area, volume, speed, temperature and currency, and a history you open by swiping
down on the display.
:::

## What it is

Telos Calculator is a [virtual app](../#how-the-built-in-apps-work), part of the launcher. It has no permissions, no
account and no network features of its own (currency rates come from the launcher's unit converter, see below).
Switching it off in [Telos Store](../store/) hides its icon and its Quick Settings tile stops opening it.

## Calculator

| Feature | Details |
| --- | --- |
| Standard keys | `+ − × ÷`, `%`, brackets (one key that opens or closes), decimal point, `AC`, backspace |
| Scientific keys | sin, cos, tan and their inverses (INV key), ln, log, `√`, `x!`, `x²`, `xʸ`, `π`, `e`. Shown automatically in landscape, or with the **f(x)** button in portrait |
| Degrees and radians | The **DEG / RAD** button switches the angle unit, the choice is remembered |
| Percent | `200 + 10%` is 220, `200 − 10%` is 180, `50%` is 0.5 |
| Live result | The result is shown under the expression while you type, `=` keeps it as the new expression |
| History | Swipe down on the display (or tap the clock) to see past calculations with date and time. Tap one to reuse its result. Up to 100 entries are kept on the device |

You can also type in expressions with a keyboard that is attached to the phone: functions are written as `sin(30)`,
`sqrt(16)`, `log2(8)`, `abs(-3)`, `exp(2)`.

## VAT

The **VAT** tab (and the two chips under the display of the calculator) adds or removes value added tax.

1. Type the **VAT rate** (24 by default, with chips for 24, 13, 6 and 0). The rate is remembered.
2. Choose **Add VAT** (the amount you type is without VAT) or **Remove VAT** (the amount you type already includes VAT).
3. Type the amount. It can be a calculation such as `12.5*3`.

The tab shows the amount without VAT, the VAT and the amount with VAT at once, rounded to cents so that the first two
add up to the third. Tap an amount to copy it. **Use the result from the calculator** takes the current result as the
amount, and the button at the bottom sends the answer back to the calculator.

In the calculator itself, the chips **+ VAT 24%** and **− VAT 24%** appear when the expression has a result. They
replace the expression with the amount with or without VAT.

## Convert

Length, mass, area, volume, speed, temperature, time and data units use the same converters as the launcher search.
**Currency** is listed when exchange rates have been downloaded: turn on *Currencies* for the unit converter in
**Settings > Search** and the launcher fetches the rates in the background. Pick a category, type a value (a
calculation also works), choose the two units and tap the result to copy it. The arrow swaps the units.

## Quick Settings tile

Add the **Calculator** tile from the Quick Settings editor to open the calculator with one tap.

## Limitations

- The decimal point is always a point on the keypad, a comma typed on a keyboard is accepted as well.
- There is no floating window or mini-window mode yet.
- Results have 12 significant digits.
