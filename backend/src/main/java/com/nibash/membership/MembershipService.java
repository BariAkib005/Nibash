package com.nibash.membership;

import com.nibash.building.Building;
import com.nibash.building.BuildingRepository;
import com.nibash.resident.Resident;
import com.nibash.resident.ResidentRepository;
import com.nibash.staffing.Staff;
import com.nibash.staffing.StaffRepository;
import com.nibash.unit.Unit;
import com.nibash.user.User;
import com.nibash.user.UserRepository;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Attaching an account to a building — shared by invitations and by approving a rental applicant
 * from outside. Membership is what tenancy reads: a resident row, a staff row, or owning the building.
 */
@Service
public class MembershipService {

    private final ResidentRepository residents;
    private final StaffRepository staff;
    private final BuildingRepository buildings;
    private final UserRepository users;

    public MembershipService(ResidentRepository residents, StaffRepository staff, BuildingRepository buildings,
                             UserRepository users) {
        this.residents = residents;
        this.staff = staff;
        this.buildings = buildings;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public boolean hasAnyBuilding(User user) {
        return !residents.findBuildingIdsForUser(user.getId()).isEmpty()
                || !staff.findBuildingIdsForUser(user.getId()).isEmpty()
                || !buildings.findIdsOwnedBy(user.getId()).isEmpty();
    }

    @Transactional(readOnly = true)
    public boolean belongsTo(User user, Long buildingId) {
        return residents.findBuildingIdsForUser(user.getId()).contains(buildingId)
                || staff.findBuildingIdsForUser(user.getId()).contains(buildingId)
                || buildings.findIdsOwnedBy(user.getId()).contains(buildingId);
    }

    /**
     * Why an existing account can't join a building as {@code role}, or null when it can.
     *
     * <p>A Nibash account holds one role across every building it belongs to, so joining as
     * something else would carry the new role — and its permissions — into the account's other
     * buildings: a resident of one building made committee in another would become committee in
     * both. An account with no building yet (a renter who signed up to apply for a flat) can take
     * any role.
     */
    @Transactional(readOnly = true)
    public String roleConflict(User user, String role) {
        if (user.isBackOffice()) {
            return "This email belongs to a back-office account, which already has access to every building.";
        }
        if (!user.isActive()) {
            return "The account for " + user.getEmail() + " has been deactivated.";
        }
        if (role.equals(user.getRole()) || !hasAnyBuilding(user)) {
            return null;
        }
        return user.getEmail() + " already has a " + user.getRole() + " account in another building. "
                + "A Nibash account has one role, so use a different email for this one.";
    }

    /** Gives the account {@code role}; only call after {@link #roleConflict} has returned null. */
    @Transactional
    public void adoptRole(User user, String role) {
        if (!role.equals(user.getRole())) {
            user.setRole(role);
            users.save(user);
        }
    }

    @Transactional
    public Resident addResident(User user, Building building, Unit unit, boolean owner, LocalDate startDate) {
        Resident resident = new Resident();
        resident.setUser(user);
        resident.setBuilding(building);
        resident.setUnit(unit);
        resident.setOwner(owner);
        resident.setStartDate(startDate);
        return residents.save(resident);
    }

    @Transactional
    public Staff addStaff(User user, Building building, String role, String designation, String contact) {
        Staff member = new Staff();
        member.setUser(user);
        member.setBuilding(building);
        member.setName(user.getName());
        member.setRole(role);
        member.setDesignation(designation);
        member.setContactInfo(contact == null || contact.isBlank() ? null : contact);
        return staff.save(member);
    }
}
