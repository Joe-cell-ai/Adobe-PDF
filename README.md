# Adobe PDF

A privacy-first, open-source native Android PDF workspace built from scratch.

## Implemented in the current development branch

- Kotlin + Jetpack Compose Android application
- Android Storage Access Framework file selection and export
- Local PDF merge, first-page extraction, page rotation and text overlay
- On-device PDF reader using Android's `PdfRenderer`
- Continuous page viewing with pinch-to-zoom
- Page organizer with page thumbnails and selection
- Reorder pages, delete selected pages, duplicate selected pages, extract selected pages and rotate selected pages
- Modified documents are exported as new PDFs; the original source is left untouched
- No account or cloud upload required for these local operations

## Build

Use Android Studio with JDK 17 and Android SDK 35. Open the repository and run the `:app` configuration. GitHub Actions builds a debug APK and publishes it as a workflow artifact when CI passes.

## Roadmap

- More complete page navigation and editing controls
- Text and image placement, annotations, stamps and signatures
- OCR and searchable PDFs
- Image/PDF conversions and compression
- Forms, metadata, document security and redaction
- Accessibility, dark theme, large-file testing and release hardening

## Feature references

- [Stirling PDF](https://stirling.com/app) — advanced processing, OCR, security and batch workflows
- [BentoPDF](https://www.bentopdf.com/) — local-first tool collection and page workflows
- [Sejda PDF Editor](https://www.sejda.com/pdf-editor) — document editing, annotations, forms and signatures

These products are feature references, not bundled source code, branding or assets. This project is independently implemented. Review third-party library licences and attribution requirements before redistribution.
