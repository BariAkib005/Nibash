package com.nibash.finance;

import com.nibash.auth.CurrentUser;
import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
import com.nibash.common.Times;
import com.nibash.finance.FinanceDtos.PaymentDto;
import com.nibash.tenancy.TenantService;
import com.nibash.user.User;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** {@code /api/payments/} — IsAuthenticated (spec §8.3). */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PaymentRepository payments;
    private final InvoiceRepository invoices;
    private final TenantService tenancy;

    public PaymentController(PaymentRepository payments, InvoiceRepository invoices, TenantService tenancy) {
        this.payments = payments;
        this.invoices = invoices;
        this.tenancy = tenancy;
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<PaymentDto> list(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(name = "building_id", required = false) Long buildingId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "paymentDate"));
        return PageEnvelope.of(payments.findByInvoiceBuildingIdIn(scope, pageable), PaymentDto::from);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public PaymentDto detail(@PathVariable Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return PaymentDto.from(payments.findByIdAndInvoiceBuildingIdIn(id, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found.")));
    }

    /**
     * Pay an invoice (spec §8.3). The demo gateway always succeeds; what matters here is that the
     * money side is exactly once.
     *
     * <p>The invoice is read with {@code SELECT … FOR UPDATE}, so two concurrent checkouts of the
     * same invoice serialise: the first flips the status inside the transaction, the second blocks
     * on the row lock and then sees {@code paid} and is rejected. Reading without the lock would let
     * both pass the already-paid check and write two payment rows.
     */
    @PostMapping("/checkout/")
    @Transactional
    public ResponseEntity<Map<String, Object>> checkout(@RequestBody Map<String, Object> body) {
        User caller = CurrentUser.require();
        Long invoiceId = Body.requireLong(body, "invoice_id");
        List<Long> allowed = tenancy.allowedBuildingIds(caller);

        Invoice invoice = invoices.lockByIdAndBuildingIdIn(invoiceId, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));

        if (Invoice.PAID.equals(invoice.getStatus())) {
            throw ApiException.badRequest("Invoice is already paid.");
        }

        String method = Body.str(body, "method");
        String transactionId = Body.str(body, "transaction_id");

        Payment payment = new Payment();
        payment.setInvoice(invoice);
        payment.setResident(invoice.getResident());
        payment.setAmount(invoice.getAmount() == null ? BigDecimal.ZERO : invoice.getAmount());
        payment.setPaymentDate(Times.now());
        payment.setMethod(method == null || method.isBlank() ? "card" : method.trim());
        payment.setTransactionId(transactionId == null || transactionId.isBlank()
                ? demoTransactionId() : transactionId.trim());
        Payment saved = payments.save(payment);

        invoice.setStatus(Invoice.PAID);
        invoices.save(invoice);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("checkout_status", "paid");
        response.put("transaction_id", saved.getTransactionId());
        response.put("payment", PaymentDto.from(saved));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private static String demoTransactionId() {
        byte[] bytes = new byte[6];
        RANDOM.nextBytes(bytes);
        return "demo_" + HexFormat.of().formatHex(bytes);
    }
}
