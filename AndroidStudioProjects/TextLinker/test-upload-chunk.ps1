# PowerShell test script for /upload-chunk endpoint

# Test single chunk
Write-Host "Testing single chunk upload..."
try {
    $response = Invoke-WebRequest -Uri "https://textlinker.pro/upload-chunk" -Method POST -Headers @{"Content-Type"="application/json"} -Body '{"token":"DEMO","chunkIndex":0,"totalChunks":1,"textChunk":"hello"}' -UseBasicParsing
    Write-Host "Response: $($response.StatusCode) - $($response.Content)"
} catch {
    Write-Host "Error: $($_.Exception.Message)"
    if ($_.Exception.Response) {
        Write-Host "Status: $($_.Exception.Response.StatusCode)"
    }
}

Write-Host "`nTesting multi-chunk upload (part 1)..."
try {
    $response = Invoke-WebRequest -Uri "https://textlinker.pro/upload-chunk" -Method POST -Headers @{"Content-Type"="application/json"} -Body '{"token":"DEMO2","chunkIndex":0,"totalChunks":2,"textChunk":"first part"}' -UseBasicParsing
    Write-Host "Response: $($response.StatusCode) - $($response.Content)"
} catch {
    Write-Host "Error: $($_.Exception.Message)"
}

Write-Host "`nTesting multi-chunk upload (part 2)..."
try {
    $response = Invoke-WebRequest -Uri "https://textlinker.pro/upload-chunk" -Method POST -Headers @{"Content-Type"="application/json"} -Body '{"token":"DEMO2","chunkIndex":1,"totalChunks":2,"textChunk":" second part"}' -UseBasicParsing
    Write-Host "Response: $($response.StatusCode) - $($response.Content)"
} catch {
    Write-Host "Error: $($_.Exception.Message)"
}
