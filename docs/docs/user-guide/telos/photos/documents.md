# Document viewer

Telos Photos also opens documents. Android offers it for PDF, plain text and code, Office, OpenDocument, RTF and EPUB
files. It shows documents with their structure and can **edit and save** text files, Word, Excel, PowerPoint and
OpenDocument files, see [Office editing and PDF search](./office-editing). PDFs have their own set of
[PDF tools](./pdf-tools). It is not a full office suite.

## How documents reach the viewer

| From | Behavior |
| --- | --- |
| "Open with" in another app, for the types below | Telos Photos is listed as **Telos Photos** |
| [Telos Files](../files/browsing#opening-and-sharing-files) | PDF, text, code and RTF open directly. `docx`, `xlsx`, `pptx`, `odt`, `ods` and `epub` first show the archive dialog, where **Open with...** leads to Telos Photos |
| A file shared with a "view" action | Opens in the viewer |

The registered media types are PDF, plain text, Markdown, CSV, XML, JSON, RTF (`application/rtf` and `text/rtf`),
EPUB, and the Word, Excel, PowerPoint and OpenDocument types. File types are also recognized by their extension
when the sender gives a generic type.

## Formats

| Format | How it is shown |
| --- | --- |
| PDF | Page by page, pinch to zoom (1x to 4x), page counter, **Search in PDF**, **Go to page**, **PDF tools**. Password-protected PDFs ask for the password in the PDF tools |
| Plain text and code | Monospace, selectable. **Edit** and **Save** write back to the file |
| Word (`.docx`) | Headings, paragraphs, lists, tables and pictures. Editable |
| Excel (`.xlsx`) | One table per sheet, with formulas. Editable |
| PowerPoint (`.pptx`) | Titles, text boxes and pictures per slide. Editable |
| OpenDocument (`.odt`, `.ods`, `.odp`) | Like the matching Office format. Editable |
| Old Office (`.doc`, `.xls`, `.ppt`) | Read-only, with **Convert to ... and edit** |
| RTF | Extracted text |
| EPUB | Extracted readable text |

Recognised text and code extensions: `txt md markdown csv tsv log json xml html htm yml yaml ini conf prop
properties kt java py js css sh c cpp h rs go toml sql gradle srt vtt ass gpx kml tex bat`.

::: info Layout is not reproduced
Text, structure, tables and pictures are shown. Fonts, page layout and slide design are not reproduced. Old binary
files (`.doc`, `.xls`, `.ppt`) are read-only; they can be converted to a new `.docx`, `.xlsx` or `.pptx` file.
Encrypted Office files are not supported.
:::

## The screen

| Element | Behavior |
| --- | --- |
| Top bar | The file name and a back arrow |
| **Edit**, **Save**, **Cancel** | For text and editable Office files. See [Office editing](./office-editing) |
| **Open with** | Sends the file to another app through Android's chooser, with a read permission |
| Content | A progress circle while loading, then the document, or a message |
| Message line | "Saved", "Could not save", shown at the bottom for about 2.5 seconds |

### Messages you can see

| Message | Meaning |
| --- | --- |
| "Nothing to show in this file. Use Open with to open it in another app." | The file has no readable text |
| "This PDF is protected with a password." | Encrypted PDFs are not supported |
| "This file cannot be opened here (reason). Use Open with." | The file is damaged or unusual |
| "The file is longer, only the first 2 MB are shown." | A text file is longer than 2 MB |

## Editing text files

1. Tap **Edit**. The text becomes an editable monospace field.
2. Change it and tap **Save**. Telos writes the file back in place when the sender allows it.
3. If the file cannot be written back, Android's **Save as** dialog appears so you can save it as a new file.
4. **Cancel** leaves editing without saving.

::: tip Long files are read only
Text files are read only up to **2 MB**. If a file is longer, the **Edit** button is not shown, so the shortened
text can never be written back over the original. Open long files in another app to edit them.
:::

## Limits and behavior

| Topic | Detail |
| --- | --- |
| Text size | The first 2 MB, with a note when the file is longer |
| Office blocks | At most 20,000 paragraphs, headings or table blocks of an OpenDocument file, and 3,000 rows per table |
| PDF | Rendered with Android's own PDF renderer; processed with PdfBox-Android in the PDF tools |
| Cache | The document is copied into the app cache (`doc_view`) because a PDF needs a real file. Copies older than two hours are deleted |
| Searching in a document | In a PDF (**Search in PDF**) |
| Annotations | Not available |
| Printing | Not available. Use **Open with** |
| Layout of Office files | Not reproduced |
| Images inside documents | Not shown |

## Privacy and permissions

- The viewer needs no storage permission: the sender hands it the file.
- Nothing is uploaded. The copy in the cache stays on the device.
- The copy is plain text/bytes. Clear the Telos cache in Android settings if you opened something sensitive.

## Limitations

- No layout, fonts or slide design; macros, comments, charts and tracked changes are kept but not shown.
- Encrypted Office files cannot be opened. Old `.doc`, `.xls` and `.ppt` are read-only.
- No annotations in the viewer. No bookmarks (the PDF tools can edit them).
- Large spreadsheets and documents are cut at the limits above.

## Troubleshooting

| Problem | What to check |
| --- | --- |
| "Nothing to show in this file" | The file has no readable text. Use **Open with** |
| PDF says it is protected | Open the [PDF tools](./pdf-tools) and enter the password, or use Unlock |
| A `.docx` from Files shows an archive dialog | Choose **Open with...** and pick Telos Photos |
| "Could not save" | The sender did not allow writing. Use the **Save as** dialog |
| A CSV looks cramped | CSV is shown as plain text, not as a table |
| A `.doc` cannot be saved | Use **Convert to .docx and edit** |
