import 'server-only'

// Server settings from .env.local (locally) or the container environment (Azure).
// Fails fast with a clear message instead of a confusing error deep inside a login.
function required(name: string): string {
  const value = process.env[name]
  if (!value) {
    throw new Error(`Missing environment variable ${name} (see web/.env.local)`)
  }
  return value
}

export const env = {
  keycloakIssuer: required('KEYCLOAK_ISSUER'),
  keycloakClientId: required('KEYCLOAK_CLIENT_ID'),
  keycloakClientSecret: required('KEYCLOAK_CLIENT_SECRET'),
  apiUrl: required('API_URL'),
  appUrl: required('BETTER_AUTH_URL'),
}
