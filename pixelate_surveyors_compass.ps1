param(
    [int]$ReducedSize = 64,
    [int]$InnerReducedSize = 80,
    [ValidateSet("OuterOverlay", "InnerMaskOverlay", "OuterAndInnerOverlay", "AllFrames")]
    [string]$Mode = "OuterOverlay",
    [string[]]$Versions = @("1.21.11", "26.1.2")
)

if ($ReducedSize -lt 1) {
    throw "ReducedSize must be at least 1."
}

Add-Type -AssemblyName System.Drawing

function Resize-NearestNeighbor {
    param(
        [Parameter(Mandatory = $true)]
        [System.Drawing.Bitmap]$Source,
        [Parameter(Mandatory = $true)]
        [int]$Width,
        [Parameter(Mandatory = $true)]
        [int]$Height
    )

    $target = New-Object System.Drawing.Bitmap($Width, $Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($target)

    try {
        $graphics.Clear([System.Drawing.Color]::Transparent)
        $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
        $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
        $graphics.DrawImage($Source, 0, 0, $Width, $Height)
    } finally {
        $graphics.Dispose()
    }

    return $target
}

function Load-Bitmap {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    $bytes = [System.IO.File]::ReadAllBytes($Path)
    $memoryStream = New-Object System.IO.MemoryStream(, $bytes)
    $loadedImage = [System.Drawing.Image]::FromStream($memoryStream)
    $source = New-Object System.Drawing.Bitmap($loadedImage)
    $loadedImage.Dispose()
    $memoryStream.Dispose()

    return $source
}

function Save-Bitmap {
    param(
        [Parameter(Mandatory = $true)]
        [System.Drawing.Bitmap]$Bitmap,
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    $tempPath = "$Path.pixel.tmp.png"
    if (Test-Path -LiteralPath $tempPath) {
        Remove-Item -LiteralPath $tempPath -Force
    }

    $Bitmap.Save($tempPath, [System.Drawing.Imaging.ImageFormat]::Png)
    Copy-Item -LiteralPath $tempPath -Destination $Path -Force
    Remove-Item -LiteralPath $tempPath -Force
}

function Get-PixelatedBitmap {
    param(
        [Parameter(Mandatory = $true)]
        [System.Drawing.Bitmap]$Source,
        [Parameter(Mandatory = $true)]
        [int]$ReducedSize
    )

    $downWidth = [Math]::Max(1, [Math]::Min($source.Width, $ReducedSize))
    $downHeight = [Math]::Max(1, [Math]::Min($source.Height, $ReducedSize))

    $downscaled = Resize-NearestNeighbor -Source $Source -Width $downWidth -Height $downHeight
    try {
        return Resize-NearestNeighbor -Source $downscaled -Width $Source.Width -Height $Source.Height
    } finally {
        $downscaled.Dispose()
    }
}

function Pixelate-Image {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,
        [Parameter(Mandatory = $true)]
        [int]$ReducedSize
    )

    $source = Load-Bitmap -Path $Path
    try {
        $pixelated = Get-PixelatedBitmap -Source $source -ReducedSize $ReducedSize
        try {
            Save-Bitmap -Bitmap $pixelated -Path $Path
        } finally {
            $pixelated.Dispose()
        }
    } finally {
        $source.Dispose()
    }
}

function Blend-ColorChannel {
    param(
        [int]$Base,
        [int]$Overlay,
        [double]$Factor
    )

    return [Math]::Max(0, [Math]::Min(255, [int][Math]::Round(($Base * (1.0 - $Factor)) + ($Overlay * $Factor))))
}

function Apply-InnerMaskOverlay {
    param(
        [Parameter(Mandatory = $true)]
        [string]$TextureDir,
        [Parameter(Mandatory = $true)]
        [int]$ReducedSize
    )

    $innerPath = Join-Path $TextureDir "surveyors_compass_inner.png"
    $targetFiles = @(
        Join-Path $TextureDir "surveyors_compass.png"
    ) + (Get-ChildItem -LiteralPath $TextureDir -Filter "surveyors_compass_??.png" | Sort-Object Name | Select-Object -ExpandProperty FullName)

    $innerOriginal = Load-Bitmap -Path $innerPath
    try {
        $innerPixelated = Get-PixelatedBitmap -Source $innerOriginal -ReducedSize $ReducedSize
        try {
            Save-Bitmap -Bitmap $innerPixelated -Path $innerPath

            foreach ($targetFile in $targetFiles) {
                $targetBitmap = Load-Bitmap -Path $targetFile
                try {
                    $pixelatedTarget = Get-PixelatedBitmap -Source $targetBitmap -ReducedSize $ReducedSize
                    try {
                        $resultBitmap = New-Object System.Drawing.Bitmap($targetBitmap.Width, $targetBitmap.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
                        try {
                            for ($x = 0; $x -lt $targetBitmap.Width; $x++) {
                                for ($y = 0; $y -lt $targetBitmap.Height; $y++) {
                                    $maskColor = $innerOriginal.GetPixel($x, $y)
                                    if ($maskColor.A -le 0) {
                                        $resultBitmap.SetPixel($x, $y, $targetBitmap.GetPixel($x, $y))
                                        continue
                                    }

                                    $factor = $maskColor.A / 255.0
                                    $baseColor = $targetBitmap.GetPixel($x, $y)
                                    $overlayColor = $pixelatedTarget.GetPixel($x, $y)
                                    $alpha = Blend-ColorChannel -Base $baseColor.A -Overlay $overlayColor.A -Factor $factor
                                    $red = Blend-ColorChannel -Base $baseColor.R -Overlay $overlayColor.R -Factor $factor
                                    $green = Blend-ColorChannel -Base $baseColor.G -Overlay $overlayColor.G -Factor $factor
                                    $blue = Blend-ColorChannel -Base $baseColor.B -Overlay $overlayColor.B -Factor $factor
                                    $blended = [System.Drawing.Color]::FromArgb(
                                        $alpha,
                                        $red,
                                        $green,
                                        $blue
                                    )
                                    $resultBitmap.SetPixel($x, $y, $blended)
                                }
                            }

                            Save-Bitmap -Bitmap $resultBitmap -Path $targetFile
                        } finally {
                            $resultBitmap.Dispose()
                        }
                    } finally {
                        $pixelatedTarget.Dispose()
                    }
                } finally {
                    $targetBitmap.Dispose()
                }
            }
        } finally {
            $innerPixelated.Dispose()
        }
    } finally {
        $innerOriginal.Dispose()
    }
}

function Apply-OuterOverlay {
    param(
        [Parameter(Mandatory = $true)]
        [string]$TextureDir,
        [Parameter(Mandatory = $true)]
        [int]$ReducedSize
    )

    $outerPath = Join-Path $TextureDir "surveyors_compass_outer.png"
    $targetFiles = @(
        Join-Path $TextureDir "surveyors_compass.png"
    ) + (Get-ChildItem -LiteralPath $TextureDir -Filter "surveyors_compass_??.png" | Sort-Object Name | Select-Object -ExpandProperty FullName)

    $outerOriginal = Load-Bitmap -Path $outerPath
    try {
        $outerPixelated = Get-PixelatedBitmap -Source $outerOriginal -ReducedSize $ReducedSize
        try {
            Save-Bitmap -Bitmap $outerPixelated -Path $outerPath

            foreach ($targetFile in $targetFiles) {
                $targetBitmap = Load-Bitmap -Path $targetFile
                $resultBitmap = New-Object System.Drawing.Bitmap($targetBitmap.Width, $targetBitmap.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
                $graphics = [System.Drawing.Graphics]::FromImage($resultBitmap)
                try {
                    $graphics.Clear([System.Drawing.Color]::Transparent)
                    $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceOver
                    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
                    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
                    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
                    $graphics.DrawImage($targetBitmap, 0, 0, $targetBitmap.Width, $targetBitmap.Height)
                    $graphics.DrawImage($outerPixelated, 0, 0, $outerPixelated.Width, $outerPixelated.Height)
                } finally {
                    $graphics.Dispose()
                    $targetBitmap.Dispose()
                }

                try {
                    Save-Bitmap -Bitmap $resultBitmap -Path $targetFile
                } finally {
                    $resultBitmap.Dispose()
                }
            }
        } finally {
            $outerPixelated.Dispose()
        }
    } finally {
        $outerOriginal.Dispose()
    }
}

$repoRoot = $PSScriptRoot

foreach ($version in $Versions) {
    $textureDir = Join-Path $repoRoot "$version\src\main\resources\assets\village-quest\textures\item"
    if (-not (Test-Path -LiteralPath $textureDir)) {
        throw "Texture directory not found: $textureDir"
    }

    $files = Get-ChildItem -LiteralPath $textureDir -Filter "surveyors_compass*.png" | Sort-Object Name
    if ($files.Count -eq 0) {
        throw "No Surveyor's Compass textures found in $textureDir"
    }

    if ($Mode -eq "AllFrames") {
        foreach ($file in $files) {
            Pixelate-Image -Path $file.FullName -ReducedSize $ReducedSize
        }
        Write-Output ("Pixelated {0} Surveyor's Compass textures in {1} at {2}px." -f $files.Count, $version, $ReducedSize)
        continue
    }

    if ($Mode -eq "OuterOverlay") {
        Apply-OuterOverlay -TextureDir $textureDir -ReducedSize $ReducedSize
        Write-Output ("Rebuilt Surveyor's Compass textures in {0} with an outer-ring overlay at {1}px and the inner art left intact." -f $version, $ReducedSize)
        continue
    }

    if ($Mode -eq "InnerMaskOverlay") {
        Apply-InnerMaskOverlay -TextureDir $textureDir -ReducedSize $InnerReducedSize
        Write-Output ("Rebuilt Surveyor's Compass textures in {0} with masked inner pixelation at {1}px while keeping the current ring intact." -f $version, $InnerReducedSize)
        continue
    }

    Apply-OuterOverlay -TextureDir $textureDir -ReducedSize $ReducedSize
    Apply-InnerMaskOverlay -TextureDir $textureDir -ReducedSize $InnerReducedSize
    Write-Output ("Rebuilt Surveyor's Compass textures in {0} with an outer-ring overlay at {1}px and masked inner pixelation at {2}px." -f $version, $ReducedSize, $InnerReducedSize)
}
