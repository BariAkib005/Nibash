package com.nibash.chat;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tenant path: {@code Message -> room -> building} (spec §6.3). */
public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * The list endpoint's single query: scoped rooms, optional room and content filters. Readable
     * room ids are passed in so private rooms never leak into a search across rooms.
     */
    @Query(value = """
                   select m from Message m join fetch m.resident res join fetch res.user
                   where m.room.id in :roomIds
                     and (:roomId is null or m.room.id = :roomId)
                     and (:search is null or lower(m.content) like lower(concat('%', :search, '%')))
                   """,
           countQuery = """
                   select count(m) from Message m
                   where m.room.id in :roomIds
                     and (:roomId is null or m.room.id = :roomId)
                     and (:search is null or lower(m.content) like lower(concat('%', :search, '%')))
                   """)
    Page<Message> search(@Param("roomIds") Collection<Long> roomIds, @Param("roomId") Long roomId,
                         @Param("search") String search, Pageable pageable);

    Optional<Message> findByIdAndRoomIdIn(Long id, Collection<Long> roomIds);

    long countByRoomIdIn(Collection<Long> roomIds);

    Optional<Message> findFirstByRoomIdInOrderBySentAtDescIdDesc(Collection<Long> roomIds);

    /** The dashboard's message window: newest N of one room (reversed to oldest→newest by the caller). */
    @Query("""
           select m from Message m join fetch m.resident res join fetch res.user
           where m.room.id = :roomId
           order by m.sentAt desc, m.id desc
           """)
    List<Message> findLatestInRoom(@Param("roomId") Long roomId, Pageable pageable);

    boolean existsByRoomIdAndContent(Long roomId, String content);
}
