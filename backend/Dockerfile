# syntax=docker/dockerfile:1.7

FROM golang:1.26-alpine AS builder

WORKDIR /src

# Dependencias primero para aprovechar cache de capas
COPY go.mod go.sum ./
RUN go mod download

# Codigo fuente
COPY . .

# Binario estatico y optimizado
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 \
    go build -trimpath -ldflags="-s -w" -o /out/api ./cmd/api

FROM alpine:3.21 AS runtime

WORKDIR /app

# Certificados TLS y utilidades para depuracion en entorno cloud
RUN apk add --no-cache ca-certificates curl \
    && addgroup -S app \
    && adduser -S -G app app

COPY --from=builder /out/api /app/api

EXPOSE 3000

USER app:app

ENTRYPOINT ["/app/api"]
