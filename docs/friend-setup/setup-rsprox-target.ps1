<#
  Adds a private-server target to RSProx that points at someone else's RSMod server.
  Run on the machine that will run RSProx (Windows PowerShell):

    .\setup-rsprox-target.ps1 -ServerIp 203.0.113.5 -Modulus <hex from the host's .data/client.key>

  What it does:
    1. Downloads the Blurite 233 jav_config and world list.
    2. Rewrites the codebase + world list host to -ServerIp.
    3. Writes both next to RSProx's config and adds a target to proxy-targets.yaml.
#>
param(
    [Parameter(Mandatory)] [string] $ServerIp,
    [Parameter(Mandatory)] [string] $Modulus,
    [string] $Name = 'Isle of Souls',
    [int]    $Port = 43594,
    [int]    $Revision = 233,
    [string] $RsproxDir = (Join-Path $HOME '.rsprox')
)
$ErrorActionPreference = 'Stop'
$base = "https://client.blurite.io"
$dir = Join-Path $RsproxDir 'private-targets'
New-Item -ItemType Directory -Force $dir | Out-Null

# --- world list: 4B size, 2B count, then per world: 2B id, 4B props, host\0, activity\0, 1B location, 2B pop
$src = (Invoke-WebRequest "$base/world_list.ws" -UseBasicParsing).Content
if ($src -is [string]) { throw 'World list came back as text, expected bytes.' }
$hostStart = 12
$hostEnd = [Array]::IndexOf($src, [byte]0, $hostStart)
$newHost = [Text.Encoding]::ASCII.GetBytes($ServerIp)
$out = New-Object System.Collections.Generic.List[byte]
$out.AddRange([byte[]]$src[4..($hostStart - 1)])
$out.AddRange([byte[]]$newHost)
$out.AddRange([byte[]]$src[$hostEnd..($src.Length - 1)])
$payload = $out.Count
$size = [BitConverter]::GetBytes([int]$payload); [Array]::Reverse($size)
$final = [byte[]]($size + $out.ToArray())
$worldListPath = Join-Path $dir 'world_list.ws'
[IO.File]::WriteAllBytes($worldListPath, $final)

# --- jav_config: swap codebase + world list url
$worldListUri = ([Uri]$worldListPath).AbsoluteUri
$jav = (Invoke-WebRequest "$base/jav_local_$Revision.ws" -UseBasicParsing).Content
if ($jav -isnot [string]) { $jav = [Text.Encoding]::UTF8.GetString($jav) }
$jav = $jav -replace '(?m)^codebase=.*$', "codebase=http://$ServerIp/"
$jav = $jav -replace '(?m)^param=17=.*$', "param=17=$worldListUri"
$javPath = Join-Path $dir 'jav_config.ws'
[IO.File]::WriteAllText($javPath, $jav, (New-Object Text.UTF8Encoding $false))

# --- proxy-targets.yaml (appends if the file already exists)
$yamlPath = Join-Path $RsproxDir 'proxy-targets.yaml'
$entry = @"
  - name: $Name
    jav_config_url: $(([Uri]$javPath).AbsoluteUri)
    modulus: $Modulus
    revision: $Revision
    game_server_port: $Port
"@
if (Test-Path $yamlPath) {
    Add-Content $yamlPath "`n$entry"
} else {
    Set-Content $yamlPath "config:`n$entry"
}
Write-Host "Done. Restart RSProx and pick '$Name' in the target dropdown."
Write-Host "  $worldListPath`n  $javPath`n  $yamlPath"
