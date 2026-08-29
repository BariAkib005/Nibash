package com.nibash.emergency;

import com.nibash.auth.CurrentUser;
import com.nibash.building.BuildingRepository;
import com.nibash.common.ApiException;
import com.nibash.common.Body;
import com.nibash.common.PageEnvelope;
import com.nibash.common.Policy;
import com.nibash.tenancy.TenantService;
import com.nibash.user.User;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** {@code /api/emergency-contacts/} — CommitteeOrAdmin (spec §8.20). */
@RestController
@RequestMapping("/api/emergency-contacts")
public class EmergencyContactController {

    private final EmergencyContactRepository contacts;
    private final BuildingRepository buildings;
    private final TenantService tenancy;

    public EmergencyContactController(EmergencyContactRepository contacts, BuildingRepository buildings,
                                      TenantService tenancy) {
        this.contacts = contacts;
        this.buildings = buildings;
        this.tenancy = tenancy;
    }

    public record ContactDto(Long id, Long building, String name, String phone, String type) {

        public static ContactDto from(EmergencyContact c) {
            return new ContactDto(c.getId(), c.getBuilding().getId(), c.getName(), c.getPhone(), c.getType());
        }
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public PageEnvelope<ContactDto> list(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(name = "building_id", required = false) Long buildingId) {
        List<Long> scope = tenancy.resolveScope(CurrentUser.require(), buildingId);
        if (scope.isEmpty()) {
            return new PageEnvelope<>(0, null, null, List.of());
        }
        var pageable = PageRequest.of(Math.max(page - 1, 0), PageEnvelope.PAGE_SIZE, Sort.by("type"));
        return PageEnvelope.of(contacts.findByBuildingIdIn(scope, pageable), ContactDto::from);
    }

    @GetMapping("/{id}/")
    @Transactional(readOnly = true)
    public ContactDto detail(@PathVariable Long id) {
        return ContactDto.from(scoped(id));
    }

    @PostMapping("/")
    @Transactional
    public ResponseEntity<ContactDto> create(@RequestBody Map<String, Object> body) {
        Policy.requireManager();
        User caller = CurrentUser.require();
        Long buildingId = Body.requireLong(body, "building");
        tenancy.requireAccess(caller, buildingId);

        EmergencyContact contact = new EmergencyContact();
        contact.setBuilding(buildings.findById(buildingId)
                .orElseThrow(() -> ApiException.notFound("Not found.")));
        contact.setName(Body.requireStr(body, "name"));
        contact.setPhone(Body.requireStr(body, "phone"));
        contact.setType(Body.requireStr(body, "type"));
        return ResponseEntity.status(HttpStatus.CREATED).body(ContactDto.from(contacts.save(contact)));
    }

    @PatchMapping("/{id}/")
    @Transactional
    public ContactDto update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Policy.requireManager();
        EmergencyContact contact = scoped(id);
        if (body.containsKey("name")) {
            contact.setName(Body.requireStr(body, "name"));
        }
        if (body.containsKey("phone")) {
            contact.setPhone(Body.requireStr(body, "phone"));
        }
        if (body.containsKey("type")) {
            contact.setType(Body.requireStr(body, "type"));
        }
        return ContactDto.from(contacts.save(contact));
    }

    @PutMapping("/{id}/")
    @Transactional
    public ContactDto replace(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return update(id, body);
    }

    @DeleteMapping("/{id}/")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Policy.requireManager();
        contacts.delete(scoped(id));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private EmergencyContact scoped(Long id) {
        List<Long> allowed = tenancy.allowedBuildingIds(CurrentUser.require());
        return contacts.findByIdAndBuildingIdIn(id, allowed)
                .orElseThrow(() -> ApiException.notFound("Not found."));
    }
}
