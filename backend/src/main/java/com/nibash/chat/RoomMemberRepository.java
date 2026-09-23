package com.nibash.chat;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tenant path: {@code RoomMember -> room -> building} (spec §6.3). */
public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {

    Page<RoomMember> findByRoomBuildingIdIn(List<Long> buildingIds, Pageable pageable);

    Page<RoomMember> findByRoomBuildingIdInAndRoomId(List<Long> buildingIds, Long roomId, Pageable pageable);

    Optional<RoomMember> findByIdAndRoomBuildingIdIn(Long id, List<Long> buildingIds);

    Optional<RoomMember> findByRoomIdAndResidentId(Long roomId, Long residentId);

    boolean existsByRoomIdAndResidentUserId(Long roomId, Long userId);

    /** Every member except the sender — the recipients of the chat notification side effect. */
    @Query("""
           select m from RoomMember m join fetch m.resident
           where m.room.id = :roomId and m.resident.id <> :senderId
           """)
    List<RoomMember> findOthers(@Param("roomId") Long roomId, @Param("senderId") Long senderId);

    /** Room ids the user belongs to, for filtering private rooms out of a list. */
    @Query("select m.room.id from RoomMember m where m.resident.user.id = :userId")
    List<Long> findRoomIdsForUser(@Param("userId") Long userId);
}
