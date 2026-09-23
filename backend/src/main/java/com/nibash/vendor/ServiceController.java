package com.nibash.vendor;

import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
import com.nibash.common.Policy;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** {@code /api/services/} — CommitteeOrAdmin, global catalog (spec §8.2). */
@RestController
@RequestMapping("/api/services")
public class ServiceController {

    private final ServiceRepository services;

    public ServiceController(ServiceRepository services) {
        this.services = services;
    }

    public record ServiceDto(Long id, String name, Long parent) {

        public static ServiceDto from(Service s) {
            return new ServiceDto(s.getId(), s.getName(), s.getParent() == null ? null : s.getParent().getId());
        }
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<ServiceDto> list(@RequestParam(defaultValue = "1") int page) {
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE, Sort.by("name"));
        return PageEnvelope.of(services.findAll(pageable), ServiceDto::from);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public ServiceDto detail(@PathVariable Long id) {
        return ServiceDto.from(find(id));
    }

    @PostMapping("/")
    @Transactional
    public ResponseEntity<ServiceDto> create(@RequestBody Map<String, Object> body) {
        Policy.requireManager();
        Service service = new Service();
        service.setName(Body.requireStr(body, "name"));
        apply(service, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ServiceDto.from(services.save(service)));
    }

    @PatchMapping("/{id}/")
    @Transactional
    public ServiceDto update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Policy.requireManager();
        Service service = find(id);
        if (body.containsKey("name")) {
            service.setName(Body.requireStr(body, "name"));
        }
        apply(service, body);
        return ServiceDto.from(services.save(service));
    }

    @PutMapping("/{id}/")
    @Transactional
    public ServiceDto replace(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return update(id, body);
    }

    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Policy.requireManager();
        services.delete(find(id));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private void apply(Service service, Map<String, Object> body) {
        if (body.containsKey("parent")) {
            Long parentId = Body.asLong(body, "parent");
            if (parentId != null && parentId.equals(service.getId())) {
                throw ApiException.badRequest("A service cannot be its own parent.");
            }
            service.setParent(parentId == null ? null : find(parentId));
        }
    }

    private Service find(Long id) {
        return services.findById(id).orElseThrow(() -> ApiException.notFound("Not found."));
    }
}
