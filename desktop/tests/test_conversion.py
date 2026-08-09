from pathlib import Path
import tempfile
from pypdf import PdfWriter

from source.conversion import unique_path, merge_pdfs, supported_input


def make_pdf(path: Path, pages: int):
    writer = PdfWriter()
    for _ in range(pages):
        writer.add_blank_page(width=612, height=792)
    with open(path, "wb") as fh:
        writer.write(fh)


def test_supported_inputs():
    assert supported_input("a.pptx")
    assert supported_input("a.docx")
    assert supported_input("a.pdf")
    assert not supported_input("a.exe")


def test_unique_path():
    with tempfile.TemporaryDirectory() as td:
        p = Path(td) / "a.pdf"
        p.write_bytes(b"x")
        assert unique_path(p).name == "a (2).pdf"


def test_merge_pages():
    with tempfile.TemporaryDirectory() as td:
        td = Path(td)
        a, b, out = td / "a.pdf", td / "b.pdf", td / "out.pdf"
        make_pdf(a, 2)
        make_pdf(b, 3)
        merge_pdfs([a, b], out)
        from source.conversion import pdf_page_count
        assert pdf_page_count(out) == 5
