param()

$srcDir = Split-Path -Parent $PSScriptRoot
$tmpDir = "$env:TEMP\asime-build"
$outDir = "$srcDir\out"

if (Test-Path $tmpDir) {
    Remove-Item -Recurse -Force $tmpDir
}

Copy-Item -Recurse "$srcDir\*" $tmpDir -Exclude "node_modules"
Copy-Item -Recurse "$srcDir\node_modules" "$tmpDir\node_modules"

Push-Location $tmpDir
try {
    npx tsc -p tsconfig.json
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
} finally {
    Pop-Location
}

if (Test-Path "$tmpDir\out") {
    Copy-Item "$tmpDir\out\*" $outDir -Recurse -Force
}
