locals {
  app_image = "${var.image_registry}/dewtally:${var.image_tag}"

  # Settings shared by api and worker: the same values as in compose.yaml, pointed at the Azure resources.
  # Passwords are not here: they come from Key Vault (see the secret blocks below).
  app_env = {
    SPRING_DOCKER_COMPOSE_ENABLED = "false"
    # Azure requires encrypted connections to PostgreSQL
    SPRING_DATASOURCE_URL      = "jdbc:postgresql://${azurerm_postgresql_flexible_server.main.fqdn}:5432/dewtally?sslmode=require"
    SPRING_DATASOURCE_USERNAME = azurerm_postgresql_flexible_server.main.administrator_login
    SPRING_RABBITMQ_HOST       = "rabbitmq"
    SPRING_RABBITMQ_PORT       = "5672"
    SPRING_RABBITMQ_USERNAME   = "dewtally"
    # Tokens are issued by our Keycloak under its public address
    SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI  = "${local.keycloak_url}/realms/dewtally"
    SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI = "${local.keycloak_url}/realms/dewtally/protocol/openid-connect/certs"
  }
}

# API: accepts uploads, serves the API, owns the database schema (Liquibase) and sends outbox messages.
resource "azurerm_container_app" "api" {
  name                         = "api"
  container_app_environment_id = azurerm_container_app_environment.main.id
  resource_group_name          = azurerm_resource_group.main.name
  revision_mode                = "Single"
  tags                         = var.tags

  identity {
    type         = "UserAssigned"
    identity_ids = [azurerm_user_assigned_identity.apps.id]
  }

  secret {
    name                = "db-password"
    key_vault_secret_id = azurerm_key_vault_secret.db_password.versionless_id
    identity            = azurerm_user_assigned_identity.apps.id
  }

  secret {
    name                = "rabbitmq-password"
    key_vault_secret_id = azurerm_key_vault_secret.rabbitmq_password.versionless_id
    identity            = azurerm_user_assigned_identity.apps.id
  }

  # Public HTTPS; Azure terminates TLS and forwards to Spring on port 8080
  ingress {
    external_enabled = true
    target_port      = 8080

    traffic_weight {
      latest_revision = true
      percentage      = 100
    }
  }

  template {
    min_replicas = 1
    max_replicas = 1

    volume {
      name         = "documents"
      storage_type = "AzureFile"
      storage_name = azurerm_container_app_environment_storage.documents.name
    }

    container {
      name   = "api"
      image  = local.app_image
      cpu    = 1
      memory = "2Gi"

      volume_mounts {
        name = "documents"
        path = "/app/data"
      }

      env {
        name  = "SPRING_PROFILES_ACTIVE"
        value = "api"
      }

      dynamic "env" {
        for_each = local.app_env
        content {
          name  = env.key
          value = env.value
        }
      }

      env {
        name        = "SPRING_DATASOURCE_PASSWORD"
        secret_name = "db-password"
      }
      env {
        name        = "SPRING_RABBITMQ_PASSWORD"
        secret_name = "rabbitmq-password"
      }

      # Actuator on the management port (application-api.properties). Startup: Liquibase and the
      # KoSIT configuration take a while; up to 5 minutes before Azure gives up and restarts.
      startup_probe {
        transport               = "HTTP"
        port                    = 9091
        path                    = "/actuator/health"
        interval_seconds        = 10
        failure_count_threshold = 30
      }
      readiness_probe {
        transport = "HTTP"
        port      = 9091
        path      = "/actuator/health"
      }
    }
  }

  depends_on = [
    azurerm_role_assignment.apps_read_secrets,
    azurerm_postgresql_flexible_server_database.dewtally,
    azurerm_postgresql_flexible_server_firewall_rule.azure_services,
  ]
}

# Worker: processes documents from the queue (PDF parsing and the language model). No way in from outside.
resource "azurerm_container_app" "worker" {
  name                         = "worker"
  container_app_environment_id = azurerm_container_app_environment.main.id
  resource_group_name          = azurerm_resource_group.main.name
  revision_mode                = "Single"
  tags                         = var.tags

  identity {
    type         = "UserAssigned"
    identity_ids = [azurerm_user_assigned_identity.apps.id]
  }

  secret {
    name                = "db-password"
    key_vault_secret_id = azurerm_key_vault_secret.db_password.versionless_id
    identity            = azurerm_user_assigned_identity.apps.id
  }

  secret {
    name                = "rabbitmq-password"
    key_vault_secret_id = azurerm_key_vault_secret.rabbitmq_password.versionless_id
    identity            = azurerm_user_assigned_identity.apps.id
  }

  # Only the worker calls the language model
  secret {
    name                = "openai-api-key"
    key_vault_secret_id = azurerm_key_vault_secret.openai_api_key.versionless_id
    identity            = azurerm_user_assigned_identity.apps.id
  }

  template {
    # Listens to the queue all the time; scaling by queue length is a later improvement
    min_replicas = 1
    max_replicas = 1

    volume {
      name         = "documents"
      storage_type = "AzureFile"
      storage_name = azurerm_container_app_environment_storage.documents.name
    }

    container {
      name   = "worker"
      image  = local.app_image
      cpu    = 1
      memory = "2Gi"

      volume_mounts {
        name = "documents"
        path = "/app/data"
      }

      env {
        name  = "SPRING_PROFILES_ACTIVE"
        value = "worker"
      }

      dynamic "env" {
        for_each = local.app_env
        content {
          name  = env.key
          value = env.value
        }
      }

      env {
        name        = "SPRING_DATASOURCE_PASSWORD"
        secret_name = "db-password"
      }
      env {
        name        = "SPRING_RABBITMQ_PASSWORD"
        secret_name = "rabbitmq-password"
      }
      env {
        name        = "OPENAI_API_KEY"
        secret_name = "openai-api-key"
      }

      # Actuator on the worker's port (application-worker.properties)
      startup_probe {
        transport               = "HTTP"
        port                    = 8081
        path                    = "/actuator/health"
        interval_seconds        = 10
        failure_count_threshold = 30
      }
    }
  }

  # The api creates the database schema; the worker only checks it, so it starts after the api.
  depends_on = [
    azurerm_role_assignment.apps_read_secrets,
    azurerm_container_app.api,
    azurerm_container_app.rabbitmq,
  ]
}
