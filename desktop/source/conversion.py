from __future__ import annotations

import os
import shutil
import subprocess
import sys
import tempfile
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Iterable

from pypdf import PdfReader, PdfWriter

OFFICE_EXTENSIONS = {".ppt", ".pptx", ".doc", ".docx", ".xls", ".xlsx"}
LIBREOFFICE_EXTENSIONS = OFFICE_EXTENSIONS | {".odt", ".ods", ".odp", ".rtf"}
MERGE_INPUTS = LIBREOFFICE_EXTENSIONS | {".pdf"}

class ConversionError(RuntimeError):
    pass

@dataclass(frozen=True)
class ConversionResult:
    source: Path
    output: Path
    engine: str
    pages: int

def _which_soffice() -> str | None:
    candidates: list[str] = []
    env = os.environ.get("SOFFICE_PATH")
    if env:
        candidates.append(env)
    for name in ("soffice", "libreoffice"):
        found = shutil.which(name)
        if found:
            candidates.append(found)
    if sys.platform == "win32":
        for root in (os.environ.get("PROGRAMFILES"), os.environ.get("PROGRAMFILES(X86)")):
            if root:
                candidates.extend([
                    str(Path(root) / "LibreOffice" / "program" / "soffice.exe"),
                    str(Path(root) / "LibreOffice 24" / "program" / "soffice.exe"),
                    str(Path(root) / "LibreOffice 25" / "program" / "soffice.exe"),
                ])
    for candidate in candidates:
        if candidate and Path(candidate).exists():
            return str(Path(candidate))
    return None

def _native_office_available() -> bool:
    if sys.platform != "win32":
        return False
    try:
        import win32com.client  # noqa: F401
        return True
    except Exception:
        return False

def engine_summary() -> str:
    native = _native_office_available()
    soffice = _which_soffice()
    if native and soffice:
        return "Microsoft Office native export (when installed) + LibreOffice fallback"
    if native:
        return "Microsoft Office native export (when installed)"
    if soffice:
        return f"LibreOffice ({soffice})"
    return "No high-fidelity Office/PDF engine detected"

def supported_input(path: str | Path) -> bool:
    return Path(path).suffix.lower() in MERGE_INPUTS

def unique_path(path: Path) -> Path:
    if not path.exists():
        return path
    stem, suffix = path.stem, path.suffix
    n = 2
    while True:
        candidate = path.with_name(f"{stem} ({n}){suffix}")
        if not candidate.exists():
            return candidate
        n += 1

def pptx_slide_count(path: Path) -> int | None:
    if path.suffix.lower() != ".pptx":
        return None
    try:
        with zipfile.ZipFile(path, "r") as zf:
            return sum(1 for name in zf.namelist() if name.startswith("ppt/slides/slide") and name.endswith(".xml") and Path(name).stem.removeprefix("slide").isdigit())
    except Exception:
        return None

def pdf_page_count(path: Path) -> int:
    try:
        return len(PdfReader(str(path), strict=False).pages)
    except Exception as exc:
        raise ConversionError(f"Output is not a readable PDF: {path.name}: {exc}") from exc

def validate_pdf_output(source: Path, output: Path) -> int:
    if not output.exists():
        raise ConversionError(f"Conversion did not create {output.name}.")
    if output.stat().st_size < 512:
        raise ConversionError(f"Conversion created an invalid tiny PDF ({output.stat().st_size} bytes).")
    pages = pdf_page_count(output)
    if pages < 1:
        raise ConversionError("Converted PDF has no pages.")
    expected_slides = pptx_slide_count(source)
    if expected_slides is not None and expected_slides != pages:
        raise ConversionError(f"PPTX fidelity gate failed: source has {expected_slides} slides but PDF has {pages} pages. The output was rejected instead of saving a broken reconstruction.")
    return pages

def _convert_with_powerpoint(source: Path, output: Path) -> None:
    import pythoncom
    import win32com.client
    pythoncom.CoInitialize()
    app = None
    presentation = None
    try:
        app = win32com.client.DispatchEx("PowerPoint.Application")
        try:
            app.Visible = True
            app.WindowState = 2
        except Exception:
            pass
        presentation = app.Presentations.Open(str(source.resolve()), WithWindow=False)
        presentation.SaveAs(str(output.resolve()), 32)
    finally:
        try:
            if presentation is not None:
                presentation.Close()
        finally:
            if app is not None:
                app.Quit()
            pythoncom.CoUninitialize()

def _convert_with_word(source: Path, output: Path) -> None:
    import pythoncom
    import win32com.client
    pythoncom.CoInitialize()
    app = None
    doc = None
    try:
        app = win32com.client.DispatchEx("Word.Application")
        app.Visible = False
        app.DisplayAlerts = 0
        doc = app.Documents.Open(str(source.resolve()), ReadOnly=True)
        doc.ExportAsFixedFormat(str(output.resolve()), 17)
    finally:
        try:
            if doc is not None:
                doc.Close(False)
        finally:
            if app is not None:
                app.Quit()
            pythoncom.CoUninitialize()

def _convert_with_excel(source: Path, output: Path) -> None:
    import pythoncom
    import win32com.client
    pythoncom.CoInitialize()
    app = None
    book = None
    try:
        app = win32com.client.DispatchEx("Excel.Application")
        app.Visible = False
        app.DisplayAlerts = False
        book = app.Workbooks.Open(str(source.resolve()), ReadOnly=True)
        book.ExportAsFixedFormat(0, str(output.resolve()))
    finally:
        try:
            if book is not None:
                book.Close(False)
        finally:
            if app is not None:
                app.Quit()
            pythoncom.CoUninitialize()

def _convert_native_office(source: Path, output: Path) -> str:
    suffix = source.suffix.lower()
    if suffix in {".ppt", ".pptx"}:
        _convert_with_powerpoint(source, output)
        return "Microsoft PowerPoint"
    if suffix in {".doc", ".docx"}:
        _convert_with_word(source, output)
        return "Microsoft Word"
    if suffix in {".xls", ".xlsx"}:
        _convert_with_excel(source, output)
        return "Microsoft Excel"
    raise ConversionError(f"No native Microsoft Office route for {suffix}.")

def _convert_libreoffice(source: Path, output_dir: Path) -> tuple[Path, str]:
    soffice = _which_soffice()
    if not soffice:
        raise ConversionError("No high-fidelity Office converter is available. Install Microsoft Office/PowerPoint or LibreOffice. Eyad PDF will not fabricate a text-only PDF.")
    output_dir.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="eyad_pdf_lo_profile_") as profile:
        cmd = [soffice, "--headless", f"-env:UserInstallation=file:///{Path(profile).as_posix()}", "--convert-to", "pdf", "--outdir", str(output_dir.resolve()), str(source.resolve())]
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=240)
    produced = output_dir / f"{source.stem}.pdf"
    if proc.returncode != 0 or not produced.exists():
        detail = (proc.stderr or proc.stdout or "unknown LibreOffice error").strip()
        raise ConversionError(f"LibreOffice conversion failed: {detail}")
    return produced, "LibreOffice"

def convert_to_pdf(source: str | Path, output_dir: str | Path, *, prefer_native: bool = True, log: Callable[[str], None] | None = None) -> ConversionResult:
    source = Path(source).resolve()
    output_dir = Path(output_dir).resolve()
    if not source.exists():
        raise ConversionError(f"Input file not found: {source}")
    suffix = source.suffix.lower()
    if suffix == ".pdf":
        pages = pdf_page_count(source)
        return ConversionResult(source, source, "Existing PDF", pages)
    if suffix not in LIBREOFFICE_EXTENSIONS:
        raise ConversionError(f"Unsupported conversion input: {suffix or '(no extension)'}")
    output_dir.mkdir(parents=True, exist_ok=True)
    target = unique_path(output_dir / f"{source.stem}.pdf")
    temp_target = output_dir / f".{source.stem}.eyad-converting.pdf"
    temp_target.unlink(missing_ok=True)
    errors: list[str] = []
    engine = ""
    produced: Path | None = None
    if prefer_native and suffix in OFFICE_EXTENSIONS and _native_office_available():
        try:
            if log:
                log(f"Native Office export: {source.name}")
            _convert_native_office(source, temp_target)
            produced = temp_target
            engine = "Microsoft Office"
        except Exception as exc:
            errors.append(f"Microsoft Office: {exc}")
            temp_target.unlink(missing_ok=True)
            if log:
                log(f"Native Office unavailable/failed; trying LibreOffice: {exc}")
    if produced is None:
        if log:
            log(f"LibreOffice high-fidelity export: {source.name}")
        try:
            with tempfile.TemporaryDirectory(prefix="eyad_pdf_lo_output_") as lo_dir:
                lo_output, engine = _convert_libreoffice(source, Path(lo_dir))
                shutil.copy2(lo_output, temp_target)
            produced = temp_target
        except Exception as exc:
            errors.append(f"LibreOffice: {exc}")
            raise ConversionError("; ".join(errors)) from exc
    try:
        pages = validate_pdf_output(source, produced)
        if target.exists():
            target = unique_path(target)
        if produced.resolve() != target.resolve():
            produced.replace(target)
        if log:
            log(f"Verified {pages} page(s) → {target}")
        return ConversionResult(source, target, engine, pages)
    except Exception:
        produced.unlink(missing_ok=True)
        raise

def merge_pdfs(inputs: Iterable[str | Path], output: str | Path) -> Path:
    paths = [Path(p).resolve() for p in inputs]
    if not paths:
        raise ConversionError("No PDFs were provided for merge.")
    output = Path(output).resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    writer = PdfWriter()
    total = 0
    for path in paths:
        reader = PdfReader(str(path), strict=False)
        for page in reader.pages:
            writer.add_page(page)
            total += 1
    if total == 0:
        raise ConversionError("The selected PDFs contain no pages.")
    with open(output, "wb") as fh:
        writer.write(fh)
    if pdf_page_count(output) != total:
        output.unlink(missing_ok=True)
        raise ConversionError("Merged PDF verification failed; output was removed.")
    return output

def convert_and_merge(sources: Iterable[str | Path], output: str | Path, *, log: Callable[[str], None] | None = None) -> Path:
    srcs = [Path(p).resolve() for p in sources]
    if not srcs:
        raise ConversionError("Add at least one file first.")
    output = unique_path(Path(output).resolve())
    with tempfile.TemporaryDirectory(prefix="eyad_pdf_merge_") as temp_dir:
        converted: list[Path] = []
        for index, source in enumerate(srcs, start=1):
            if log:
                log(f"[{index}/{len(srcs)}] {source.name}")
            if source.suffix.lower() == ".pdf":
                pdf_page_count(source)
                converted.append(source)
            else:
                result = convert_to_pdf(source, temp_dir, log=log)
                converted.append(result.output)
        if log:
            log(f"Merging {len(converted)} document(s) in list order…")
        merged = merge_pdfs(converted, output)
        if log:
            log(f"Verified merged output: {merged}")
        return merged
