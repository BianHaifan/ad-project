$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$containerName = "adproject-agent-dev"
$imageName = "adproject-agent:deepseek"
$keyWasPrompted = $false
$keyPointer = [IntPtr]::Zero

try {
    if ([string]::IsNullOrWhiteSpace($env:DEEPSEEK_API_KEY)) {
        $secureKey = Read-Host "DeepSeek API Key" -AsSecureString
        $keyPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureKey)
        $env:DEEPSEEK_API_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($keyPointer)
        $keyWasPrompted = $true
    }

    Push-Location $repoRoot
    try {
        docker build -f infra/docker/Dockerfile.agent -t $imageName .
        if ($LASTEXITCODE -ne 0) { throw "Agent image build failed." }

        $existing = docker ps -aq --filter "name=^/$containerName`$"
        if ($existing) {
            docker rm -f $containerName | Out-Null
            if ($LASTEXITCODE -ne 0) { throw "Could not replace the existing Agent container." }
        }

        docker run -d `
            --name $containerName `
            --restart unless-stopped `
            -p 8090:8090 `
            -e DEEPSEEK_API_KEY `
            -e DEEPSEEK_MODEL=deepseek-v4-flash `
            -e DEEPSEEK_BASE_URL=https://api.deepseek.com `
            $imageName | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Agent container failed to start." }

        $healthy = $false
        for ($attempt = 0; $attempt -lt 15; $attempt++) {
            try {
                $health = Invoke-RestMethod -Uri "http://127.0.0.1:8090/health"
                if ($health.status -eq "UP") {
                    $health | ConvertTo-Json -Compress
                    $healthy = $true
                    break
                }
            } catch {
                Start-Sleep -Seconds 1
            }
        }
        if (-not $healthy) {
            throw "Agent did not become healthy within 15 seconds."
        }
    } finally {
        Pop-Location
    }
} finally {
    if ($keyWasPrompted) {
        Remove-Item Env:DEEPSEEK_API_KEY -ErrorAction SilentlyContinue
    }
    if ($keyPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($keyPointer)
    }
}
