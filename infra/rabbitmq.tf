resource "random_password" "rabbitmq" {
  length  = 32
  special = false
}

resource "azurerm_key_vault_secret" "rabbitmq_password" {
  name         = "rabbitmq-password"
  value        = random_password.rabbitmq.result
  key_vault_id = azurerm_key_vault.main.id
  depends_on   = [azurerm_role_assignment.terraform_writes_secrets]
}

# Message broker between api and worker. Reachable only inside the environment, as "rabbitmq:5672",
# exactly like in Docker Compose. Messages live in memory: a restart loses undelivered ones (fine for a demo).
resource "azurerm_container_app" "rabbitmq" {
  name                         = "rabbitmq"
  container_app_environment_id = azurerm_container_app_environment.main.id
  resource_group_name          = azurerm_resource_group.main.name
  revision_mode                = "Single"
  tags                         = var.tags

  identity {
    type         = "UserAssigned"
    identity_ids = [azurerm_user_assigned_identity.apps.id]
  }

  # Read from Key Vault with the identity above; the password never appears in the app's settings.
  secret {
    name                = "rabbitmq-password"
    key_vault_secret_id = azurerm_key_vault_secret.rabbitmq_password.versionless_id
    identity            = azurerm_user_assigned_identity.apps.id
  }

  # Internal TCP: other apps in the environment connect to rabbitmq:5672; nothing from outside.
  ingress {
    external_enabled = false
    transport        = "tcp"
    target_port      = 5672
    exposed_port     = 5672

    traffic_weight {
      latest_revision = true
      percentage      = 100
    }
  }

  template {
    # The broker must always be there: never scaled to zero, never more than one.
    min_replicas = 1
    max_replicas = 1

    container {
      name   = "rabbitmq"
      image  = "docker.io/library/rabbitmq:4"
      cpu    = 0.5
      memory = "1Gi"

      env {
        name  = "RABBITMQ_DEFAULT_USER"
        value = "dewtally"
      }
      env {
        name        = "RABBITMQ_DEFAULT_PASS"
        secret_name = "rabbitmq-password"
      }
    }
  }

  # The app may only start once its identity can read the secret.
  depends_on = [azurerm_role_assignment.apps_read_secrets]
}
