$ErrorActionPreference = "Stop"

$containerName = "ebike-postgres"
$sqlFile = Join-Path $PSScriptRoot "seed_manager_revenue_test_data.sql"

if (-not (Test-Path $sqlFile)) {
    throw "Khong tim thay file seed: $sqlFile"
}

Write-Host "Copy seed SQL vao container $containerName ..."
docker cp $sqlFile "${containerName}:/tmp/seed_manager_revenue_test_data.sql"

Write-Host "Chay seed doanh thu ..."
docker exec -i $containerName psql -U ebike_user -d ebike_db -v ON_ERROR_STOP=1 -f /tmp/seed_manager_revenue_test_data.sql

Write-Host "Xong. Mo /manager/reports de xem san pham ban chay / khong ban chay."
