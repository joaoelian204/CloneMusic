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

FROM gcr.io/distroless/static-debian12:nonroot AS runtime

WORKDIR /app

COPY --from=builder /out/api /app/api

EXPOSE 3000

USER nonroot:nonroot

ENTRYPOINT ["/app/api"]
