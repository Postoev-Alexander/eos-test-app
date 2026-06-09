#!/bin/bash
# Выходим сразу, если какая-то из команд завершится ошибкой
set -e

echo "=== СТАРТ: Авторизация в GitHub Container Registry ==="
if [ -n "$CR_PAT" ]; then
    # Если переменная с токеном передана, логинимся
    echo "$CR_PAT" | docker login ghcr.io -u Postoev-Alexander --password-stdin
else
    echo "Предупреждение: Переменная CR_PAT пустая. Пробуем собрать без логина (локальный режим)..."
fi

echo "=== СТАРТ: Сборка Docker-образа ==="
docker build -t ghcr.io/postoev-alexander/eos-test-app:0.01 .
docker tag ghcr.io/postoev-alexander/eos-test-app:latest

# Проверяем, передан ли аргумент --push
if [ "$1" == "--push" ]; then
    echo "=== СТАРТ: Отправка образа на GitHub ==="
    docker push ghcr.io/postoev-alexander/eos-test-app:latest
    echo "=== ВСЁ ГОТОВО! Образы успешно отправлены ==="
else
    echo "=== Сборка завершена локально. (Флаг --push не передан, отправка пропущена) ==="
fi
