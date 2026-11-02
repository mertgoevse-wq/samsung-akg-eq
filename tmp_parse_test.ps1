$ErrorActionPreference = "Continue"
$file = $args[0]
$out = $args[1]
$tokens = $null
$errors = $null
[void][System.Management.Automation.Language.Parser]::ParseFile($file, [ref]$tokens, [ref]$errors)
$sb = New-Object System.Text.StringBuilder
$sb.AppendLine("FILE: $file") | Out-Null
$sb.AppendLine("ERRORS: $($errors.Count)") | Out-Null
foreach ($e in $errors) {
    $ext = $e.Extent
    $sb.AppendFormat("line {0} col {1}: {2} | text=[{3}]`n", $ext.StartLineNumber, $ext.StartColumnNumber, $e.Message, $ext.Text) | Out-Null
}
[System.IO.File]::WriteAllText($out, $sb.ToString(), [System.Text.Encoding]::UTF8)
if ($errors.Count -ne 0) { exit 1 }
