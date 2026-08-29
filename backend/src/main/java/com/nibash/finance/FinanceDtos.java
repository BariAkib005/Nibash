package com.nibash.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Read shapes for the finance module (spec §8.3). Same rule as the registry DTOs: columns flat, FKs
 * as raw ids, and a few denormalised names the frontend would otherwise round-trip for.
 */
public final class FinanceDtos {

    private FinanceDtos() {
    }

    public record BillTypeDto(Long id, String name, String description) {

        public static BillTypeDto from(BillType b) {
            return new BillTypeDto(b.getId(), b.getName(), b.getDescription());
        }
    }

    public record InvoiceItemDto(
            Long id, Long invoice, String description, BigDecimal quantity, BigDecimal unitPrice,
            BigDecimal taxAmount, BigDecimal totalAmount, Integer utilityBillId) {

        public static InvoiceItemDto from(InvoiceItem i) {
            return new InvoiceItemDto(i.getId(), i.getInvoice().getId(), i.getDescription(),
                    i.getQuantity(), i.getUnitPrice(), i.getTaxAmount(), i.getTotalAmount(),
                    i.getUtilityBillId());
        }
    }

    public record InvoiceDto(
            Long id, String invoiceNumber, Long resident, Long building, Long billType,
            BigDecimal amount, LocalDate dueDate, String status, LocalDateTime createdAt,
            LocalDateTime updatedAt, List<InvoiceItemDto> items,
            String residentName, String unitNumber) {

        public static InvoiceDto from(Invoice i) {
            var resident = i.getResident();
            var unit = resident.getUnit();
            return new InvoiceDto(
                    i.getId(), i.getInvoiceNumber(), resident.getId(), i.getBuilding().getId(),
                    i.getBillType() == null ? null : i.getBillType().getId(),
                    i.getAmount(), i.getDueDate(), i.getStatus(), i.getCreatedAt(), i.getUpdatedAt(),
                    i.getItems().stream().map(InvoiceItemDto::from).toList(),
                    resident.getUser().getName(),
                    unit == null ? null : unit.getUnitNumber());
        }
    }

    public record PaymentDto(
            Long id, Long invoice, Long resident, BigDecimal amount, LocalDateTime paymentDate,
            String method, String transactionId, String invoiceNumber) {

        public static PaymentDto from(Payment p) {
            return new PaymentDto(p.getId(), p.getInvoice().getId(), p.getResident().getId(),
                    p.getAmount(), p.getPaymentDate(), p.getMethod(), p.getTransactionId(),
                    p.getInvoice().getInvoiceNumber());
        }
    }

    public record ExpenseDto(
            Long id, Long building, String category, BigDecimal amount, String description,
            LocalDate date, String receiptPath, Long createdBy, Long vendor,
            LocalDateTime createdAt, String createdByName) {

        public static ExpenseDto from(Expense e) {
            return new ExpenseDto(e.getId(), e.getBuilding().getId(), e.getCategory(), e.getAmount(),
                    e.getDescription(), e.getDate(), e.getReceiptPath(), e.getCreatedBy().getId(),
                    e.getVendor() == null ? null : e.getVendor().getId(), e.getCreatedAt(),
                    e.getCreatedBy().getName());
        }
    }

    /** One row of {@code GET /api/expenses/reports/monthly/}. */
    public record MonthlyExpenseRow(String month, String category, BigDecimal total, long entries) {
    }
}
