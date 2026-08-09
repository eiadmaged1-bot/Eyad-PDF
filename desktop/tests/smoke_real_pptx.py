import argparse
from pathlib import Path
from source.conversion import convert_to_pdf, pdf_page_count, pptx_slide_count

parser = argparse.ArgumentParser()
parser.add_argument("pptx")
parser.add_argument("outdir")
args = parser.parse_args()
result = convert_to_pdf(Path(args.pptx), Path(args.outdir), prefer_native=False, log=print)
expected = pptx_slide_count(Path(args.pptx))
actual = pdf_page_count(result.output)
size = result.output.stat().st_size
print(f"source_slides={expected} output_pages={actual} bytes={size} engine={result.engine}")
assert expected is not None and expected > 0
assert expected == actual
assert size > 512
print(result.output)
