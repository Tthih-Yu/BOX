@echo off
chcp 65001 >nul
setlocal

echo ============================================
echo   斑马标签自动打印代理 - 构建脚本
echo ============================================
echo.

where dotnet >nul 2>nul
if %errorlevel%==0 (
    echo [方式1] 检测到 dotnet SDK，使用 dotnet 发布单文件 exe...
    dotnet publish AutoPrintAgent.csproj -c Release -r win-x64 --self-contained false -o dist
    if %errorlevel%==0 goto ok
)

echo.
echo 未检测到 dotnet SDK，请用以下任一方式构建：
echo.
echo   A. 安装 .NET SDK 后重跑本脚本： https://dotnet.microsoft.com/download
echo   B. 用 Visual Studio 打开 AutoPrintAgent.csproj，选择 Release 生成
echo   C. 用 MSBuild：
echo        msbuild AutoPrintAgent.csproj /p:Configuration=Release
echo.
goto end

:ok
echo.
echo 构建完成，输出目录： dist\AutoPrintAgent.exe
echo 把整个 dist 文件夹拷到打印电脑即可运行。
echo.

:end
endlocal
pause
