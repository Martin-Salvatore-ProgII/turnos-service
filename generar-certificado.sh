#!/usr/bin/env bash
# Genera el certificado autofirmado con el que los dos servicios exponen su API por HTTPS en el entorno
# local (ADR-0035). Es uno solo para los dos, así la app confía en un único certificado.
#
# Deja tls.crt y tls.key en secrets/ de este repo y de ../catalogo-service. Ninguno se commitea:
# secrets/ está fuera de Git. tls.crt es el que se instala en la app (o en el sistema) para confiar en él.
#
# Uso: ./generar-certificado.sh            (no pisa un certificado que ya existe)
#      ./generar-certificado.sh --forzar   (lo reemplaza: la app tiene que volver a confiar en el nuevo)
set -euo pipefail

cd "$(dirname "$0")"
CATALOGO=../catalogo-service

if [ ! -d "$CATALOGO" ]; then
	echo "No encuentro $CATALOGO: clonalo al lado de este repo." >&2
	exit 1
fi
if [ -f secrets/tls.crt ] && [ "${1:-}" != "--forzar" ]; then
	echo "Ya existe secrets/tls.crt. Para reemplazarlo: $0 --forzar" >&2
	exit 1
fi

mkdir -p secrets "$CATALOGO/secrets"

# Nombres con los que se llega a los servicios:
# - localhost y 127.0.0.1: desde la máquina (curl, Postman);
# - 10.0.2.2: la máquina vista desde el emulador de Android;
# - turnos-service y catalogo-service: dentro de la red de Docker Compose (turnos llama al catálogo así).
openssl req -x509 -newkey rsa:2048 -nodes -days 825 \
	-subj "/CN=turnos-catalogo-local" \
	-addext "subjectAltName=DNS:localhost,IP:127.0.0.1,IP:10.0.2.2,DNS:turnos-service,DNS:catalogo-service" \
	-keyout secrets/tls.key -out secrets/tls.crt 2>/dev/null
chmod 600 secrets/tls.key

cp secrets/tls.crt secrets/tls.key "$CATALOGO/secrets/"

echo "Certificado generado en secrets/ de los dos repos. Vence en 825 días."
openssl x509 -in secrets/tls.crt -noout -ext subjectAltName
