#!/bin/bash
set -e

: "${OLLAMA_MODEL:?OLLAMA_MODEL environment variable must be set}"

ollama serve &
OLLAMA_PID=$!

until ollama list >/dev/null 2>&1; do
  sleep 1
done

ollama pull "$OLLAMA_MODEL"

java -jar /app/app.jar &
SERVICE_PID=$!

trap 'kill -TERM $OLLAMA_PID $SERVICE_PID 2>/dev/null' TERM INT

wait -n "$OLLAMA_PID" "$SERVICE_PID"
kill -TERM $OLLAMA_PID $SERVICE_PID 2>/dev/null
wait
