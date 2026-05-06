FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
ARG INSTALL_MEDIA_DEPS=false

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    ffmpeg \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt requirements.media.txt ./
RUN pip install --no-cache-dir --upgrade pip setuptools wheel && \
    pip install --no-cache-dir -r requirements.txt

RUN if [ "$INSTALL_MEDIA_DEPS" = "true" ]; then \
      apt-get update && apt-get install -y --no-install-recommends \
      pkg-config \
      gcc \
      libavformat-dev \
      libavcodec-dev \
      libavdevice-dev \
      libavutil-dev \
      libavfilter-dev \
      libswscale-dev \
      libswresample-dev \
      && pip install --no-cache-dir -r requirements.media.txt \
      && rm -rf /var/lib/apt/lists/*; \
    fi

COPY . .

EXPOSE 8000

CMD ["python", "main.py"]
