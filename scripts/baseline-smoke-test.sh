#!/usr/bin/env bash
set -eo pipefail

echo "=================================================="
echo " Bookstore AI Systems - Baseline Smoke Test Suite "
echo "=================================================="

SERVICES=(
  "Discovery Service (Eureka)|http://localhost:8761/actuator/health|8761"
  "API Gateway|http://localhost:8080/actuator/health|8080"
  "Config Server|http://localhost:8888/actuator/health|8888"
  "Auth Server|http://localhost:9001/actuator/health|9001"
  "User Service|http://localhost:8101/actuator/health|8101"
  "Catalog Service|http://localhost:8201/actuator/health|8201"
  "Checkout Service|http://localhost:8301/actuator/health|8301"
  "Notification Service|http://localhost:8401/actuator/health|8401"
  "Audit Service|http://localhost:8501/actuator/health|8501"
  "Prometheus|http://localhost:9092/-/healthy|9092"
  "Grafana|http://localhost:3000/api/health|3000"
)

ALL_PASSED=0

printf "%-30s %-8s %-12s\n" "SERVICE" "PORT" "STATUS"
printf "%-30s %-8s %-12s\n" "------------------------------" "--------" "------------"

for entry in "${SERVICES[@]}"; do
  IFS="|" read -r NAME URL PORT <<< "$entry"
  if curl -sf --max-time 3 "$URL" > /dev/null 2>&1; then
    printf "%-30s %-8s \033[0;32m%-12s\033[0m\n" "$NAME" "$PORT" "HEALTHY"
  else
    printf "%-30s %-8s \033[0;31m%-12s\033[0m\n" "$NAME" "$PORT" "DOWN"
    ALL_PASSED=1
  fi
done

if [ $ALL_PASSED -eq 0 ]; then
  echo -e "\n\033[0;32mAll baseline services are UP and HEALTHY! Stack is ready.\033[0m"
  exit 0
else
  echo -e "\n\033[0;31mOne or more services failed health verification.\033[0m"
  exit 1
fi
