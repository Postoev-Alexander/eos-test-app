#!/bin/bash
set -e

IMAGE_NAME="ghcr.io/postoev-alexander/eos-test-app"

echo "=== СТАРТ: Авторизация в GitHub Container Registry ==="
if [ -n "$CR_PAT" ]; then
    echo "$CR_PAT" | docker login ghcr.io -u Postoev-Alexander --password-stdin
else
    echo "Предупреждение: Переменная CR_PAT пустая. Пробуем собрать без логина..."
fi

echo "=== СТАРТ: Сборка Docker-образа ==="
docker build -t ${IMAGE_NAME}:latest .

if [ "$1" == "--push" ]; then
    echo "=== СТАРТ: Отправка образа на GitHub ==="
    docker push ${IMAGE_NAME}:latest
    echo "=== ВСЁ ГОТОВО! Образ успешно отправлен ==="
else
    echo "=== Сборка завершена локально. Отправка пропущена ==="
fi
