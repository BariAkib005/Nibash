package com.nibash.finance;

import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
import com.nibash.common.Policy;
import com.nibash.finance.FinanceDtos.BillTypeDto;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * {@code /api/bill-types/} — CommitteeOrAdmin, and deliberately <b>global</b>: bill types are a
 * shared vocabulary, not building data, so no tenant filter applies (spec §8.3).
 */
@RestController
@RequestMapping("/api/bill-types")
public class BillTypeController {

    private final BillTypeRepository billTypes;

    public BillTypeController(BillTypeRepository billTypes) {
        this.billTypes = billTypes;
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<BillTypeDto> list(@RequestParam(defaultValue = "1") int page) {
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE);
        return PageEnvelope.of(billTypes.findAllByOrderByNameAsc(pageable), BillTypeDto::from);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public BillTypeDto detail(@PathVariable Long id) {
        return BillTypeDto.from(find(id));
    }

    @PostMapping("/")
    @Transactional
    public ResponseEntity<BillTypeDto> create(@RequestBody Map<String, Object> body) {
        Policy.requireManager();
        String name = Body.requireStr(body, "name");
        billTypes.findByName(name).ifPresent(existing -> {
            throw ApiException.badRequest("A bill type with this name already exists.");
        });

        BillType billType = new BillType();
        billType.setName(name);
        billType.setDescription(Body.str(body, "description"));
        return ResponseEntity.status(HttpStatus.CREATED).body(BillTypeDto.from(billTypes.save(billType)));
    }

    @PatchMapping("/{id}/")
    @Transactional
    public BillTypeDto update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Policy.requireManager();
        BillType billType = find(id);
        if (body.containsKey("name")) {
            billType.setName(Body.requireStr(body, "name"));
        }
        if (body.containsKey("description")) {
            billType.setDescription(Body.str(body, "description"));
        }
        return BillTypeDto.from(billTypes.save(billType));
    }

    @PutMapping("/{id}/")
    @Transactional
    public BillTypeDto replace(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return update(id, body);
    }

    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Policy.requireManager();
        billTypes.delete(find(id));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private BillType find(Long id) {
        return billTypes.findById(id).orElseThrow(() -> ApiException.notFound("Not found."));
    }
}
