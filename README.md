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
- Edited files are exported as separate PDFs; the source document is not overwritten
- No account or cloud upload is required for these local operations

## Build

Use Android Studio with JDK 17 and Android SDK 35. Open the repository and run the `:app` configuration. GitHub Actions builds a debug APK and publishes it as a workflow artifact after CI passes.

## Next roadmap

- Free-position editing controls and page-specific editor selection
- Drawn signatures, richer annotations, stamps and links
- OCR for additional scripts/languages and improved text positioning
- Image/PDF conversions and compression
- Forms, metadata, document security and redaction
- Accessibility, dark theme, large-file testing and release hardening

## Feature references

- [Stirling PDF](https://stirling.com/app) — advanced processing, OCR, security and batch workflows
- [BentoPDF](https://www.bentopdf.com/) — local-first tool collection and page workflows
- [Sejda PDF Editor](https://www.sejda.com/pdf-editor) — document editing, annotations, forms and signatures

These products are feature references, not bundled source code, branding or assets. This project is independently implemented. Review third-party library licences and attribution requirements before redistribution.
