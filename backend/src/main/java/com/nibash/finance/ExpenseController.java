package com.nibash.finance;

import com.nibash.auth.CurrentUser;
import com.nibash.building.Building;
import com.nibash.building.BuildingRepository;
import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
import com.nibash.common.Policy;
import com.nibash.finance.FinanceDtos.ExpenseDto;
import com.nibash.finance.FinanceDtos.MonthlyExpenseRow;
import com.nibash.storage.StorageService;
import com.nibash.tenancy.TenantService;
import com.nibash.user.User;
import com.nibash.vendor.VendorRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** {@code /api/expenses/} — CommitteeOrAdmin (spec §8.3). */
@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseRepository expenses;
    private final BuildingRepository buildings;
    private final VendorRepository vendors;
    private final TenantService tenancy;
    private final StorageService storage;

    public ExpenseController(ExpenseRepository expenses, BuildingRepository buildings,
                             VendorRepository vendors, TenantService tenancy, StorageService storage) {
        this.expenses = expenses;
        this.buildings = buildings;
        this.vendors = vendors;
        this.tenancy = tenancy;
        this.storage = storage;
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<ExpenseDto> list(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(name = "building_id", required = false) Long buildingId,
                                         @RequestParam(required = false) String category) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "date").and(Sort.by(Sort.Direction.DESC, "id")));

        var result = category == null || category.isBlank()
                ? expenses.findByBuildingIdIn(scope, pageable)
                : expenses.findByBuildingIdInAndCategory(scope, category, pageable);
        return PageEnvelope.of(result, ExpenseDto::from);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public ExpenseDto detail(@PathVariable Long id) {
        return ExpenseDto.from(scoped(id));
    }

    /**
     * Create an expense. Accepts JSON or multipart so the receipt can arrive with the row in one
     * request — the frontend's form does exactly that.
     */
    @PostMapping(value = "/", consumes = {"application/json", "multipart/form-data"})
    @Transactional
    public ResponseEntity<ExpenseDto> create(@RequestParam(required = false) Map<String, Object> form,
                                             @RequestBody(required = false) Map<String, Object> json,
                                             @RequestPart(name = "receipt", required = false) MultipartFile receipt) {
        Policy.requireManager();
        User caller = CurrentUser.require();
        Map<String, Object> body = json != null ? json : form == null ? Map.of() : form;

        Long buildingId = Body.requireLong(body, "building");
        tenancy.requireAccess(caller, buildingId);
        Building building = buildings.findById(buildingId)
                .orElseThrow(() -> ApiException.notFound("Not found."));

        Expense expense = new Expense();
        expense.setBuilding(building);
        expense.setCreatedBy(caller);
        expense.setCategory(Body.requireStr(body, "category"));
        expense.setAmount(requireAmount(body));
        expense.setDate(requireDate(body));
        apply(expense, body);

        if (receipt != null && !receipt.isEmpty()) {
            expense.setReceiptPath(storage.store(receipt, "receipts"));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(ExpenseDto.from(expenses.save(expense)));
    }

    @PatchMapping("/{id}/")
    @Transactional
    public ExpenseDto update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Policy.requireManager();
        Expense expense = scoped(id);
        if (body.containsKey("category")) {
            expense.setCategory(Body.requireStr(body, "category"));
        }
        if (body.containsKey("amount")) {
            expense.setAmount(requireAmount(body));
        }
        if (body.containsKey("date")) {
            expense.setDate(requireDate(body));
        }
        apply(expense, body);
        return ExpenseDto.from(expenses.save(expense));
    }

    @PutMapping("/{id}/")
    @Transactional
    public ExpenseDto replace(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return update(id, body);
    }

    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Policy.requireManager();
        expenses.delete(scoped(id));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /** Attach or replace a receipt on an existing expense. */
    @PostMapping("/{id}/receipt/")
    @Transactional
    public ExpenseDto uploadReceipt(@PathVariable Long id,
                                    @RequestPart(name = "file", required = false) MultipartFile file) {
        Policy.requireManager();
        Expense expense = scoped(id);
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("file is required");
        }
        expense.setReceiptPath(storage.store(file, "receipts"));
        return ExpenseDto.from(expenses.save(expense));
    }

    /** Spend by month and category (spec §8.3) — the stacked chart on the expenses screen. */
    @GetMapping("/reports/monthly/")
    @Transactional(readOnly = true)
    public Map<String, Object> monthlyReport(@RequestParam(name = "building_id", required = false) Long buildingId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return Map.of("results", List.of());
        }
        List<MonthlyExpenseRow> rows = expenses.monthlyReport(scope).stream()
                .map(row -> new MonthlyExpenseRow(
                        (String) row[0],
                        (String) row[1],
                        (BigDecimal) row[2],
                        ((Number) row[3]).longValue()))
                .toList();
        return Map.of("results", rows);
    }

    private void apply(Expense expense, Map<String, Object> body) {
        if (body.containsKey("description")) {
            expense.setDescription(Body.str(body, "description"));
        }
        if (body.containsKey("vendor")) {
            Long vendorId = Body.asLong(body, "vendor");
            expense.setVendor(vendorId == null ? null
                    : vendors.findById(vendorId).orElseThrow(() -> ApiException.notFound("Not found.")));
        }
    }

    /** Spec §8.3 quotes both messages, so they are part of the contract. */
    private static BigDecimal requireAmount(Map<String, Object> body) {
        BigDecimal amount = Body.asDecimal(body, "amount");
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("Amount must be positive.");
        }
        return amount;
    }

    private static LocalDate requireDate(Map<String, Object> body) {
        LocalDate date = Body.asDate(body, "date");
        if (date == null) {
            throw ApiException.badRequest("date is required");
        }
        if (date.isAfter(LocalDate.now())) {
            throw ApiException.badRequest("Expense date cannot be in the future.");
        }
        return date;
    }

    private Expense scoped(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return expenses.findByIdAndBuildingIdIn(id, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));
    }
}
