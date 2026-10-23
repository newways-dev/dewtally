package com.kate.dewtally.invoice;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A single line of an invoice, e.g. "10 h consulting at 100.00 EUR, 19 % VAT".
 */
@Entity
@Table(name = "invoice_line")
public class InvoiceLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // LAZY: loading a line must not load the whole invoice.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private Invoice invoice;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "quantity", precision = 15, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_code", length = 10)
    private String unitCode;

    @Column(name = "unit_price", precision = 15, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "net_amount", precision = 15, scale = 2)
    private BigDecimal netAmount;

    @Column(name = "tax_rate", precision = 5, scale = 2)
    private BigDecimal taxRate;

    protected InvoiceLine() {
        // Required by JPA.
    }

    public InvoiceLine(int lineNo,
                       String description,
                       BigDecimal quantity,
                       String unitCode,
                       BigDecimal unitPrice,
                       BigDecimal netAmount,
                       BigDecimal taxRate) {
        this.lineNo = lineNo;
        this.description = description;
        this.quantity = quantity;
        this.unitCode = unitCode;
        this.unitPrice = unitPrice;
        this.netAmount = netAmount;
        this.taxRate = taxRate;
    }

    // Package-private: only Invoice.addLine() may connect a line to an invoice.
    void attachTo(Invoice invoice) {
        this.invoice = invoice;
    }

    // Package-private: only Invoice.correct() may change a line.
    // The line is updated in place (not replaced), so its line number stays unique at all times.
    void update(InvoiceLineData data) {
        this.description = data.description();
        this.quantity = data.quantity();
        this.unitCode = data.unitCode();
        this.unitPrice = data.unitPrice();
        this.netAmount = data.netAmount();
        this.taxRate = data.taxRate();
    }

    public UUID getId() {
        return id;
    }

    public int getLineNo() {
        return lineNo;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public String getUnitCode() {
        return unitCode;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getNetAmount() {
        return netAmount;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }
}