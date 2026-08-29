package com.nibash.community;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tenant path: {@code EventAttendee -> event -> building} (spec §6.3). */
public interface EventAttendeeRepository extends JpaRepository<EventAttendee, Long> {

    Page<EventAttendee> findByEventBuildingIdIn(List<Long> buildingIds, Pageable pageable);

    Optional<EventAttendee> findByIdAndEventBuildingIdIn(Long id, List<Long> buildingIds);

    /** The uniqueness key — an RSVP toggles this row rather than adding another. */
    Optional<EventAttendee> findByEventIdAndResidentId(Long eventId, Long residentId);

    List<EventAttendee> findByEventIdOrderByIdAsc(Long eventId);

    @Query("select a.event.id, count(a) from EventAttendee a where a.event.id in :eventIds and a.status = 'going' group by a.event.id")
    List<Object[]> countGoing(@Param("eventIds") List<Long> eventIds);
}
