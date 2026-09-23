package com.nibash.chat;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tenant path: {@code ChatRoom -> building} (spec §6.3). */
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByIdAndBuildingIdIn(Long id, List<Long> buildingIds);

    Optional<ChatRoom> findByBuildingIdAndName(Long buildingId, String name);

    /**
     * Rooms ordered by latest message activity, most recent first, then by name (spec §9) — the
     * order the room list and the dashboard both use. Rooms with no messages sort last.
     */
    @Query("""
           select r from ChatRoom r
           where r.building.id in :buildingIds
           order by (select max(m.sentAt) from Message m where m.room = r) desc nulls last, r.name asc
           """)
    List<ChatRoom> findByActivity(@Param("buildingIds") List<Long> buildingIds);

    long countByBuildingIdIn(List<Long> buildingIds);
}
