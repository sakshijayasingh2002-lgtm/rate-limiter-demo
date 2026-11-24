# Proves the rate limit is shared across instances.
#
# /api/report allows 2 requests per 15 seconds. We send 9 requests with the SAME
# API key, rotating across the three instances (app1, app2, app3, app1, ...).
# If the limit were per-instance, each instance would allow 2 (6 in total).
# Because state lives in Redis, exactly 2 are allowed in total.

$key = "demo-" + [guid]::NewGuid().ToString("N").Substring(0, 8)   # fresh key each run, so old runs don't interfere
$ports = 8081, 8082, 8083
$allowed = 0
$rejected = 0

Write-Host "Using API key: $key"
Write-Host ""

for ($i = 1; $i -le 9; $i++) {
    $port = $ports[($i - 1) % $ports.Count]
    $out = curl.exe -s -w "|%{http_code}" -H "X-API-Key: $key" "http://localhost:$port/api/report"

    if (-not $out) {
        Write-Host ("Request {0} -> port {1}: no answer (is the stack running?)" -f $i, $port)
        continue
    }

    $code = $out.Split("|")[-1]
    if ($code -eq "200") {
        $served = [regex]::Match($out, '"servedBy":"([^"]+)"').Groups[1].Value
        Write-Host ("Request {0} -> port {1}: 200 ALLOWED  (served by {2})" -f $i, $port, $served)
        $allowed++
    }
    elseif ($code -eq "429") {
        Write-Host ("Request {0} -> port {1}: 429 REJECTED" -f $i, $port)
        $rejected++
    }
    else {
        Write-Host ("Request {0} -> port {1}: unexpected status {2}" -f $i, $port, $code)
    }
}

Write-Host ""
Write-Host "Allowed: $allowed   Rejected: $rejected   (limit is 2 in total)"
if ($allowed -eq 2 -and $rejected -eq 7) {
    Write-Host "PASS - the limit held across all three instances." -ForegroundColor Green
}
else {
    Write-Host "FAIL - expected exactly 2 allowed and 7 rejected." -ForegroundColor Red
}
