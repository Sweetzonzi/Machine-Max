param(
  [string]$Endpoint = 'http://127.0.0.1:3000/bb-mcp',
  [string]$EvalFile,
  [string]$Tool,
  [string]$ArgsJson = '{}',
  [string]$SessionFile = (Join-Path $env:TEMP 'bb_mcp_session.txt'),
  [int]$TimeoutSec = 300
)

$ErrorActionPreference = 'Stop'
$headers = @{ 'Accept' = 'application/json, text/event-stream'; 'Content-Type' = 'application/json; charset=utf-8' }

function ConvertFrom-Sse([string]$text) {
  if ($text -match '^\s*[\[{]') { return $text }
  $lines = ($text -split "`r?`n") | Where-Object { $_ -match '^\s*data:' }
  return (($lines | ForEach-Object { $_ -replace '^\s*data:\s?', '' }) -join "`n")
}

function New-Session {
  $init = '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"dsh-skill","version":"1.0"}}}'
  $resp = Invoke-WebRequest -Uri $Endpoint -Method Post -Headers $headers -Body ([Text.Encoding]::UTF8.GetBytes($init)) -TimeoutSec 30
  $id = $resp.Headers['Mcp-Session-Id']
  if ($id -is [array]) { $id = $id[0] }
  if (-not $id) { throw "MCP initialize failed: $($resp.StatusCode)" }
  $h = $headers.Clone(); $h['Mcp-Session-Id'] = $id
  $note = '{"jsonrpc":"2.0","method":"notifications/initialized"}'
  Invoke-WebRequest -Uri $Endpoint -Method Post -Headers $h -Body ([Text.Encoding]::UTF8.GetBytes($note)) -TimeoutSec 30 | Out-Null
  return $id
}

function Clean-Js([string]$code) {
  $code = [regex]::Replace($code, '/\*[\s\S]*?\*/', '')
  $code = [regex]::Replace($code, '(?m)^\s*//.*$', '')
  $code = [regex]::Replace($code, '\s//[^\r\n]*', '')
  if ($code -match '//') { throw 'JS still contains // after comment stripping; move the text into a string without //' }
  if ($code -match 'console\.') { throw 'risky_eval rejects console.*; use return values instead' }
  return $code
}

$sessionId = $null
if (Test-Path $SessionFile) { $sessionId = (Get-Content $SessionFile -Raw).Trim() }
if (-not $sessionId) { $sessionId = New-Session; Set-Content -Path $SessionFile -Value $sessionId -NoNewline }

if ($EvalFile) {
  if (-not (Test-Path $EvalFile)) { throw "Eval file not found: $EvalFile" }
  $code = Clean-Js (Get-Content -Path $EvalFile -Raw -Encoding UTF8)
  $payload = @{ jsonrpc = '2.0'; id = 7; method = 'tools/call'
    params = @{ name = 'risky_eval'; arguments = @{ code = $code } } } | ConvertTo-Json -Depth 8 -Compress
} else {
  if (-not $Tool) { throw 'Provide -EvalFile or -Tool' }
  $payload = @{ jsonrpc = '2.0'; id = 7; method = 'tools/call'
    params = @{ name = $Tool; arguments = ($ArgsJson | ConvertFrom-Json) } } | ConvertTo-Json -Depth 20 -Compress
}

$h = $headers.Clone(); $h['Mcp-Session-Id'] = $sessionId
$body = [Text.Encoding]::UTF8.GetBytes($payload)
$resp = Invoke-WebRequest -Uri $Endpoint -Method Post -Headers $h -Body $body -TimeoutSec $TimeoutSec -SkipHttpErrorCheck

if ($resp.StatusCode -eq 404) {
  $sessionId = New-Session
  Set-Content -Path $SessionFile -Value $sessionId -NoNewline
  $h['Mcp-Session-Id'] = $sessionId
  $resp = Invoke-WebRequest -Uri $Endpoint -Method Post -Headers $h -Body $body -TimeoutSec $TimeoutSec -SkipHttpErrorCheck
}

if ($resp.StatusCode -ne 200) { throw "MCP HTTP $($resp.StatusCode): $($resp.Content)" }

$text = ConvertFrom-Sse $resp.Content
$obj = $null
try { $obj = $text | ConvertFrom-Json } catch { Write-Output $text; return }
if ($obj.error) { throw ($obj.error | ConvertTo-Json -Depth 8) }

foreach ($item in $obj.result.content) {
  if ($item.type -eq 'text') { Write-Output $item.text }
  elseif ($item.type -eq 'image') { Write-Output "[image $($item.mimeType) $([int]($item.data.Length/1024))KB]" }
  else { Write-Output "[$($item.type)]" }
}
if ($obj.result.isError) { Write-Output 'TOOL ERROR' }
