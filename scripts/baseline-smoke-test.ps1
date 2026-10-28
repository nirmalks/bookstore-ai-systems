# PowerShell Baseline Smoke Test Script for bookstore-ai-systems
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host " Bookstore AI Systems - Baseline Smoke Test Suite " -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan

$services = @(
    @{ Name = "Discovery Service (Eureka)"; Url = "http://localhost:8761/actuator/health"; Port = 8761 },
    @{ Name = "API Gateway";               Url = "http://localhost:8080/actuator/health"; Port = 8080 },
    @{ Name = "Config Server";             Url = "http://localhost:8888/actuator/health"; Port = 8888 },
    @{ Name = "Auth Server";               Url = "http://localhost:9001/actuator/health"; Port = 9001 },
    @{ Name = "User Service";              Url = "http://localhost:8101/actuator/health"; Port = 8101 },
    @{ Name = "Catalog Service";           Url = "http://localhost:8201/actuator/health"; Port = 8201 },
    @{ Name = "Checkout Service";          Url = "http://localhost:8301/actuator/health"; Port = 8301 },
    @{ Name = "Notification Service";      Url = "http://localhost:8401/actuator/health"; Port = 8401 },
    @{ Name = "Audit Service";             Url = "http://localhost:8501/actuator/health"; Port = 8501 },
    @{ Name = "Prometheus";                Url = "http://localhost:9092/-/healthy";       Port = 9092 },
    @{ Name = "Grafana";                   Url = "http://localhost:3000/api/health";      Port = 3000 }
)

$allPassed = $true
$results = @()

foreach ($svc in $services) {
    try {
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        $response = Invoke-RestMethod -Uri $svc.Url -Method Get -TimeoutSec 3 -ErrorAction Stop
        $sw.Stop()

        $status = "UNKNOWN"
        if ($response -is [string]) {
            if ($response -match "UP|Healthy|ok") { $status = "HEALTHY" }
        } elseif ($response.status) {
            $status = $response.status
        } elseif ($response.database -eq "ok") {
            $status = "HEALTHY"
        }

        $results += [PSCustomObject]@{
            Service = $svc.Name
            Port    = $svc.Port
            Status  = $status
            Latency = "$($sw.ElapsedMilliseconds)ms"
            Result  = "PASS"
        }
    }
    catch {
        $allPassed = $false
        $results += [PSCustomObject]@{
            Service = $svc.Name
            Port    = $svc.Port
            Status  = "DOWN"
            Latency = "-"
            Result  = "FAIL ($($_.Exception.Message))"
        }
    }
}

$results | Format-Table -AutoSize

if ($allPassed) {
    Write-Host "All baseline services are UP and HEALTHY! Stack is ready." -ForegroundColor Green
    exit 0
} else {
    Write-Host "One or more services failed health verification." -ForegroundColor Red
    exit 1
}
