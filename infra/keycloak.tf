resource "random_password" "keycloak_admin" {
  length  = 32
  special = false
}

resource "azurerm_key_vault_secret" "keycloak_admin_password" {
  name         = "keycloak-admin-password"
  value        = random_password.keycloak_admin.result
  key_vault_id = azurerm_key_vault.main.id
  depends_on   = [azurerm_role_assignment.terraform_writes_secrets]
}

locals {
  # Public address of Keycloak; tokens name it as issuer, so the API must use exactly the same.
  keycloak_url = "https://keycloak.${azurerm_container_app_environment.main.default_domain}"
}

# Login server in production mode (start --optimized, see keycloak/Dockerfile) with its own database.
# The dewtally realm is imported on the first start only; later starts keep users and settings.
resource "azurerm_container_app" "keycloak" {
  name                         = "keycloak"
  container_app_environment_id = azurerm_container_app_environment.main.id
  resource_group_name          = azurerm_resource_group.main.name
  revision_mode                = "Single"
  tags                         = var.tags

  identity {
    type         = "UserAssigned"
    identity_ids = [azurerm_user_assigned_identity.apps.id]
  }

  secret {
    name                = "keycloak-admin-password"
    key_vault_secret_id = azurerm_key_vault_secret.keycloak_admin_password.versionless_id
    identity            = azurerm_user_assigned_identity.apps.id
  }

  secret {
    name                = "db-password"
    key_vault_secret_id = azurerm_key_vault_secret.db_password.versionless_id
    identity            = azurerm_user_assigned_identity.apps.id
  }

  # Public HTTPS: users log in here. Azure terminates TLS and forwards plain HTTP to port 8080.
  ingress {
    external_enabled = true
    target_port      = 8080

    traffic_weight {
      latest_revision = true
      percentage      = 100
    }
  }

  template {
    # One instance: several would need a shared cache cluster, which a demo does not need.
    min_replicas = 1
    max_replicas = 1

    container {
      name   = "keycloak"
      image  = "${var.image_registry}/dewtally-keycloak:${var.image_tag}"
      cpu    = 1
      memory = "2Gi"

      env {
        name  = "KC_DB_URL"
        value = "jdbc:postgresql://${azurerm_postgresql_flexible_server.main.fqdn}:5432/keycloak?sslmode=require"
      }
      env {
        name  = "KC_DB_USERNAME"
        value = azurerm_postgresql_flexible_server.main.administrator_login
      }
      env {
        name        = "KC_DB_PASSWORD"
        secret_name = "db-password"
      }
      env {
        name  = "KC_BOOTSTRAP_ADMIN_USERNAME"
        value = "admin"
      }
      env {
        name        = "KC_BOOTSTRAP_ADMIN_PASSWORD"
        secret_name = "keycloak-admin-password"
      }
      # Tokens always name the public HTTPS address, even though the container itself speaks HTTP
      env {
        name  = "KC_HOSTNAME"
        value = local.keycloak_url
      }
      # Behind Azure's proxy: trust its X-Forwarded headers and accept plain HTTP from it
      env {
        name  = "KC_PROXY_HEADERS"
        value = "xforwarded"
      }
      env {
        name  = "KC_HTTP_ENABLED"
        value = "true"
      }

      # Health endpoints on Keycloak's management port. Startup: up to 5 minutes for the first start
      # (database schema + realm import). Readiness: no traffic until Keycloak can answer.
      startup_probe {
        transport               = "HTTP"
        port                    = 9000
        path                    = "/health/started"
        interval_seconds        = 10
        failure_count_threshold = 30
      }
      readiness_probe {
        transport = "HTTP"
        port      = 9000
        path      = "/health/ready"
      }
    }
  }

  depends_on = [
    azurerm_role_assignment.apps_read_secrets,
    azurerm_postgresql_flexible_server_database.keycloak,
    azurerm_postgresql_flexible_server_firewall_rule.azure_services,
  ]
}
