# Web UI of dewtally (Next.js standalone build).
# Build: docker build -t dewtally-web:local web
# Settings (Keycloak, API, Better Auth secret) come from the environment at start, never from the image.

FROM node:24-alpine AS deps
WORKDIR /app
RUN npm install -g pnpm@12.15.0
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
# Exactly the versions of the lock file, nothing newer
RUN pnpm install --frozen-lockfile

FROM deps AS build
COPY . .
ENV NEXT_TELEMETRY_DISABLED=1
# lib/env.ts insists on its settings, and Next.js loads the modules while building.
# These placeholders exist only during this step; the real values are passed when the container starts.
RUN KEYCLOAK_ISSUER=http://keycloak.invalid/realms/build \
    KEYCLOAK_CLIENT_ID=build \
    KEYCLOAK_CLIENT_SECRET=build-only-not-a-secret \
    API_URL=http://api.invalid \
    BETTER_AUTH_URL=http://web.invalid \
    BETTER_AUTH_SECRET=build-only-placeholder-never-used-at-runtime \
    pnpm build

FROM node:24-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0
# Only the build output: no sources, no dev dependencies, no package manager
COPY --from=build /app/public ./public
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
# The files belong to root and cannot be changed; the server runs as the unprivileged user "node"
USER node
EXPOSE 3000
CMD ["node", "server.js"]