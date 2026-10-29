# Alarm, not a brake: Azure keeps running and billing, but sends an e-mail early enough to react
# (usually: "terraform destroy").
resource "azurerm_consumption_budget_resource_group" "main" {
  name              = "budget-${var.prefix}"
  resource_group_id = azurerm_resource_group.main.id
  amount            = var.budget_amount
  time_grain        = "Monthly"

  # A budget must start on the first day of a month: the current one when the environment is created.
  time_period {
    start_date = formatdate("YYYY-MM-01'T'00:00:00Z", timestamp())
  }

  # Real spending has passed 80 % of the budget
  notification {
    enabled        = true
    threshold      = 80
    operator       = "GreaterThan"
    threshold_type = "Actual"
    contact_emails = [var.budget_email]
  }

  # Azure expects the month to end above the budget if nothing changes
  notification {
    enabled        = true
    threshold      = 100
    operator       = "GreaterThan"
    threshold_type = "Forecasted"
    contact_emails = [var.budget_email]
  }

  # timestamp() changes on every run; the start date is only set once, when the budget is created.
  lifecycle {
    ignore_changes = [time_period]
  }
}
