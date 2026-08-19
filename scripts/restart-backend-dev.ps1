$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$containerName = "adproject-backend-dev"
$imageName = "adproject-backend:dev"

try {
    Push-Location $repoRoot
    try {
        # Read the DeepSeek key from backend/.env (git-ignored) so the value never appears on the command line.
        $envFile = Join-Path $repoRoot "backend\.env"
        if (-not (Test-Path $envFile)) { throw "backend/.env is missing (it should hold DEEPSEEK_API_KEY)." }
        $keyLine = Get-Content $envFile | Where-Object { $_ -match '^\s*DEEPSEEK_API_KEY\s*=' } | Select-Object -Last 1
        if (-not $keyLine) { throw "backend/.env has no DEEPSEEK_API_KEY line." }
        $apiKey = ($keyLine -split "=", 2)[1].Trim().Trim('"').Trim("'")
        if ([string]::IsNullOrWhiteSpace($apiKey)) { throw "backend/.env DEEPSEEK_API_KEY is empty." }

        docker build -f infra/docker/Dockerfile.backend -t $imageName .
        if ($LASTEXITCODE -ne 0) { throw "Backend image build failed." }

        $existing = docker ps -aq --filter "name=^/$containerName`$"
        if ($existing) {
            docker rm -f $containerName | Out-Null
            if ($LASTEXITCODE -ne 0) { throw "Could not replace the existing backend container." }
        }

        docker run -d `
            --name $containerName `
            --restart unless-stopped `
            -p 8080:8080 `
            -e DB_URL="jdbc:mysql://host.docker.internal:13307/adproject?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC" `
            -e DB_USERNAME=adproject `
            -e DB_PASSWORD=local_agent_app `
            -e JWT_SECRET=local-agent-validation-jwt-secret-at-least-32-bytes `
            -e AGENT_PLANNER_BASE_URL=http://host.docker.internal:8090 `
            -e DEEPSEEK_API_KEY=$apiKey `
            $imageName | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Backend container failed to start." }

        $up = $false
        for ($attempt = 0; $attempt -lt 45; $attempt++) {
            try {
                $client = New-Object Net.Sockets.TcpClient
                $client.Connect("127.0.0.1", 8080)
                $client.Close()
                $up = $true
                break
            } catch {
                Start-Sleep -Seconds 2
            }
        }
        if (-not $up) { throw "Backend did not open port 8080 within 90 seconds." }
        Write-Output "Backend is up: http://localhost:8080"
    } finally {
        Pop-Location
    }
} finally {
    Remove-Variable apiKey -ErrorAction SilentlyContinue
}
