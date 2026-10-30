package com.kate.dewtally.validation;

import com.kate.dewtally.invoice.Invoice;
import com.kate.dewtally.supplier.Iban;
import com.kate.dewtally.supplier.SupplierCheck;
import com.kate.dewtally.supplier.SupplierService;
import com.kate.dewtally.supplier.SupplierStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Checks an invoice against the tenant's supplier master data. The main protection against
 * invoice fraud: invoices from unknown senders and, above all, a changed bank account.
 * A language model cannot detect this: a fraudulent invoice can be perfectly well-formed.
 */
@Component
public class SupplierRule implements InvoiceRule {

    // A changed IBAN is the classic fraud pattern; Invoice.accept() requires an explicit confirmation.
    public static final String IBAN_CHANGED = "DT-IBAN-CHANGED";

    private final SupplierService suppliers;

    public SupplierRule(SupplierService suppliers) {
        this.suppliers = suppliers;
    }

    @Override
    public List<Finding> check(ValidationContext context) {
        Invoice invoice = context.invoice();
        UUID tenantId = invoice.getTenantId();
        List<Finding> findings = new ArrayList<>();

        String iban = Iban.normalize(invoice.getIban());
        boolean ibanValid = iban == null || Iban.isValid(iban);
        if (!ibanValid) {
            findings.add(Finding.error("DT-IBAN-INVALID",
                    "Die IBAN " + iban + " ist ungültig (Prüfsumme oder Format)."));
        }

        // Tenants without master data are not flooded with "unknown supplier" on every invoice.
        if (!suppliers.hasSuppliers(tenantId)) {
            return findings;
        }

        Optional<SupplierCheck> supplier =
                suppliers.findForInvoice(tenantId, invoice.getSupplierVatId(), invoice.getSupplierName());
        if (supplier.isEmpty()) {
            findings.add(Finding.warning("DT-UNKNOWN-SUPPLIER",
                    "Der Lieferant ist nicht im Lieferantenstamm. Bitte prüfen, ob die Rechnung erwartet wurde."));
            return findings;
        }

        SupplierCheck known = supplier.get();
        if (known.status() == SupplierStatus.BLOCKED) {
            findings.add(Finding.warning("DT-SUPPLIER-BLOCKED",
                    "Der Lieferant " + known.name() + " ist gesperrt."));
        }

        if (iban != null && ibanValid && !known.verifiedIbans().contains(iban)) {
            if (known.pendingIbans().contains(iban)) {
                findings.add(Finding.warning("DT-IBAN-UNVERIFIED",
                        "Die IBAN ist hinterlegt, aber noch nicht im Vier-Augen-Prinzip bestätigt."));
            } else {
                findings.add(Finding.warning(IBAN_CHANGED,
                        "Die IBAN weicht von den bekannten Bankverbindungen des Lieferanten ab. "
                                + "Vor der Zahlung beim Lieferanten über eine bekannte Telefonnummer bestätigen."));
            }
        }
        return findings;
    }
}