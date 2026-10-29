# Production Keycloak image with the dewtally realm built in (used in Azure).
# Follows the official container guide: build once for PostgreSQL, then start with --optimized.
# Locally, compose.yaml keeps using the stock image in dev mode with the mounted realm file.

# Build stage: bake the database vendor and the health endpoints into the server.
FROM quay.io/keycloak/keycloak:26.3 AS builder
ENV KC_DB=postgres
ENV KC_HEALTH_ENABLED=true
WORKDIR /opt/keycloak
RUN /opt/keycloak/bin/kc.sh build

# Final image: the optimized server plus the realm to import on the very first start.
# If the realm already exists in the database, the import is skipped and no data is overwritten.
FROM quay.io/keycloak/keycloak:26.3
COPY --from=builder /opt/keycloak/ /opt/keycloak/
COPY dewtally-realm.json /opt/keycloak/data/import/dewtally-realm.json
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--optimized", "--import-realm"]