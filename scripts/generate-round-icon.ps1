# Generates circular LibBook launcher icons (left blue / right white + LB).
param(
    [string]$Source = "$PSScriptRoot\..\app\src\main\ic_launcher_source.png",
    [string]$ResDir = "$PSScriptRoot\..\app\src\main\res",
    [string]$PreviewOut = "$PSScriptRoot\..\apk\icon-preview-on-phone.png"
)

Add-Type -AssemblyName System.Drawing

$Blue  = [System.Drawing.Color]::FromArgb(255, 33, 150, 243)
$White = [System.Drawing.Color]::White

function New-HqGraphics([System.Drawing.Bitmap]$bitmap) {
    $g = [System.Drawing.Graphics]::FromImage($bitmap)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    return $g
}

function New-BrandLogo([int]$side) {
    $bmp = New-Object System.Drawing.Bitmap $side, $side
    $g = New-HqGraphics $bmp

    $mid = [int]($side / 2)
    $blueBrush = New-Object System.Drawing.SolidBrush $Blue
    $whiteBrush = New-Object System.Drawing.SolidBrush $White
    $g.FillRectangle($blueBrush, 0, 0, $mid, $side)
    $g.FillRectangle($whiteBrush, $mid, 0, $side - $mid, $side)

    $sf = New-Object System.Drawing.StringFormat
    $sf.Alignment = [System.Drawing.StringAlignment]::Center
    $sf.LineAlignment = [System.Drawing.StringAlignment]::Center

    $fontSize = [single][Math]::Round($side * 0.26)
    $font = New-Object System.Drawing.Font("Arial", $fontSize, [System.Drawing.FontStyle]::Bold)
    $letterH = [int][Math]::Round($side * 0.40)
    $letterY = [int](($side - $letterH) / 2)
    $letterW = [single]($side * 0.20)
    $gap = [single]($side * 0.04)

    $rectL = New-Object System.Drawing.RectangleF ($mid - $letterW - $gap), $letterY, $letterW, $letterH
    $rectB = New-Object System.Drawing.RectangleF ($mid + $gap), $letterY, $letterW, $letterH
    $g.DrawString("L", $font, $whiteBrush, $rectL, $sf)
    $g.DrawString("B", $font, $blueBrush, $rectB, $sf)

    $whiteBrush.Dispose()
    $blueBrush.Dispose()
    $font.Dispose()
    $sf.Dispose()
    $g.Dispose()
    return $bmp
}

function Save-CircularIcon([System.Drawing.Image]$source, [string]$targetPath, [int]$size) {
    $bmp = New-Object System.Drawing.Bitmap $size, $size
    $g = New-HqGraphics $bmp
    $g.Clear([System.Drawing.Color]::Transparent)

    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddEllipse(0, 0, $size - 1, $size - 1)
    $g.SetClip($path)
    $g.DrawImage($source, 0, 0, $size, $size)

    $g.Dispose()
    $path.Dispose()
    $bmp.Save($targetPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

function Save-PhonePreview([string]$iconPath, [string]$outPath) {
    $w = 900
    $h = 1600
    $bmp = New-Object System.Drawing.Bitmap $w, $h
    $g = New-HqGraphics $bmp

    $grad = New-Object System.Drawing.Drawing2D.LinearGradientBrush (
        (New-Object System.Drawing.Rectangle 0, 0, $w, $h),
        [System.Drawing.Color]::FromArgb(255, 255, 160, 60),
        [System.Drawing.Color]::FromArgb(255, 80, 140, 220),
        135
    )
    $g.FillRectangle($grad, 0, 0, $w, $h)
    $grad.Dispose()

    $icon = [System.Drawing.Image]::FromFile($iconPath)
    $iconSize = 118
    $startX = 72
    $startY = 280
    $gapX = 168
    $gapY = 200

    for ($row = 0; $row -lt 4; $row++) {
        for ($col = 0; $col -lt 4; $col++) {
            $px = $startX + $col * $gapX
            $py = $startY + $row * $gapY
            if ($row -eq 2 -and $col -eq 1) {
                $g.DrawImage($icon, $px, $py, $iconSize, $iconSize)
            } else {
                $shade = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(40, 255, 255, 255))
                $g.FillEllipse($shade, $px + 20, $py + 20, 78, 78)
                $shade.Dispose()
            }
        }
    }
    $icon.Dispose()
    $g.Dispose()

    $dir = Split-Path $outPath -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    $bmp.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

$brand = New-BrandLogo 1024
$brandPath = Join-Path (Split-Path $Source -Parent) "ic_launcher_brand.png"
$brand.Save($brandPath, [System.Drawing.Imaging.ImageFormat]::Png)

$mipmap = @{
    "mipmap-mdpi"     = 48
    "mipmap-hdpi"     = 72
    "mipmap-xhdpi"    = 96
    "mipmap-xxhdpi"   = 144
    "mipmap-xxxhdpi"  = 192
}
foreach ($folder in $mipmap.Keys) {
    $dir = Join-Path $ResDir $folder
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    Save-CircularIcon $brand (Join-Path $dir "ic_launcher.png") $mipmap[$folder]
    Save-CircularIcon $brand (Join-Path $dir "ic_launcher_round.png") $mipmap[$folder]
}

$adaptive = @{
    "drawable-mdpi"    = 108
    "drawable-hdpi"    = 162
    "drawable-xhdpi"   = 216
    "drawable-xxhdpi"  = 324
    "drawable-xxxhdpi" = 432
}
foreach ($folder in $adaptive.Keys) {
    $dir = Join-Path $ResDir $folder
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    Save-CircularIcon $brand (Join-Path $dir "ic_launcher_foreground_full.png") $adaptive[$folder]
}

Save-PhonePreview (Join-Path $ResDir "mipmap-xxxhdpi\ic_launcher_round.png") $PreviewOut
$brand.Dispose()

Write-Host "Icons: $ResDir"
Write-Host "Phone preview: $PreviewOut"
