@echo off
setlocal
set "SRC=%~dp0READY_TO_USE\Eyad PDF.exe"
set "DEST=%LOCALAPPDATA%\Programs\Eyad PDF"
if not exist "%SRC%" (
  echo Eyad PDF.exe was not found in READY_TO_USE.
  pause
  exit /b 1
)
if not exist "%DEST%" mkdir "%DEST%"
copy /y "%SRC%" "%DEST%\Eyad PDF.exe" >nul
powershell -NoProfile -ExecutionPolicy Bypass -Command "$s=(New-Object -ComObject WScript.Shell).CreateShortcut([Environment]::GetFolderPath('Desktop')+'\Eyad PDF.lnk');$s.TargetPath='%DEST%\Eyad PDF.exe';$s.WorkingDirectory='%DEST%';$s.Save();$sm=[Environment]::GetFolderPath('StartMenu')+'\Programs\Eyad PDF.lnk';$s2=(New-Object -ComObject WScript.Shell).CreateShortcut($sm);$s2.TargetPath='%DEST%\Eyad PDF.exe';$s2.WorkingDirectory='%DEST%';$s2.Save()"
start "" "%DEST%\Eyad PDF.exe"
endlocal
