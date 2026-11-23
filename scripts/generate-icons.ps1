param(
    [string]$FontName = 'Bahnschrift SemiBold Condensed',
    [string]$FallbackFont = 'Impact'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$res = Join-Path $root 'app\src\main\res'

try {
    $family = [System.Drawing.FontFamily]::new($FontName)
} catch {
    $family = [System.Drawing.FontFamily]::new($FallbackFont)
}

$master = 432
$measureBitmap = [System.Drawing.Bitmap]::new(8, 8)
$measureGraphics = [System.Drawing.Graphics]::FromImage($measureBitmap)

# 在 108dp 画布的安全区内选最大可用字号。
$maxWidth = 288.0
$maxBlockHeight = 252.0
$fontSize = 140
$font = $null
while ($fontSize -ge 8) {
    $candidate = [System.Drawing.Font]::new(
        $family,
        [single]$fontSize,
        [System.Drawing.FontStyle]::Regular,
        [System.Drawing.GraphicsUnit]::Pixel
    )
    $line1 = $measureGraphics.MeasureString('DARK', $candidate).Width
    $line2 = $measureGraphics.MeasureString('TOGGLE', $candidate).Width
    $lineHeight = $candidate.GetHeight($measureGraphics)
    if ([Math]::Max($line1, $line2) -le $maxWidth -and
        (2 * $lineHeight * 0.92) -le $maxBlockHeight) {
        $font = $candidate
        break
    }
    $candidate.Dispose()
    $fontSize -= 2
}
if ($null -eq $font) {
    throw 'Could not fit the text into the icon safe zone.'
}
$measureGraphics.Dispose()
$measureBitmap.Dispose()
Write-Output "Font: $($family.Name), size: $fontSize px @ ${master}px master"

function New-IconBitmap([bool]$BlackBackground, [int]$OffsetX = 0, [int]$OffsetY = 0) {
    $bitmap = [System.Drawing.Bitmap]::new(
        $master,
        $master,
        [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
    )
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAlias

    if ($BlackBackground) {
        $graphics.Clear([System.Drawing.Color]::Black)
    } else {
        $graphics.Clear([System.Drawing.Color]::Transparent)
    }

    if ($OffsetX -ne 0 -or $OffsetY -ne 0) {
        $graphics.TranslateTransform($OffsetX, $OffsetY)
    }

    $format = [System.Drawing.StringFormat]::new()
    $format.Alignment = [System.Drawing.StringAlignment]::Center
    $format.LineAlignment = [System.Drawing.StringAlignment]::Center

    $lineHeight = [single]($font.GetHeight($graphics) * 0.92)
    $top = [single](($master - 2 * $lineHeight) / 2.0)
    $rect1 = [System.Drawing.RectangleF]::new(0, $top, $master, $lineHeight)
    $rect2 = [System.Drawing.RectangleF]::new(0, $top + $lineHeight, $master, $lineHeight)

    $graphics.DrawString('DARK', $font, [System.Drawing.Brushes]::White, $rect1, $format)
    $graphics.DrawString('TOGGLE', $font, [System.Drawing.Brushes]::White, $rect2, $format)

    $format.Dispose()
    $graphics.Dispose()
    return $bitmap
}

function Get-InkBounds($bitmap) {
    $rect = [System.Drawing.Rectangle]::new(0, 0, $bitmap.Width, $bitmap.Height)
    $data = $bitmap.LockBits(
        $rect,
        [System.Drawing.Imaging.ImageLockMode]::ReadOnly,
        [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
    )
    try {
        $bytes = [byte[]]::new($data.Stride * $data.Height)
        [System.Runtime.InteropServices.Marshal]::Copy($data.Scan0, $bytes, 0, $bytes.Length)
    } finally {
        $bitmap.UnlockBits($data)
    }

    $minX = $bitmap.Width
    $maxX = -1
    $minY = $bitmap.Height
    $maxY = -1
    for ($y = 0; $y -lt $bitmap.Height; $y++) {
        $row = $y * $data.Stride
        for ($x = 0; $x -lt $bitmap.Width; $x++) {
            if ($bytes[$row + $x * 4 + 3] -gt 32) {
                if ($x -lt $minX) { $minX = $x }
                if ($x -gt $maxX) { $maxX = $x }
                if ($y -lt $minY) { $minY = $y }
                if ($y -gt $maxY) { $maxY = $y }
            }
        }
    }
    if ($maxX -lt 0) {
        throw 'No visible text pixels found.'
    }
    return [pscustomobject]@{
        MinX = $minX
        MaxX = $maxX
        MinY = $minY
        MaxY = $maxY
    }
}

# 先画一版探测图，按实际墨迹包围盒计算水平和竖向偏移，再正式绘制。
$probe = New-IconBitmap $false 0 0
$ink = Get-InkBounds $probe
$probe.Dispose()

$dx = [int][Math]::Round($master / 2.0 - (($ink.MinX + $ink.MaxX + 1) / 2.0))
$dy = [int][Math]::Round($master / 2.0 - (($ink.MinY + $ink.MaxY + 1) / 2.0))
Write-Output "Ink bbox: x=$($ink.MinX)..$($ink.MaxX) y=$($ink.MinY)..$($ink.MaxY); shift dx=$dx dy=$dy"

$foreground = New-IconBitmap $false $dx $dy
$legacy = New-IconBitmap $true $dx $dy

$finalInk = Get-InkBounds $foreground
Write-Output ("Final ink bbox: x={0}..{1} y={2}..{3}; center={4},{5}" -f `
    $finalInk.MinX, $finalInk.MaxX, $finalInk.MinY, $finalInk.MaxY,
    (($finalInk.MinX + $finalInk.MaxX + 1) / 2.0),
    (($finalInk.MinY + $finalInk.MaxY + 1) / 2.0))

function Save-ScaledPng($source, [int]$size, [string]$path) {
    $dest = [System.Drawing.Bitmap]::new(
        $size,
        $size,
        [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
    )
    $graphics = [System.Drawing.Graphics]::FromImage($dest)
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $graphics.DrawImage($source, 0, 0, $size, $size)
    $graphics.Dispose()

    $dir = Split-Path -Parent $path
    New-Item -ItemType Directory -Force $dir | Out-Null
    $dest.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $dest.Dispose()
}

$densities = @(
    @{ Name = 'mdpi'; Foreground = 108; Legacy = 48 },
    @{ Name = 'hdpi'; Foreground = 162; Legacy = 72 },
    @{ Name = 'xhdpi'; Foreground = 216; Legacy = 96 },
    @{ Name = 'xxhdpi'; Foreground = 324; Legacy = 144 },
    @{ Name = 'xxxhdpi'; Foreground = 432; Legacy = 192 }
)

foreach ($density in $densities) {
    Save-ScaledPng $foreground $density.Foreground (
        Join-Path $res "drawable-$($density.Name)\ic_launcher_foreground.png"
    )
    Save-ScaledPng $legacy $density.Legacy (
        Join-Path $res "mipmap-$($density.Name)\ic_launcher.png"
    )
    Save-ScaledPng $legacy $density.Legacy (
        Join-Path $res "mipmap-$($density.Name)\ic_launcher_round.png"
    )
}

$foreground.Dispose()
$legacy.Dispose()
$font.Dispose()

Write-Output "Icons written under $res"
