# PDF tools

Telos Photos contains a set of 20 PDF tools. They run **entirely on your device**: no upload, no account, no
network. The feature set and the tool logic are adapted from
[PaperKnife+](https://github.com/potatameister/PaperKnifePlus) (GPL-3.0-or-later). PDF processing uses
[PdfBox-Android](https://github.com/TomRoush/PdfBox-Android) (Apache-2.0).

## Opening the tools

| From | What happens |
| --- | --- |
| The **PDF tools** button in the [document viewer](./documents) | The tools open with the PDF you are looking at |
| The Telos Photos start screen | The tool list opens empty; add files first |

Add one or more PDFs with **Add PDF** (or pictures with **Add images**). The list shows the page count and size of
each file. A file that cannot be parsed is marked as damaged and only the **Repair** tool accepts it. A
password-protected PDF asks for its password when it is added.

## The tools

| Group | Tool | What it does |
| --- | --- | --- |
| Organize | **Merge** | Combine several PDFs into one, in the order of the list |
| | **Split** | Split by ranges, every N pages or selected pages |
| | **Rotate pages** | Turn single pages or the whole document |
| | **Rearrange pages** | Change the order of the pages |
| | **Delete pages** | Remove pages you do not need |
| | **Bookmarks** | View, edit and add the table of contents |
| Optimize | **Compress** | Make the file smaller by shrinking its images. Shows the size before and after, or says when there was no gain |
| | **Grayscale** | Turn a colored PDF into black and white |
| | **Repair** | Rebuild a damaged PDF so it opens again |
| Security | **Protect** | Lock a PDF with a password (typed twice) |
| | **Unlock** | Remove the password from a PDF |
| Edit | **Watermark** | Stamp text or a picture on every page |
| | **Page numbers** | Number the pages |
| | **Sign** | Draw or insert your signature |
| | **Metadata** | View and edit title, author and more |
| Convert | **Images to PDF** | Make a PDF from photos and pictures |
| | **PDF to images** | Save pages as pictures in a ZIP |
| | **Extract images** | Save the pictures inside a PDF |
| | **PDF to text** | Copy the text out of a PDF |
| View and compare | **Preview pages** | Browse all pages of a PDF |
| | **Compare** | Put two versions side by side |

## Working with results

A tool never changes the original. It writes a **result file** in the cache. Then you can:

| Action | Meaning |
| --- | --- |
| **Save** / **Save to ...** | Write the result into a folder you chose once. **Choose a folder...** changes it, **Save as...** asks for a name each time |
| **Share** / **Open** | Send the result to another app |
| **Use as input for another tool** | Chain tools, for example Merge, then Compress, then Protect |
| **History** | A list of what you saved. **Clear all** empties it |

## Limits and privacy

- Everything stays on the device. Results in the cache are temporary until you save them.
- Passwords are used to open or lock the file and are not stored.
- **Compress** only shrinks images. A PDF that is mostly text and vector graphics may not get smaller, and the tool
  tells you so.
- **Repair** can rebuild the structure of a damaged file; it cannot restore content that is really lost.
- Very large PDFs depend on the memory of the phone. If a tool fails, the message "The operation failed" names the
  reason.

## Troubleshooting

| Problem | What to check |
| --- | --- |
| "Wrong password" | Type the password again; it is case sensitive |
| The file is marked damaged | Use **Repair** first |
| Compress says there is no gain | The file has few or no images |
| "Could not save" | Choose another folder with **Choose a folder...** |
