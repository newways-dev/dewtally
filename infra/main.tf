# Everything of the environment lives in one resource group:
# "terraform destroy" removes it completely, so nothing keeps costing money.
resource "azurerm_resource_group" "main" {
  name     = "rg-${var.prefix}"
  location = var.location
  tags     = var.tags
}
