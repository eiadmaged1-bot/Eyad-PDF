# PROJECT_CONTEXT.md — Eyad PDF

**Last Updated:** 2026-08-09  
**Current Version:** `v0.11.0 Desktop Conversion Recovery`  
**Package Name:** `Eyad_PDF_Desktop_v0.11.0_Conversion_Recovery`  
**Current Status:** 🟡 Desktop conversion recovery implemented; real user PPTX regression passed 9/9 locally; Windows x64 EXE compiled and automated Windows conversion smoke passed on GitHub Actions. One physical Windows UI/drag-drop acceptance remains.  
**Last Stable Version:** `v0.8.1` is the recorded accepted Desktop visual baseline; `v0.9.0` is the recorded pre-rename Desktop rollback package. Exact local copies: `Unknown — verify locally`.  
**Immediate Next Action:** Perform one physical Windows acceptance pass with the delivered EXE: drag the exact user PPTX into the app, keep same-input checked, convert it, then verify Convert & Merge order/output.

## 1. Project purpose, users, current scope

Eyad PDF is a local-first PDF/document workspace for Windows and Android. This package is a focused Windows recovery release after a proven Office→PDF fidelity failure. Android code/release branches are not changed by this desktop recovery.

Current desktop scope for this release:
- high-fidelity Office document → PDF conversion;
- drag/drop batch queue;
- output destination control;
- convert-and-merge workflow;
- safe failure instead of low-fidelity reconstructed PDFs;
- Windows EXE packaging.

## 2. Version / package / stage

- Version: 0.11.0.
- Date: 2026-08-09.
- Package type: Desktop recovery source + Windows EXE build pipeline.
- Stage: 🟡 Release Candidate until one physical Windows acceptance.
- Previous accepted visual baseline: v0.8.1.
- Recorded rollback baseline: v0.9.0 pre-rename.

## 3. Architecture / stack / integrations

- Python 3.12 target.
- PySide6 native Windows UI; no browser shell.
- pypdf for PDF validation/merge.
- pywin32 COM automation on Windows.
- Conversion preference:
  1. Microsoft PowerPoint/Word/Excel native PDF export when available.
  2. LibreOffice headless conversion fallback.
  3. No ReportLab/text reconstruction fallback for Office→PDF.
- PyInstaller for `Eyad PDF.exe`.
- No database is introduced by this recovery release.
- No cloud API is required for conversion.

## 4. Important files / entry points / repository

- `source/app.py` — PySide6 desktop UI.
- `source/conversion.py` — conversion, fidelity validation and merge engine.
- `tests/test_conversion.py` — core regression tests.
- `tests/smoke_real_pptx.py` — real/synthetic PPTX fidelity smoke runner.
- `EyadPDF.spec` — single-file Windows EXE build.
- `build_windows.ps1` — local/CI Windows build.
- `INSTALL_OR_UPDATE.bat` — installs EXE under `%LOCALAPPDATA%\Programs\Eyad PDF\` and creates Desktop/Start Menu shortcuts.

Repository: `eiadmaged1-bot/Eyad-PDF`.
Branch: `desktop/conversion-recovery-v0.11.0`.
Release-candidate source commit: `d4205585a63cc535ebf14af8269cc42dd9bfffcb`.
GitHub Actions run: `31329854279`.
Artifact: `Eyad-PDF-Desktop-v0.11.0-Windows-EXE` (artifact id `9042628141`).
EXE SHA-256: `c6fc71a50abdd8263ac634609bc1c2102f1c5377ed651a5b52d949965e43988a`.

## 5. Approved requirements and UI/UX rules

- ✅ Drag/drop files directly into Converter.
- ✅ Multi-file queue.
- ✅ Drag rows to control merge order.
- ✅ Output folder chooser.
- ✅ Checkbox: save output in same folder as input.
- ✅ Convert.
- ✅ Convert & Merge.
- ✅ Native Windows EXE requirement.
- ✅ Preserve originals; do not overwrite input.
- ✅ High-fidelity PPTX conversion; no text-only reconstruction.
- ✅ Clear failure when no high-fidelity engine exists.
- ✅ Calm blue workspace family: canvas #F3F5F7, white surfaces, navy #18232D rail, primary #2854C7, 44px controls, no clipping.
- ✅ Layout must be scroll/resize safe; no tiny controls or giant dead cards.

## 6. Decisions and reasons

- Office→PDF now delegates rendering to an actual Office renderer because reconstructing extracted text cannot preserve slide layout, images, fonts and geometry.
- Microsoft Office native COM export is preferred on Windows for best fidelity when installed.
- LibreOffice remains the deterministic local fallback.
- PPTX conversions are validated by comparing source slide count to output PDF page count. A mismatch is a hard failure.
- Convert & Merge converts non-PDF inputs into a temporary directory, merges in visible list order, then cleans temporary files.
- Same-input-folder mode for Convert & Merge saves the single merged result beside the first file.

## 7. Rejected / superseded

- 🗑️ ReportLab Office→PDF reconstruction.
- 🗑️ Treating extracted PPTX text as a real slide-to-PDF conversion.
- 🗑️ Claiming PPTX→PDF “ready” without real layout/fidelity validation.
- 🗑️ Saving a page-count-mismatched PPTX conversion as successful output.

## 8. Feature status

- ✅ Conversion engine selection and safe fallback.
- ✅ PPTX slide-count fidelity gate.
- ✅ Drag/drop queue source implementation.
- ✅ Same-input/custom-output source implementation.
- ✅ Convert & Merge source implementation.
- ✅ PDF merge verification.
- ✅ Windows x64 GUI EXE compiled by GitHub Actions.
- ✅ Windows CI conversion smoke: synthetic 4-slide PPTX → verified 4-page PDF with LibreOffice.
- ⚠️ Physical Windows UI/DPI/drag-drop acceptance.
- ⚠️ Native Microsoft PowerPoint COM route acceptance on user machine.

## 9. Bugs / exact errors / fixes

### Critical conversion bug — reproduced 2026-08-09
Input: `week-3 tissue repair.pptx` (9 slides).
Old output: `week-3 tissue repair.pdf`.
Observed old PDF:
- ReportLab producer;
- 2 pages only;
- A4 portrait geometry;
- 3,601 bytes.

This proves the old route reconstructed text rather than rendering slides. It removed/collapsed visual slide content.

Fix:
- remove ReportLab/text reconstruction from Office→PDF path;
- use PowerPoint native export or LibreOffice;
- verify PPTX slide count equals output page count;
- reject/delete mismatch.

Real-file new-route result:
- 9 output pages for 9 slides;
- 960.009 × 540 pt slide geometry;
- 616,911 bytes.

## 10. Tests / results / remaining QA

✅ Real user PPTX conversion through LibreOffice: 9/9 pages.  
✅ PDF readable.  
✅ Slide geometry preserved.  
✅ Core merge and routing tests implemented.  
✅ Windows x64 EXE compile completed in GitHub Actions run `31329854279`.  
✅ Windows CI real conversion path: 4-slide PPTX → 4-page PDF.  
⚠️ Physical EXE launch/drag-drop + DPI matrix pending.  
⚠️ PowerPoint COM route pending Windows evidence.

## 11. Version history / baselines / rollback

- v0.8.1 — recorded accepted Desktop interaction/visual baseline.
- v0.9.0 — recorded OCR/performance Desktop rollback under legacy identity.
- v0.10.x — Eyad PDF branding + Android expansion; historical desktop conversion maturity claims are now corrected by this regression.
- v0.11.0 — current Desktop Conversion Recovery.

Rollback: do not overwrite older extracted packages. This release has no DB migration and does not modify user data.

## 12. User data / preservation

- Program install target: `%LOCALAPPDATA%\Programs\Eyad PDF\`.
- Outputs remain in user-selected folders or beside source files.
- Originals are never modified.
- Temporary merge/conversion files are deleted after completion.
- No secrets or personal documents belong in release ZIPs.

## 13. Attachment manifest

- `week-3 tissue repair.pptx` — real 9-slide regression input supplied by user; not included in public/source release.
- `week-3 tissue repair.pdf` — defective ReportLab output supplied by user; not included in public/source release.
- Future App Standard — packaging/update/rollback authority.
- Eyad Unified Workspace Theme v1.0.0 — shared visual authority.

## 14. Roadmap / priorities / risks

1. One exact physical Windows acceptance using the delivered EXE and real PPTX.
2. Confirm drag/drop and same-input output.
3. Confirm Convert & Merge with mixed PPTX + PDF.
4. If accepted, freeze conversion as stable and restore broader desktop tool catalog around this verified engine.
5. Later: optional bundled/managed LibreOffice engine pack if target PCs cannot be assumed to have Office/LibreOffice.

Risk: a standalone EXE cannot magically provide Microsoft Office/LibreOffice rendering binaries. The application therefore detects installed engines and fails safely if neither exists. A future bundled engine pack can remove that dependency at the cost of package size.

## 15. Exact stopping point

The replacement conversion/UI source is on GitHub branch `desktop/conversion-recovery-v0.11.0`. The exact user PPTX passed 9/9 locally, and GitHub Actions run `31329854279` compiled the Windows x64 EXE and passed core tests plus a 4-slide→4-page Windows LibreOffice smoke. Work stops before physical Windows UI/drag-drop acceptance.

## 16. Single best immediate next action

Install/run the delivered `Eyad PDF.exe` on the user Windows PC and perform one combined acceptance: drag the exact PPTX, Convert with same-input checked, then Convert & Merge in list order.

## 17. Startup instructions for next AI

Read this file completely before editing. Inspect the included source and compare it with this document. Preserve v0.8.1/v0.9.0 rollback history and do not restore ReportLab Office reconstruction. Keep Android work separate. Continue from the immediate next action. Report any discrepancy instead of guessing. Update `PROJECT_CONTEXT.md` again before the next ZIP.

## Instructions for the Next AI

- Read this complete file before editing anything.
- Inspect the included source code and compare it with this document.
- Continue from the recorded immediate next action.
- Preserve the last stable version and user data.
- Never restore rejected behavior.
- Report any discrepancy between the source code and this document.
- Update `PROJECT_CONTEXT.md` again before producing the next ZIP.
