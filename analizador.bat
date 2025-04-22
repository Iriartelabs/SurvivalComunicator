@echo off
setlocal enabledelayedexpansion

set OUTPUT_FILE=project_structure.txt
echo Analizando estructura del proyecto... > %OUTPUT_FILE%
echo Fecha: %date% %time% >> %OUTPUT_FILE%
echo. >> %OUTPUT_FILE%
echo Estructura de directorios: >> %OUTPUT_FILE%
echo ========================== >> %OUTPUT_FILE%
echo. >> %OUTPUT_FILE%

for /f "tokens=*" %%a in ('dir /s /b /a:-d') do (
    set "filepath=%%a"
    set "relativepath=!filepath:%CD%=!"
    echo !relativepath! >> %OUTPUT_FILE%
)

echo. >> %OUTPUT_FILE%
echo Resumen de tipos de archivos: >> %OUTPUT_FILE%
echo =========================== >> %OUTPUT_FILE%

for /f "tokens=*" %%a in ('dir /s /b /a:-d ^| findstr /i "\.java$ \.kt$ \.xml$ \.gradle$ \.properties$ \.md$"') do (
    set "filepath=%%a"
    set "ext=%%~xa"
    if "!ext!" == ".java" (set /a java_files+=1)
    if "!ext!" == ".kt" (set /a kotlin_files+=1)
    if "!ext!" == ".xml" (set /a xml_files+=1)
    if "!ext!" == ".gradle" (set /a gradle_files+=1)
    if "!ext!" == ".properties" (set /a properties_files+=1)
    if "!ext!" == ".md" (set /a md_files+=1)
)

echo Archivos Java: %java_files% >> %OUTPUT_FILE%
echo Archivos Kotlin: %kotlin_files% >> %OUTPUT_FILE%
echo Archivos XML: %xml_files% >> %OUTPUT_FILE%
echo Archivos Gradle: %gradle_files% >> %OUTPUT_FILE%
echo Archivos Properties: %properties_files% >> %OUTPUT_FILE%
echo Archivos Markdown: %md_files% >> %OUTPUT_FILE%

echo.
echo Análisis completo. Los resultados se han guardado en %OUTPUT_FILE%
echo.

endlocal