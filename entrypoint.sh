#!/bin/bash
set -e

MODEL_PATH="${LLAMA_MODEL_DIR}/${LLAMA_MODEL_FILE}"
MODEL_URL="https://huggingface.co/${LLAMA_MODEL_REPO}/resolve/main/${LLAMA_MODEL_FILE}"

if [ ! -f "$MODEL_PATH" ]; then
  echo "downloading ${LLAMA_MODEL_FILE} (~7.2 GB) from ${LLAMA_MODEL_REPO}"
  mkdir -p "$LLAMA_MODEL_DIR"

  ( while sleep 30; do
      [ -f "${MODEL_PATH}.part" ] && echo "  ... $(du -h "${MODEL_PATH}.part" | cut -f1) of ~7.2G" || true
    done ) &
  PROGRESS_PID=$!

  curl -fL --retry 5 --retry-delay 5 -C - --no-progress-meter -o "${MODEL_PATH}.part" "$MODEL_URL"

  kill "$PROGRESS_PID" 2>/dev/null || true
  mv "${MODEL_PATH}.part" "$MODEL_PATH"
  echo "download complete: $(du -h "$MODEL_PATH" | cut -f1)"
fi

llama-server \
  --model "$MODEL_PATH" \
  --alias "$LLAMA_MODEL_ALIAS" \
  --host 127.0.0.1 \
  --port "$LLAMA_PORT" \
  --ctx-size "$LLAMA_CTX_SIZE" \
  --n-gpu-layers 99 \
  --flash-attn on \
  --cache-type-k q4_0 \
  --cache-type-v q4_0 \
  --jinja \
  --no-webui &
LLAMA_PID=$!

until curl -sf "http://127.0.0.1:${LLAMA_PORT}/health" >/dev/null; do
  if ! kill -0 "$LLAMA_PID" 2>/dev/null; then
    echo "llama-server exited before becoming healthy" >&2
    exit 1
  fi
  sleep 2
done
echo "llama-server ready, serving ${LLAMA_MODEL_ALIAS}"

java -jar /app/app.jar &
SERVICE_PID=$!

trap 'kill -TERM $LLAMA_PID $SERVICE_PID 2>/dev/null' TERM INT

wait -n "$LLAMA_PID" "$SERVICE_PID"
kill -TERM $LLAMA_PID $SERVICE_PID 2>/dev/null
wait
