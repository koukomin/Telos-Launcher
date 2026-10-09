# Office editing and PDF search

The [document viewer](./documents) of Telos Photos shows Word, Excel, PowerPoint and OpenDocument files with their
structure and lets you **edit and save** them. It is not a full office suite: the edits are limited to text and
simple formatting, and page layout and fonts are not reproduced.

## What you can do per format

| Format | Shown | You can edit | Saved as |
| --- | --- | --- | --- |
| Word `.docx` | Headings, paragraphs, lists, tables, pictures | Paragraph text, **bold**, *italic*, add and delete paragraphs, table cells, add table rows | `.docx` |
| Excel `.xlsx` | One table per sheet | Cell values, numbers and formulas (text starting with `=`) | `.xlsx` |
| PowerPoint `.pptx` | Titles, text boxes and pictures per slide | Text of the text boxes, delete slides | `.pptx` |
| OpenDocument `.odt` `.ods` `.odp` | Same as the matching Office format | Same as the matching Office format | The same OpenDocument format |
| Plain text and code | Monospace | Everything | The same file |
| PDF | Page by page | Not edited here: use the [PDF tools](./pdf-tools) | |

Telos edits the XML inside the file and copies everything else (styles, images, themes, macros) unchanged. This
keeps the file intact, but a feature that Telos does not know is simply left as it is.

## Editing

1. Tap **Edit**.
2. Tap a paragraph, a cell or a text box and type. Use **Bold** and **Italic** on a paragraph.
3. Tap **Save**.

| Control | What it does |
| --- | --- |
| **Add paragraph below** / **Delete paragraph** | Change the structure of a Word document |
| **Add row** | Add a row to a table |
| **Edit cell A1** | Opens a dialog with **Value**. Type text, a number or a formula starting with `=` |
| **Delete slide** | Remove a slide from a presentation |
| **Save as a copy** | Write a new file and leave the original untouched |
| **Undo last save** | Restore the version before your last save |
| **Cancel** | Asks **Discard changes?** if you changed something |

::: info A cell with a formula
If a cell contains a formula, Telos shows "This cell contains a formula (...). A new value replaces it." before you
overwrite it. In `.xlsx` and OpenDocument sheets text that starts with `=` is stored as a formula; Telos does not
recalculate, the app that opens the file next does.
:::

## Safe saving

- The file is first written **completely into the cache**. Only a finished file replaces your original, so a failed
  save never cuts the original.
- If the sender does not allow writing back, Android's **Save as** dialog appears.
- **Undo last save** keeps the previous version of the last save so you can go back once.

## Old binary files (.doc, .xls, .ppt)

The binary formats of Office 97 to 2003 are read by a built-in reader. They are shown **read-only**, with text and
values extracted as far as possible. They cannot be saved because the binary format is a file system inside a file
that depends on exact offsets and on features Telos cannot reproduce without damaging the document.

Instead Telos offers **Convert to .docx / .xlsx / .pptx and edit**. This creates a **new file**; formatting,
pictures and embedded objects of the old file are not carried over, and the original stays as it is.

## Searching in a PDF

Tap **Search in PDF** in the PDF viewer, type a word and press search. Telos reads the text of each page the first
time it is needed ("Searching... page N"), shows the number of results and lets you jump to a page. **Go to page**
accepts a page number between 1 and the page count.

The search is the same as everywhere in Telos: [accents and case do not matter, and Greek and Greeklish match each
other](../search-in-apps).

A scanned PDF has no text layer; Telos says "This PDF has no searchable text".

## Limits

| Topic | Detail |
| --- | --- |
| Text files | Over 2 MB they are read-only; **Edit first 2 MB as a copy** saves a copy and never changes the original |
| Layout | Page layout and fonts are not reproduced; slide design is not reproduced |
| Password-protected Office files | Not supported |
| Macros, tracked changes, comments | Kept in the file, not shown and not editable |
| Charts and drawings | Kept in the file, not shown |
| Formula results | Not recalculated by Telos |
| Embedded objects of old `.doc`, `.xls`, `.ppt` | Not carried over by the conversion |

## Troubleshooting

| Problem | What to check |
| --- | --- |
| "This old Office file could not be read" | Use **Open with** |
| "Could not save" | Use **Save as a copy** |
| Edits look wrong in another app | That app recalculates formulas and applies its own layout; use **Undo last save** if needed |
| "This PDF has no searchable text" | The PDF is a scan |
