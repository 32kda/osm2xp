# Downloads FlightGear terrain (BTG tiles) for a bounding box from the TerraSync
# master mirror into a local scenery folder, without running the simulator.
#
# NOTE: This Windows-only PowerShell helper is superseded by the cross-platform
#       Java tool at tools/DownloadFgTerrain.java (JDK 11+, runs on Windows and
#       Linux). Prefer the Java tool; this script is kept for convenience.
#
# The resulting layout matches what TerraSync maintains on disk:
#   <Root>/Terrain/<band>/<cell>/<bucket-index>.btg.gz
# e.g.  <Root>/Terrain/e080n50/e083n53/4318144.btg.gz
#
# Used by the FlightGear building elevation prober (FlightGearElevProber),
# which locates the tile for a lon/lat via FlightGearBucket and spawns
# `fgelev --use-vpb --tile-file <tile>` per sub-bucket.
#
# Usage:
#   .\tools\download_fg_terrain.ps1 -Bbox "77.4,51.2,88.5,56.1" -Root D:\Work\fg_scenery
#
# Parameters:
#   -Bbox         "lonMin,latMin,lonMax,latMax"
#   -Root         destination scenery root (default D:\Work\fg_scenery)
#   -BaseUrl      TerraSync mirror root (default https://terrasync.b-cdn.net, the fast
#                 WS2.0 CDN advertised via the terrasync.flightgear.org NAPTR record;
#                 the Cloudflare-fronted sourceforge master rate-limits bulk downloads)
#   -Concurrency  parallel 1-degree cells to fetch (default 8)
#   -Force        re-download tiles that already exist

param(
    [Parameter(Mandatory = $true)]
    [string]$Bbox,
    [string]$Root = "D:\Work\fg_scenery",
    [string]$BaseUrl = "https://terrasync.b-cdn.net",
    [int]$Concurrency = 8,
    [switch]$Force
)

$ErrorActionPreference = "Stop"

function Get-Curl {
    $curl = Get-Command curl.exe -ErrorAction SilentlyContinue
    if (-not $curl) { throw "curl.exe is required." }
    return $curl.Source
}

function Get-CellDirIndex {
    param([string]$cellUrl, [string]$curl)
    $idx = & $curl -sL --max-time 60 "$cellUrl/.dirindex" 2>$null
    if ($LASTEXITCODE -ne 0) { return $null }
    if (-not $idx) { return $null }
    return $idx -join "`n"
}

function Get-CellTiles {
    param($cell, [string]$baseUrl, [string]$curl)
    $band = "{0}{1:000}{2}{3:00}" -f ($(if ([int]$cell.Lon -ge 0) { 'e' } else { 'w' }), [Math]::Abs([Math]::Floor([int]$cell.Lon / 10) * 10),
        $(if ([int]$cell.Lat -ge 0) { 'n' } else { 's' }), [Math]::Abs([Math]::Floor([int]$cell.Lat / 10) * 10))
    $cellPath = "{0}{1:000}{2}{3:00}" -f ($(if ([int]$cell.Lon -ge 0) { 'e' } else { 'w' }), [Math]::Abs([int]$cell.Lon),
        $(if ([int]$cell.Lat -ge 0) { 'n' } else { 's' }), [Math]::Abs([int]$cell.Lat))
    $idx = Get-CellDirIndex "$baseUrl/Terrain/$band/$cellPath" $curl
    if (-not $idx) { return @() }
    $tiles = @()
    foreach ($line in ($idx -split "`n")) {
        if ($line -match '^f:(\d+)\.btg\.gz:[0-9a-f]+:(\d+)$') {
            $tiles += [pscustomobject]@{
                Cell = $cellPath
                Band = $band
                Index = [long]$Matches[1]
                Size = [long]$Matches[2]
            }
        }
    }
    return $tiles
}

$curl = Get-Curl
$parts = $Bbox.Split(',')
if ($parts.Count -ne 4) { throw "Bbox must be 'lonMin,latMin,lonMax,latMax'" }
$lonMin = [Math]::Floor([double]$parts[0])
$lonMax = [Math]::Ceiling([double]$parts[2])
$latMin = [Math]::Floor([double]$parts[1])
$latMax = [Math]::Ceiling([double]$parts[3])

$cells = @()
for ($lon = $lonMin; $lon -le $lonMax; $lon++) {
    for ($lat = $latMin; $lat -le $latMax; $lat++) {
        $cells += [pscustomobject]@{ Lon = $lon; Lat = $lat }
    }
}

Write-Host "Enumerating terrain tiles from $BaseUrl for $($cells.Count) cells..."
$allTiles = @()
$cellIndex = 0
foreach ($cell in $cells) {
    $cellIndex++
    $tiles = Get-CellTiles $cell $BaseUrl $curl
    Write-Host "  cell $($cell.Lon)/$($cell.Lat): $($tiles.Count) tiles"
    $allTiles += $tiles
}
$totalBytes = ($allTiles | Measure-Object -Property Size -Sum).Sum
Write-Host "Found $($allTiles.Count) tiles, $([Math]::Round($totalBytes / 1MB, 1)) MB."

$downloaded = 0
$skipped = 0
$failed = @()

$worker = {
    param($tile, $root, $baseUrl, $curl, $force)
    $dir = Join-Path $root ("Terrain\" + $tile.Band + "\" + $tile.Cell)
    New-Item -ItemType Directory -Path $dir -Force -ErrorAction SilentlyContinue | Out-Null
    $target = Join-Path $dir ($tile.Index.ToString() + ".btg.gz")
    if (-not $force -and (Test-Path $target) -and (Get-Item $target).Length -eq $tile.Size) {
        return "skip"
    }
    $tmp = $target + ".tmp"
    $backoff = @(1, 3, 7, 15, 30)
    for ($attempt = 0; $attempt -lt 5; $attempt++) {
        $code = & $curl -sL --max-time 120 -o $tmp -w "%{http_code}" "$baseUrl/Terrain/$($tile.Band)/$($tile.Cell)/$($tile.Index).btg.gz" 2>$null
        if ($code -eq "200" -and (Test-Path $tmp) -and (Get-Item $tmp).Length -eq $tile.Size) {
            Move-Item -Force $tmp $target
            return "ok"
        }
        Remove-Item $tmp -ErrorAction SilentlyContinue
        Start-Sleep -Seconds $backoff[$attempt]
    }
    return "fail"
}

$jobs = @{}
$next = 0
$progress = 0
while ($next -lt $allTiles.Count -or $jobs.Count -gt 0) {
    while ($jobs.Count -lt $Concurrency -and $next -lt $allTiles.Count) {
        $tile = $allTiles[$next]
        $next++
        $job = Start-Job -ScriptBlock $worker -ArgumentList $tile, $Root, $BaseUrl, $curl, $Force
        $jobs[$job.Id] = $tile
    }
    foreach ($id in @($jobs.Keys)) {
        $job = Get-Job -Id $id
        if ($job.State -ne "Running") {
            $result = Receive-Job -Id $id -Keep
            Remove-Job -Id $id
            $tile = $jobs[$id]
            $jobs.Remove($id)
            $progress++
            if ($result -eq "ok") { $downloaded++ }
            elseif ($result -eq "skip") { $skipped++ }
            else { $failed += "$($tile.Band)/$($tile.Cell)/$($tile.Index)" }
            if ($progress % 100 -eq 0) {
                Write-Host "  ... $progress/$($allTiles.Count) ($downloaded new, $skipped present)"
            }
        }
    }
    Start-Sleep -Milliseconds 100
}

Write-Host "Done: $downloaded downloaded, $skipped already present, $($failed.Count) failed."
if ($failed.Count -gt 0) {
    Write-Host "Failed tiles (first 20):"
    $failed | Select-Object -First 20 | ForEach-Object { Write-Host "  $_" }
}
