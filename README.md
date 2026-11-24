# Adobe PDF

A privacy-first, open-source native Android PDF workspace built from scratch.

## Implemented on main

- Native Kotlin + Jetpack Compose Android app
- Android Storage Access Framework PDF selection and export
- On-device PDF reader with continuous page rendering and pinch-to-zoom
- Page organizer with thumbnails, selection, reorder, delete, duplicate, extract and rotate operations
- Local PDF merge, first-page extraction and rotate-all-pages tools
- PDF editor overlays: add text, insert an image, add a highlight rectangle and add a typed italic signature
- Offline OCR using the bundled ML Kit Latin text model; create a searchable PDF by adding an invisible text layer to scanned pages
- Convert multiple images into a multi-page PDF
- Compress image-heavy PDFs by rendering pages to JPEG and rebuilding a smaller PDF
- PDF sticky-note annotations and clickable HTTP/HTTPS link annotations
- Edit standard PDF metadata: title, author, subject and keywords
- Password-protect PDFs with separate user and owner passwords (128-bit encryption)
- Edited files are exported as separate PDFs; the source document is not overwritten
- No account or cloud upload is required for these local operations

## Build

Use Android Studio with JDK 17 and Android SDK 35. Open the repository and run the `:app` configuration. GitHub Actions builds a debug APK and publishes it as a workflow artifact after CI passes.

## Next roadmap

- Free-position text placement with page selection and percentage-based coordinates
- Drawn signatures and richer annotation controls
- OCR for additional scripts/languages and improved text positioning
- Additional conversion formats (PDF to image, Office formats) and non-destructive compression that preserves selectable text and vector graphics
- Interactive AcroForm field filling, metadata clearing and permanent visual redaction
- Accessibility, dark theme, large-file testing and release hardening

## Feature references

- [Stirling PDF](https://stirling.com/app) — advanced processing, OCR, security and batch workflows
- [BentoPDF](https://www.bentopdf.com/) — local-first tool collection and page workflows
- [Sejda PDF Editor](https://www.sejda.com/pdf-editor) — document editing, annotations, forms and signatures

Compression currently rasterises page content, so selectable text, vector graphics and form elements are not preserved in the compressed copy. Keep the original when those properties matter. Annotation and web-link placement currently targets page 1 with a preset position. Free-position text uses page-relative percentages. Permanent redaction rebuilds all pages as images after painting the selected region, so the output no longer contains the original selectable text, vectors, annotations, links or form fields; keep the source file. AcroForm filling supports standard text-like fields and may not support every specialized field type. Password protection uses standard 128-bit PDF encryption; it is not a substitute for secure file sharing or a strong password. These products are feature references, not bundled source code, branding or assets. This project is independently implemented. Review third-party library licences and attribution requirements before redistribution.


## Phase 8–9 completion batch

- Page-relative text placement with configurable page, X/Y position and font size
- Interactive AcroForm field discovery and field-value updates
- Permanent redaction by rasterizing pages and replacing the selected region with black pixels
- Metadata fields can now be cleared by submitting empty values

The redaction workflow intentionally flattens the output to prevent recovery of the redacted page content from the PDF object layer. Verify redacted output visually before sharing; the source document is never overwritten.


- Handwritten signature drawing pad with vector stroke output
- Selected launcher icon concept B applied as an adaptive purple/cyan icon, with a legacy fallback for older Android versions
