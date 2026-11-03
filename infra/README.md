# dewtally on Azure (Terraform)

The whole cloud environment is described in this folder. `terraform apply` creates it, `terraform destroy` removes
it completely, so it only costs money while it is needed.

> Status: the code is checked with `terraform validate`; the first real deployment is still to come.

## What gets created

All resources live in one resource group (`rg-dewtally`) in Germany West Central (Frankfurt), so invoice data stays
in Germany.

| Resource                        | Purpose                                                                                              |
|---------------------------------|------------------------------------------------------------------------------------------------------|
| Container Apps environment      | Shared home of all containers; they reach each other by name, like in compose                        |
| Container app `web`             | Public HTTPS web UI (Next.js); logs users in with Keycloak and calls the API                         |
| Container app `api`             | Public HTTPS API, owns the database schema (Liquibase), sends outbox messages                        |
| Container app `worker`          | Processes documents from the queue (PDF parsing, language model); no ingress                         |
| Container app `rabbitmq`        | Message broker, reachable only inside the environment (`rabbitmq:5672`)                              |
| Container app `keycloak`        | Login server in production mode with the `dewtally` realm and demo users                             |
| PostgreSQL Flexible Server      | Databases `dewtally` and `keycloak`, encrypted connections only                                      |
| Key Vault                       | Generated passwords (database, RabbitMQ, Keycloak admin, web client, session key) and the OpenAI key |
| Managed identity                | Lets the containers read secrets from Key Vault; nothing else                                        |
| Storage account with file share | Uploaded documents, shared by api and worker at `/app/data`                                          |
| Log Analytics workspace         | Container logs, with a daily cap                                                                     |
| Budget                          | E-mail at 80 % of the monthly budget and when Azure forecasts more than 100 %                        |

The images come from GitHub Container Registry, published by CI as public packages, so Azure needs no registry
login: `ghcr.io/newways-dev/dewtally` (api and worker), `ghcr.io/newways-dev/dewtally-keycloak` and
`ghcr.io/newways-dev/dewtally-web`.

Every address is known before anything is created: Azure names each app `<app>.<environment domain>`. So Keycloak
is told the web UI's address on its first start, and browser, web UI and API all use the same public Keycloak
address, which is also the issuer in every token.

## Requirements

- An Azure subscription
- [Azure CLI](https://learn.microsoft.com/cli/azure/install-azure-cli) and Terraform 1.9 or newer
- An OpenAI API key (for plain PDF invoices)

## Create the environment

PowerShell:

```powershell
az login
$env:ARM_SUBSCRIPTION_ID = (az account show --query id -o tsv)
$env:TF_VAR_openai_api_key = "<your OpenAI key>"     # never written to a file
$env:TF_VAR_budget_email   = "<your e-mail address>"

cd infra
terraform init
terraform plan -out env.tfplan     # shows what will be created
terraform apply env.tfplan
terraform output                   # web_url, api_url, keycloak_url, ...
```

The first start of Keycloak and the api takes a few minutes (database schema, realm import).

Run a specific version instead of `latest`:

```powershell
terraform apply -var "image_tag=0.7.0"
```

## Try it

Open the web UI and log in with a demo user:

```powershell
start (terraform output -raw web_url)
```

The demo users are the same as locally: `anna` (clerk), `ben` (approver), `carla` (admin) of Demo GmbH and `otto`
(clerk) of Other AG; the password equals the user name. The two tenants must exist in the database (see the
main README).

Or call the API directly:

```powershell
$api = terraform output -raw api_url
$keycloak = terraform output -raw keycloak_url
$token = (Invoke-RestMethod -Method Post -Uri "$keycloak/realms/dewtally/protocol/openid-connect/token" `
    -Body @{ grant_type = "password"; client_id = "dewtally-cli"; username = "anna"; password = "anna" }).access_token
Invoke-RestMethod -Uri "$api/api/invoices" -Headers @{ Authorization = "Bearer $token" }
```

## Remove the environment

```powershell
terraform destroy
```

Everything is deleted, including the databases and uploaded documents. Key Vault is purged for good, so the next
`apply` starts clean.

## Known limits (deliberate for a demo)

- **State is local** (`terraform.tfstate`, never committed). A team would keep it in an Azure Storage backend.
- **The realm is imported on Keycloak's first start only.** Later changes to the realm file, the web UI's address or
  its client secret do not reach a running Keycloak; change them in its admin console or start with a fresh
  environment (`destroy`, then `apply`).
- **PostgreSQL is reachable from Azure services**, not from a private network. A virtual network would be stricter.
- **One database user** for the application and Keycloak; the Azure provider cannot create users inside PostgreSQL.
- **RabbitMQ keeps messages in memory**: a restart loses undelivered ones. Production would use persistent storage
  or a managed broker.
- **Documents on a file share**: Azure Blob Storage in the code would be the cloud-native way.
- **One instance of everything**: the worker could scale by queue length (KEDA), Keycloak would need a cache cluster.