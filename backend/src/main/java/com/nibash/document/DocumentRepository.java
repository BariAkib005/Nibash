package com.nibash.document;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tenant path: {@code Document -> building} (spec §6.3). */
public interface DocumentRepository extends JpaRepository<Document, Long> {

    @Query(value = """
                   select d from Document d join fetch d.uploadedBy
                   where d.building.id in :buildingIds
                     and (:active is null or d.active = :active)
                     and (:search is null or lower(d.title) like lower(concat('%', :search, '%')))
                   """,
           countQuery = """
                   select count(d) from Document d
                   where d.building.id in :buildingIds
                     and (:active is null or d.active = :active)
                     and (:search is null or lower(d.title) like lower(concat('%', :search, '%')))
                   """)
    Page<Document> search(@Param("buildingIds") List<Long> buildingIds, @Param("active") Boolean active,
                          @Param("search") String search, Pageable pageable);

    Optional<Document> findByIdAndBuildingIdIn(Long id, List<Long> buildingIds);

    /** The next version in the chain, if one has been uploaded. */
    Optional<Document> findFirstByParentIdOrderByVersionDesc(Long parentId);

    boolean existsByParentId(Long parentId);

    Optional<Document> findFirstByBuildingIdAndTitle(Long buildingId, String title);

    /** Dashboard {@code documents}: newest active documents. */
    @Query("""
           select d from Document d join fetch d.uploadedBy
           where d.building.id = :buildingId and d.active = true
           order by d.uploadedAt desc, d.id desc
           """)
    List<Document> findNewestActive(@Param("buildingId") Long buildingId, Pageable pageable);
}
