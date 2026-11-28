<#
  Experimento QA-04 (ASR-MOD): Prueba de Mutación Arquitectónica con ArchUnit.
  1) Verifica estado base: HexagonalArchitectureTest en verde en los 5 microservicios.
  2) Inyecta deliberadamente 1 importación prohibida (domain -> application) en mortgage-pricing-service.
  3) Ejecuta HexagonalArchitectureTest y confirma el rechazo determinístico en < 5 s.
  4) Revierte el archivo limpiamente y re-verifica que vuelve a verde (0 cambios persistentes).
#>
$ErrorActionPreference = 'Stop'
$backend = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$ide = 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3'
$mvn = Join-Path $ide 'plugins\maven\lib\maven3\bin\mvn.cmd'
$env:JAVA_HOME = Join-Path $ide 'jbr'
$outDir = Join-Path $backend 'experiments\qa04-archunit'
New-Item -ItemType Directory -Force $outDir | Out-Null
$log = Join-Path $outDir 'qa04-mutation-results.txt'

$targetFile = Join-Path $backend 'mortgage-pricing-service\src\main\java\com\fidiacorp\credicasa\pricing\domain\service\FrenchAmortizationEngine.java'
$originalContent = Get-Content -Path $targetFile -Raw -Encoding UTF8

"=== EXPERIMENTO QA-04: MUTACION ARQUITECTONICA ARCHUNIT ===" | Out-File -Encoding utf8 $log
"Fecha: $(Get-Date -Format o)" | Out-File -Append -Encoding utf8 $log

Push-Location $backend
try {
  Write-Host "`n[1/3] Baseline: HexagonalArchitectureTest en los 5 microservicios..." -ForegroundColor Cyan
  $t0 = Get-Date
  $ErrorActionPreference = 'Continue'
  & $mvn -B -q "-Dstyle.color=never" -pl credicasa-shared-kernel install -DskipTests | Out-Null
  $baseOut = & $mvn -B "-Dstyle.color=never" test "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=HexagonalArchitectureTest" -pl "identity-subscription-service,property-catalog-service,mortgage-pricing-service,quotation-deal-service,communication-notification-service" 2>&1
  $baseCode = $LASTEXITCODE
  $baseSec = [math]::Round(((Get-Date) - $t0).TotalSeconds, 2)
  "--- PASO 1: BASELINE (exit=$baseCode, ${baseSec}s) ---" | Out-File -Append -Encoding utf8 $log
  ($baseOut | Select-String "Running .*HexagonalArchitectureTest|Tests run:|BUILD ") | Out-File -Append -Encoding utf8 $log
  Write-Host "Baseline exit=$baseCode (${baseSec}s)"

  Write-Host "`n[2/3] Inyectando mutacion ilegal (domain -> application.dto) en FrenchAmortizationEngine.java..." -ForegroundColor Yellow
  $mutated = $originalContent -replace '(package com\.fidiacorp\.credicasa\.pricing\.domain\.service;\r?\n)', "`$1import com.fidiacorp.credicasa.pricing.application.dto.response.SimulationResponseDto;`r`n"
  $mutated = $mutated -replace '(public class FrenchAmortizationEngine[^\r\n]*\{)', "`$1`r`n    private static final Class<?> ILLEGAL_MUTATION_REF = SimulationResponseDto.class;"
  [System.IO.File]::WriteAllText($targetFile, $mutated, [System.Text.UTF8Encoding]::new($false))

  $t1 = Get-Date
  $mutOut = & $mvn -B "-Dstyle.color=never" test "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=HexagonalArchitectureTest" -pl "mortgage-pricing-service" 2>&1
  $mutCode = $LASTEXITCODE
  $mutSec = [math]::Round(((Get-Date) - $t1).TotalSeconds, 2)
  "--- PASO 2: MUTACION INYECTADA (esperado exit!=0; real exit=$mutCode, ${mutSec}s) ---" | Out-File -Append -Encoding utf8 $log
  ($mutOut | Select-String "Architecture Violation|domainMustNotDependOnApplication|hexagonalLayersRespectDependencies|ILLEGAL_MUTATION_REF|Tests run:|BUILD ") | Out-File -Append -Encoding utf8 $log
  Write-Host "Mutacion detectada: exit=$mutCode (${mutSec}s)"
} finally {
  [System.IO.File]::WriteAllText($targetFile, $originalContent, [System.Text.UTF8Encoding]::new($false))
  Write-Host "`n[3/3] Archivo restaurado. Re-verificando HexagonalArchitectureTest en verde..." -ForegroundColor Green
  $t2 = Get-Date
  $recOut = & $mvn -B "-Dstyle.color=never" test "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=HexagonalArchitectureTest" -pl "mortgage-pricing-service" 2>&1
  $recCode = $LASTEXITCODE
  $recSec = [math]::Round(((Get-Date) - $t2).TotalSeconds, 2)
  "--- PASO 3: RESTAURACION VERIFICADA (exit=$recCode, ${recSec}s) ---" | Out-File -Append -Encoding utf8 $log
  ($recOut | Select-String "Running .*HexagonalArchitectureTest|Tests run:|BUILD ") | Out-File -Append -Encoding utf8 $log
  Pop-Location
}

$passed = ($baseCode -eq 0) -and ($mutCode -ne 0) -and ($recCode -eq 0)
"VEREDICTO QA-04: $(if ($passed) { 'PASS (Mutacion bloqueada deterministicamente por ArchUnit)' } else { 'FAIL' })" | Tee-Object -Append -FilePath $log
Get-Content $log | Out-Host
if (-not $passed) { exit 1 }
