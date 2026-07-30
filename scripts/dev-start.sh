#!/usr/bin/env bash

set -Eeuo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

cd "${PROJECT_ROOT}"

if ! command -v docker >/dev/null 2>&1; then
    echo "[dev] Docker를 찾을 수 없습니다. Docker Desktop 또는 Docker Engine을 설치하세요." >&2
    exit 1
fi

if ! docker info >/dev/null 2>&1; then
    echo "[dev] Docker가 실행 중이 아닙니다. Docker를 시작한 뒤 다시 실행하세요." >&2
    exit 1
fi

echo "[dev] 로컬 MySQL을 준비합니다."
docker compose up -d --wait mysql

echo "[dev] Spring Boot를 local 프로필로 실행합니다."
echo "[dev] 종료하려면 Ctrl+C를 누르세요. MySQL 컨테이너는 유지됩니다."

exec env SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
