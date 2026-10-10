# Telos Calculator

A calculator with round keys, a scientific keypad, a VAT page, a unit and currency converter and a history with
date and time. The layout is modeled on the calculator of OxygenOS, the colors, shapes and fonts follow the Telos
theme.

::: tip At a glance
Standard and scientific keypad, percent that works like on a pocket calculator, VAT that you can add or remove at
any rate, twelve unit categories including currency, pressure, energy and numeral systems, and a history you open
by swiping down on the display.
:::

## What it is

Telos Calculator is a [virtual app](../#how-the-built-in-apps-work), part of the launcher. It has no permissions, no
account and no network features of its own (currency rates come from the launcher's unit converter, see below).
Switching it off in [Telos Store](../store/) hides its icon and its Quick Settings tile stops opening it. The texts
are translated to Greek, in other languages they are English.

## The calculator page

The top bar has four buttons: the **keypad switch** (the icon with `√ π e =` switches to the scientific keypad,
the one with `+ − × =` switches back), the **VAT** (a receipt with a percent sign), the **unit converter** (four squares) and the **menu** (three dots) with
**History** and **Copy result** (VAT has its own icon in the top row).

| Part | Details |
| --- | --- |
| Standard keypad | 4 columns: `AC`, `%`, backspace and `÷`, then the digits with `×`, `−` and `+`, and `00`, `0`, the decimal key and `=` |
| Scientific keypad | 5 columns: `sin`, `cos`, `tan`, `rad`, `deg` / `log`, `ln`, `(`, `)`, `inv` / `!`, `AC`, `%`, backspace, `÷` / `^`, `√`, `π`, `e` next to the digits. Shown automatically in landscape |
| `rad` and `deg` | The selected one is highlighted. The choice is remembered |
| `inv` | Turns `sin`, `cos`, `tan` into their inverses and `log`, `ln` into `10ˣ` and `eˣ` |
| Decimal key | Shows the decimal separator of your language (a comma in Greek). The calculator accepts both |
| Percent | `200 + 10%` is 220, `200 − 10%` is 180, `50%` is 0.5 |
| Live result | The result is shown under the expression while you type, `=` keeps it as the new expression |
| History | Swipe down on the display, or open it from the menu. It lists past calculations with date and time, tap one to reuse its result. Up to 100 entries are kept on the device |

With a keyboard attached you can also type functions: `sin(30)`, `sqrt(16)`, `log2(8)`, `abs(-3)`, `exp(2)`.

## VAT

Open **VAT** with the receipt icon in the top row, next to the scientific toggle and the unit converter, or use the two chips under the display of the calculator.

Tapping the VAT icon while the calculator shows something opens the page with the amount already filled: the result of the calculation if there is one (for example `12+8` gives 20), otherwise the number you typed. With an empty or zero display the page starts empty. You can edit the amount freely.

1. Type the **VAT rate** (24 by default, with chips for 24, 13, 6 and 0). The rate is remembered.
2. Choose **Add VAT** (the amount you type is without VAT) or **Remove VAT** (the amount you type already includes VAT).
3. Type the amount. It can be a calculation such as `12.5*3`.

The page shows the amount without VAT, the VAT and the amount with VAT at once, rounded to cents so that the first
two add up to the third. Tap an amount to copy it. **Use the result from the calculator** takes the current result as
the amount, and the button at the bottom sends the answer back to the calculator.

In the calculator itself, the chips **+ VAT 24%** and **− VAT 24%** appear when the expression has a result. They
replace the expression with the amount with or without VAT.

## Unit converter

The grid button opens the categories: **Currency**, **Length**, **Area**, **Volume**, **Weight**, **Temperature**,
**Speed**, **Pressure**, **Energy**, **Numeral system**, **Time** and **Data**. Pick one, type a value (a
calculation also works), choose the two units and tap the result to copy it. The arrows swap the units.

| Category | Units |
| --- | --- |
| Length, area, volume, weight, temperature, speed, time, data | The same units as the unit converter in the launcher search |
| Pressure | Pa, kPa, hPa, bar, mbar, atm, psi, mmHg, inHg |
| Energy | J, kJ, cal, kcal, Wh, kWh, BTU, eV |
| Numeral system | Binary, octal, decimal and hexadecimal, for whole numbers |
| Currency | Needs exchange rates. Turn on *Currencies* for the unit converter in **Settings > Search**, then the launcher fetches the rates in the background. Until then the page says so |

## Quick Settings tile

Add the **Calculator** tile from the Quick Settings editor to open the calculator with one tap. The floating
launcher has a Calculator tool too, see [Floating launcher](../launcher/desktop-and-overlays#floating-launcher).

## Limitations

- There is no floating window or mini-window mode yet.
- Results have 12 significant digits.
- The numeral system converter handles whole numbers up to 64 bits, without fractions.
