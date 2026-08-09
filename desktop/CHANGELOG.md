# Changelog

## v0.11.0 — 2026-08-09

### Fixed
- 🔴 Removed the defective Office → PDF text-reconstruction route that could turn a 9-slide PPTX into a 2-page A4 text PDF and discard slide images/layout.
- ✅ Added post-conversion PPTX slide/page parity verification.
- ✅ Conversion failure now rejects/deletes bad output instead of presenting it as success.

### Added
- ✅ Native Microsoft Office export with LibreOffice fallback.
- ✅ Drag/drop multi-file conversion queue.
- ✅ Reorder files for merge order.
- ✅ Same-as-input output checkbox.
- ✅ Custom output directory.
- ✅ Convert & Merge workflow.
- ✅ Collision-safe naming and temporary-file cleanup.
- ✅ Windows EXE build pipeline.
