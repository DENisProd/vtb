# Dockerfile для предзагрузки модели ИИ
FROM nvidia/cuda:12.1.1-cudnn8-runtime-ubuntu22.04

WORKDIR /app

# Устанавливаем системные зависимости
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    python3 \
    python3-pip \
    build-essential \
    git \
    && rm -rf /var/lib/apt/lists/*

# Копируем requirements и устанавливаем зависимости
COPY requirements.txt .
RUN pip3 install --no-cache-dir --upgrade pip && \
    pip3 install --no-cache-dir torch==2.5.1 --index-url https://download.pytorch.org/whl/cu121 && \
    pip3 install --no-cache-dir -r requirements.txt

# Копируем скрипт предзагрузки
COPY preload_model.py .

# Делаем скрипт исполняемым
RUN chmod +x preload_model.py

# Устанавливаем переменные окружения по умолчанию
ENV QWEN_MODEL_NAME=Qwen/Qwen3-4B-Instruct
ENV AI_VERIFICATION_VERBOSE=false
ENV HF_HOME=/models/hub
ENV DISABLE_TRITON=0

# Создаем директорию для кэша моделей
RUN mkdir -p /models/hub

# Запускаем скрипт предзагрузки
CMD ["python3", "preload_model.py"]

