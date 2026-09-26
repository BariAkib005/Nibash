package com.nibash.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.nibash.common.Times;
import com.nibash.support.ApiTestSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;

/**
 * Getting people into a building: an owner who signed up invites staff, committee and residents,
 * and someone from outside rents a published flat. Every account here is created by the test, so the
 * demo accounts' memberships — which other suites count on — never change.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MembershipAndPublicRentalsTest extends ApiTestSupport {

    private static final String COMMITTEE = "committee1@nibash.bd";
    private static final String RESIDENT = "resident1@nibash.bd";
    private static final String TENANT = "resident2@nibash.bd";
    private static final String OTHER_COMMITTEE = "committee2@nibash.bd";
    private static final String PASSWORD = "Harbour-Lights-42";

    @Autowired InvitationRepository invitations;
    @MockitoBean JavaMailSender mail;

    // ---------------------------------------------------------------- invitations

    @Test
    void anOwnerInvitesAGuardWhoJoinsWithTheirOwnLogin() throws Exception {
        Owner owner = newOwner();
        String email = unique("guard") + "@example.com";
        JsonNode issued = postJson("/api/invitations/", owner.token(),
                "{\"building\":%d,\"name\":\"Rafiq Mia\",\"email\":\"%s\",\"role\":\"guard\",\"phone\":\"01700000000\"}"
                        .formatted(owner.building(), email), 201);
        assertThat(issued.get("invitation").get("status").asString()).isEqualTo("pending");
        assertThat(issued.get("invitation").get("staff_role").asString()).isEqualTo("Security");
        String token = tokenIn(issued);

        // The link is emailed too, pointing at the app, with the token only in the fragment.
        assertThat(issued.get("emailed").asBoolean()).isTrue();
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, atLeastOnce()).send(sent.capture());
        SimpleMailMessage invite = sent.getAllValues().stream()
                .filter(m -> m.getTo()[0].equals(email)).findFirst().orElseThrow();
        assertThat(invite.getText()).contains("/join#" + token).contains(owner.buildingName());

        JsonNode preview = postJson("/api/invitations/preview/", null, "{\"token\":\"%s\"}".formatted(token), 200);
        assertThat(preview.get("building_name").asString()).isEqualTo(owner.buildingName());
        assertThat(preview.get("role").asString()).isEqualTo("guard");
        assertThat(preview.get("account_exists").asBoolean()).isFalse();

        postJson("/api/invitations/accept/", null, "{\"token\":\"%s\",\"password\":\"12345678\"}".formatted(token), 400);
        JsonNode joined = postJson("/api/invitations/accept/", null,
                "{\"token\":\"%s\",\"password\":\"%s\"}".formatted(token, PASSWORD), 200);
        assertThat(joined.get("user").get("role").asString()).isEqualTo("guard");
        assertThat(joined.get("building").get("id").asLong()).isEqualTo(owner.building());
        String guard = joined.get("token").asString();
        long guardUser = joined.get("user").get("id").asLong();

        // The staff row is linked to the new login, so the guard can clock in for themselves.
        long staffId = -1;
        for (JsonNode row : getJson("/api/staff/?building_id=" + owner.building(), guard).get("results")) {
            if (row.get("user").asLong() == guardUser) {
                staffId = row.get("id").asLong();
                assertThat(row.get("role").asString()).isEqualTo("Security");
                assertThat(row.get("contact_info").asString()).isEqualTo("01700000000");
            }
        }
        assertThat(staffId).isPositive();
        postJson("/api/attendance/checkin/", guard, "{\"staff_id\":%d}".formatted(staffId), 201);

        // A link works once.
        postJson("/api/invitations/accept/", null, "{\"token\":\"%s\",\"password\":\"%s\"}".formatted(token, PASSWORD), 400);
        postJson("/api/invitations/preview/", null, "{\"token\":\"%s\"}".formatted(token), 400);
        assertThat(getJson("/api/invitations/?building_id=" + owner.building(), owner.token()).get("count").asLong())
                .isZero();
    }

    @Test
    void aResidentInvitationTakesItsFlatAndARenewedLinkRetiresTheOldOne() throws Exception {
        Owner owner = newOwner();
        long unit = newUnit(owner, "4C");
        JsonNode first = postJson("/api/invitations/", owner.token(),
                "{\"building\":%d,\"name\":\"Nadia Islam\",\"email\":\"%s\",\"role\":\"resident\",\"unit\":%d,\"is_owner\":true}"
                        .formatted(owner.building(), unique("nadia") + "@example.com", unit), 201);
        long id = first.get("invitation").get("id").asLong();
        assertThat(first.get("invitation").get("unit_number").asString()).isEqualTo("4C");

        JsonNode renewed = postJson("/api/invitations/" + id + "/renew/", owner.token(), null, 200);
        String oldToken = tokenIn(first);
        String newToken = tokenIn(renewed);
        assertThat(newToken).isNotEqualTo(oldToken);
        postJson("/api/invitations/preview/", null, "{\"token\":\"%s\"}".formatted(oldToken), 404);

        JsonNode joined = postJson("/api/invitations/accept/", null,
                "{\"token\":\"%s\",\"password\":\"%s\",\"name\":\"Nadia R. Islam\",\"phone\":\"01811111111\"}"
                        .formatted(newToken, PASSWORD), 200);
        assertThat(joined.get("user").get("role").asString()).isEqualTo("resident");
        assertThat(joined.get("user").get("name").asString()).isEqualTo("Nadia R. Islam");
        String resident = joined.get("token").asString();

        JsonNode rows = getJson("/api/residents/?building_id=" + owner.building(), resident).get("results");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("unit_number").asString()).isEqualTo("4C");
        assertThat(rows.get(0).get("is_owner").asBoolean()).isTrue();
        assertThat(getJson("/api/units/" + unit + "/", owner.token()).get("status").asString()).isEqualTo("occupied");
    }

    @Test
    void invitationsAreForManagersOfTheirOwnBuildings() throws Exception {
        Owner owner = newOwner();
        String committee = tokenFor(COMMITTEE);
        long gulshan = buildingId(committee);
        String body = "{\"building\":%d,\"name\":\"X\",\"email\":\"%s\",\"role\":\"resident\"}";

        postJson("/api/invitations/", tokenFor(RESIDENT), body.formatted(gulshan, unique("x") + "@example.com"), 403);
        postJson("/api/invitations/", owner.token(), body.formatted(gulshan, unique("x") + "@example.com"), 404);
        getJson("/api/invitations/?building_id=" + owner.building(), committee, 404);
        postJson("/api/invitations/", owner.token(),
                "{\"building\":%d,\"name\":\"X\",\"email\":\"x@example.com\",\"role\":\"admin\"}".formatted(owner.building()), 400);
        postJson("/api/invitations/", owner.token(),
                "{\"building\":%d,\"name\":\"X\",\"email\":\"%s\",\"role\":\"staff\"}"
                        .formatted(owner.building(), unique("x") + "@example.com"), 400); // staff need a job

        // Someone already here, and someone whose account holds a different role elsewhere.
        postJson("/api/invitations/", committee, body.formatted(gulshan, RESIDENT), 400);
        postJson("/api/invitations/", owner.token(),
                "{\"building\":%d,\"name\":\"X\",\"email\":\"%s\",\"role\":\"committee\"}".formatted(owner.building(), RESIDENT), 400);

        // One open invitation per email, and a revoked link is dead.
        String email = unique("once") + "@example.com";
        JsonNode issued = postJson("/api/invitations/", owner.token(), body.formatted(owner.building(), email), 201);
        postJson("/api/invitations/", owner.token(), body.formatted(owner.building(), email), 400);
        deleteJson("/api/invitations/" + issued.get("invitation").get("id").asLong() + "/", owner.token(), 204);
        postJson("/api/invitations/preview/", null, "{\"token\":\"%s\"}".formatted(tokenIn(issued)), 404);
    }

    @Test
    void anExpiredLinkIsRefusedAndAnExistingAccountJoinsWithItsOwnPassword() throws Exception {
        Owner owner = newOwner();
        JsonNode renter = newRenter();
        String email = renter.get("user").get("email").asString();

        JsonNode issued = postJson("/api/invitations/", owner.token(),
                "{\"building\":%d,\"name\":\"Existing\",\"email\":\"%s\",\"role\":\"committee\"}"
                        .formatted(owner.building(), email), 201);
        Invitation invitation = invitations.findById(issued.get("invitation").get("id").asLong()).orElseThrow();
        invitation.setExpiresAt(Times.now().minusMinutes(1));
        invitations.save(invitation);
        postJson("/api/invitations/preview/", null, "{\"token\":\"%s\"}".formatted(tokenIn(issued)), 400);

        JsonNode renewed = postJson("/api/invitations/" + invitation.getId() + "/renew/", owner.token(), null, 200);
        String token = tokenIn(renewed);
        assertThat(postJson("/api/invitations/preview/", null, "{\"token\":\"%s\"}".formatted(token), 200)
                .get("account_exists").asBoolean()).isTrue();
        postJson("/api/invitations/accept/", null, "{\"token\":\"%s\",\"password\":\"Wrong-Password-1\"}".formatted(token), 400);

        // A renter with no building yet can take the committee role.
        JsonNode joined = postJson("/api/invitations/accept/", null,
                "{\"token\":\"%s\",\"password\":\"%s\"}".formatted(token, PASSWORD), 200);
        assertThat(joined.get("user").get("id").asLong()).isEqualTo(renter.get("user").get("id").asLong());
        assertThat(joined.get("user").get("role").asString()).isEqualTo("committee");
        assertThat(buildingId(joined.get("token").asString())).isEqualTo(owner.building());
    }

    // ---------------------------------------------------------------- public rentals

    @Test
    void anOwnerPublishesAFlatAndSomeoneFromOutsideRentsIt() throws Exception {
        Owner owner = newOwner();
        long unit = newUnit(owner, "7A");
        String title = unique("Lake view 3BHK");
        JsonNode listing = postJson("/api/listings/", owner.token(),
                "{\"building\":%d,\"title\":\"%s\",\"description\":\"Airy\",\"rent\":\"70000\",\"available_from\":\"%s\",\"unit\":%d,\"is_public\":true}"
                        .formatted(owner.building(), title, LocalDate.now().plusMonths(1), unit), 201);
        long listingId = listing.get("id").asLong();
        assertThat(listing.get("resident").isNull()).isTrue();
        assertThat(listing.get("is_public").asBoolean()).isTrue();
        assertThat(listing.get("lister_user").asLong()).isEqualTo(owner.userId());

        JsonNode found = getJson("/api/public/listings/?search=" + title, null);
        assertThat(found.get("count").asLong()).isEqualTo(1);
        JsonNode card = found.get("results").get(0);
        assertThat(card.get("building_name").asString()).isEqualTo(owner.buildingName());
        assertThat(card.get("unit_number").asString()).isEqualTo("7A");
        assertThat(card.has("resident_name")).isFalse();
        assertThat(card.has("lister_user")).isFalse();
        getJson("/api/public/listings/" + listingId + "/", null);

        JsonNode renter = newRenter();
        String renterToken = renter.get("token").asString();
        long renterId = renter.get("user").get("id").asLong();
        assertThat(renter.get("building").isNull()).isTrue();
        assertThat(getJson("/api/auth/me/", renterToken).get("building").isNull()).isTrue();
        getJson("/api/listings/" + listingId + "/", renterToken, 404); // no tenancy yet

        postJson("/api/rental-applications/", renterToken,
                "{\"listing\":%d,\"message\":\"Family of three, moving from Chattogram.\"}".formatted(listingId), 201);
        postJson("/api/rental-applications/", renterToken, "{\"listing\":%d}".formatted(listingId), 400);
        JsonNode mine = getJson("/api/rental-applications/", renterToken).get("results");
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("status").asString()).isEqualTo("pending");
        assertThat(mine.get(0).get("building_name").asString()).isEqualTo(owner.buildingName());

        JsonNode request = getJson("/api/rental-requests/?building_id=" + owner.building(), owner.token())
                .get("results").get(0);
        assertThat(request.get("outside_applicant").asBoolean()).isTrue();
        assertThat(request.get("tenant").isNull()).isTrue();
        assertThat(request.get("tenant_user").asLong()).isEqualTo(renterId);
        assertThat(request.get("applicant_email").asString()).isEqualTo(renter.get("user").get("email").asString());
        assertThat(request.get("message").asString()).startsWith("Family of three");

        JsonNode approved = patchJson("/api/rental-requests/" + request.get("id").asLong() + "/", owner.token(),
                "{\"status\":\"approved\"}", 200);
        assertThat(approved.get("tenant").isNull()).isFalse();

        // Approval moved them in: a building, a resident row on the flat, the flat marked rented.
        assertThat(buildingId(renterToken)).isEqualTo(owner.building());
        JsonNode residents = getJson("/api/residents/?building_id=" + owner.building(), renterToken).get("results");
        assertThat(residents).hasSize(1);
        assertThat(residents.get(0).get("user").asLong()).isEqualTo(renterId);
        assertThat(residents.get(0).get("unit_number").asString()).isEqualTo("7A");
        assertThat(getJson("/api/units/" + unit + "/", owner.token()).get("status").asString()).isEqualTo("rented");

        // A let flat leaves the public page.
        assertThat(getJson("/api/public/listings/?search=" + title, null).get("count").asLong()).isZero();
        getJson("/api/public/listings/" + listingId + "/", null, 404);
        assertThat(getJson("/api/rental-applications/", renterToken).get("results").get(0).get("status").asString())
                .isEqualTo("approved");
    }

    @Test
    void onlyTheCommitteePublishesOrLetsSomeoneIn() throws Exception {
        String resident = tokenFor(RESIDENT);
        String committee = tokenFor(COMMITTEE);
        long gulshan = buildingId(resident);
        String payload = "{\"building\":%d,\"title\":\"%s\",\"description\":\"Quiet\",\"rent\":\"40000\",\"available_from\":\"%s\"%s}";

        postJson("/api/listings/", resident, payload.formatted(gulshan, unique("Flat"), LocalDate.now(), ",\"is_public\":true"), 403);
        long listing = postJson("/api/listings/", resident, payload.formatted(gulshan, unique("Flat"), LocalDate.now(), ""), 201)
                .get("id").asLong();
        getJson("/api/public/listings/" + listing + "/", null, 404); // inside the building only
        patchJson("/api/listings/" + listing + "/", resident, "{\"is_public\":true}", 403);
        patchJson("/api/listings/" + listing + "/", committee, "{\"is_public\":true}", 200);
        getJson("/api/public/listings/" + listing + "/", null, 200);

        JsonNode renter = newRenter();
        long renterId = renter.get("user").get("id").asLong();
        postJson("/api/rental-applications/", renter.get("token").asString(), "{\"listing\":%d}".formatted(listing), 201);

        // Accounts that could not become residents here can't apply at all.
        postJson("/api/rental-applications/", tokenFor(OTHER_COMMITTEE), "{\"listing\":%d}".formatted(listing), 400);
        postJson("/api/rental-applications/", resident, "{\"listing\":%d}".formatted(listing), 400); // own listing

        // The lister sees who is asking, a neighbour who isn't involved does not.
        long request = -1;
        for (JsonNode row : getJson("/api/rental-requests/?building_id=" + gulshan, resident).get("results")) {
            if (row.get("tenant_user").asLong() == renterId) {
                request = row.get("id").asLong();
                assertThat(row.get("applicant_email").isNull()).isFalse();
            }
        }
        assertThat(request).isPositive();
        for (JsonNode row : getJson("/api/rental-requests/?building_id=" + gulshan, tokenFor(TENANT)).get("results")) {
            assertThat(row.get("tenant_user").asLong()).isNotEqualTo(renterId);
        }
        getJson("/api/rental-requests/" + request + "/", tokenFor(TENANT), 404);

        // A resident lister can turn an outsider down, but only the committee can let them in.
        patchJson("/api/rental-requests/" + request + "/", resident, "{\"status\":\"approved\"}", 403);
        patchJson("/api/rental-requests/" + request + "/", resident, "{\"status\":\"rejected\"}", 200);
        assertThat(getJson("/api/rental-applications/", renter.get("token").asString()).get("results").get(0)
                .get("status").asString()).isEqualTo("rejected");
        assertThat(getJson("/api/auth/me/", renter.get("token").asString()).get("building").isNull()).isTrue();
    }

    // ---------------------------------------------------------------- helpers

    private record Owner(String token, long userId, long building, String buildingName) {
    }

    /** A brand-new workspace, exactly as the signup page creates one: an admin who owns one building. */
    private Owner newOwner() throws Exception {
        String name = unique("Tower");
        JsonNode created = postJson("/api/auth/signup/", null,
                "{\"name\":\"Owner\",\"email\":\"%s@example.com\",\"password\":\"%s\",\"building_name\":\"%s\"}"
                        .formatted(unique("owner"), PASSWORD, name), 201);
        return new Owner(created.get("token").asString(), created.get("user").get("id").asLong(),
                created.get("building").get("id").asLong(), name);
    }

    private JsonNode newRenter() throws Exception {
        ensureSeeded();
        return postJson("/api/auth/signup/renter/", null,
                "{\"name\":\"Rina Das\",\"email\":\"%s@example.com\",\"password\":\"%s\",\"phone\":\"01900000000\"}"
                        .formatted(unique("renter"), PASSWORD), 201);
    }

    private long newUnit(Owner owner, String number) throws Exception {
        return postJson("/api/units/", owner.token(),
                "{\"building\":%d,\"unit_number\":\"%s\",\"floor\":4,\"type\":\"3BHK\"}".formatted(owner.building(), number), 201)
                .get("id").asLong();
    }

    private static String tokenIn(JsonNode issued) {
        String path = issued.get("invite_path").asString();
        assertThat(path).startsWith("/join#");
        return path.substring("/join#".length());
    }
}
