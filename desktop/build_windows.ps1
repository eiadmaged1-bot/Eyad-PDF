$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
python -m pip install --upgrade pip
python -m pip install -r requirements-build.txt
New-Item -ItemType Directory -Force build | Out-Null
python -c "from PIL import Image; im=Image.open(r'../app/src/main/res/mipmap-xxxhdpi/ic_launcher.png').convert('RGBA'); im.save(r'build/eyad_pdf.ico', sizes=[(16,16),(32,32),(48,48),(64,64),(128,128),(256,256)])"
python -m pytest -q
python -m PyInstaller --noconfirm --clean --distpath dist --workpath build/pyinstaller EyadPDF.spec
Write-Host "Built: $PSScriptRoot\dist\Eyad PDF.exe"
