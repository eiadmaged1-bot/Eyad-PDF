# Test Report — Eyad PDF Desktop v0.11.0

## Real-file regression supplied by user

Input: `week-3 tissue repair.pptx`
- Source slides: 9.

Old app output: `week-3 tissue repair.pdf`
- Producer: ReportLab PDF Library.
- Pages: 2.
- Page geometry: A4 portrait, 595.276 × 841.89 pt.
- Size: 3,601 bytes.
- Result: 🔴 invalid conversion; slide layout/images were not preserved and slides were collapsed.

New high-fidelity LibreOffice export of the exact same PPTX:
- Pages: 9.
- Page geometry: 960.009 × 540 pt (16:9 slide geometry).
- Size: 616,911 bytes.
- Result: ✅ page-count fidelity gate passes (9 slides → 9 PDF pages).

## Automated source tests

- Supported/unsupported extension routing.
- Collision-safe output naming.
- PDF merge page-count verification.

## Still external

Native Windows EXE launch, drag/drop and Microsoft Office COM conversion require Windows execution evidence before being called fully verified.
