$file = 'app/src/main/java/com/navrot/aifuelassistant/ui/map/MapScreen.kt'
$lines = [System.IO.File]::ReadAllLines($file)
for ($i = 0; $i -lt $lines.Length; $i++) {
    $line = $lines[$i]
    $chars = $line.ToCharArray()
    $hasHigh = $false
    foreach ($ch in $chars) {
        if ([int]$ch -gt 127) {
            $hasHigh = $true
            break
        }
    }
    if ($hasHigh) {
        $codes = ($chars | ForEach-Object { "$([int]$_)" }) -join ','
        Write-Output "Line $($i + 1): $line"
        Write-Output "CharCodes: $codes"
    }
}