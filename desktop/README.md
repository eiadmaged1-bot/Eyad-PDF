# Eyad PDF Desktop v0.11.0 — Conversion Recovery

Native Windows conversion workspace focused on reliable Office → PDF conversion and batch merge.

## What changed

- PPTX/DOCX/XLSX conversion uses Microsoft Office native export when available.
- LibreOffice is the high-fidelity fallback.
- No ReportLab/text-reconstruction fallback for Office files.
- PPTX output is verified: slide count must equal PDF page count.
- Drag/drop multi-file queue with row reordering.
- `Save output in the same folder as each input file` checkbox.
- Custom output folder.
- `Convert` and `Convert & Merge` actions.
- Merge preserves visible list order and removes temporary conversion files.
- Collision-safe output naming; originals are not overwritten.

## Supported in this recovery release

Conversion to PDF: PPT/PPTX, DOC/DOCX, XLS/XLSX, ODT/ODS/ODP, RTF. PDFs can be mixed into `Convert & Merge`.

## Conversion engines

1. Microsoft PowerPoint/Word/Excel native COM export on Windows when available.
2. LibreOffice headless fallback.
3. If neither exists, conversion fails clearly rather than fabricating a low-fidelity PDF.

## Build

`build_windows.ps1` creates a windowed single-file `Eyad PDF.exe` with PyInstaller.
