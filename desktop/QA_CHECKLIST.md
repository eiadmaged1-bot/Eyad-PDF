# QA Checklist — Eyad PDF Desktop v0.11.0

- [x] User-provided 9-slide PPTX converted with the new LibreOffice fidelity route.
- [x] Output PDF page count equals PPTX slide count (9/9).
- [x] Output retains slide page geometry (16:9 landscape rather than A4 text reconstruction).
- [x] Core merge regression test.
- [x] Output collision handling regression test.
- [x] Unsupported file rejection regression test.
- [ ] Exact Windows EXE native launch from GitHub Actions artifact.
- [ ] Drag/drop on user Windows machine.
- [ ] Microsoft PowerPoint COM route on user Windows machine if Office is installed.
- [ ] Same-input-folder write permission on user folders.
- [ ] 1920×1080 / 1366×768 / 1280×720 / small-window visual QA.
- [ ] Windows 100% / 125% / 150% DPI QA.
