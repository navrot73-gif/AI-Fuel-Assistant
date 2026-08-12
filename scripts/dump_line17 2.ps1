$file = 'app/src/main/java/com/navrot/aifuelassistant/ui/map/MapScreen.kt'
$lines = [System.IO.File]::ReadAllLines($file)
$line = $lines[171]
($line.ToCharArray() | ForEach-Object { '{0:X4}' -f [int]$_ }) -join ' '
Write-Output "---"
Write-Output $line