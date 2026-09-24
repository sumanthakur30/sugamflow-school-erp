@echo off
REM One-line helpers for CMD (not PowerShell).
REM Usage examples below - edit password/host if needed.

echo.
echo === Generate SQL for DBeaver (no RDS password needed) ===
echo.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp003-generate-sql-for-dbeaver.ps1" -ExportDir "D:\school\backups\demo-school-20260725-101932"
echo.
echo SQL folder: D:\school\backups\demo-school-20260725-101932\sql-dbeaver
echo Open 00_RUN_ORDER.txt then run each .sql in DBeaver on the matching database.
echo.
pause
