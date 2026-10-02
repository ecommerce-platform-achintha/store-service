package com.achintha.storeservice.store;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreRepository extends JpaRepository<Store, UUID> {

    Optional<Store> findByPublicId(String publicId);

    boolean existsByPublicId(String publicId);

    boolean existsBySlug(String slug);

    @Query("select count(s) > 0 from Store s where lower(s.name) = lower(:name) and s.id <> :excludeId")
    boolean nameTakenByOther(@Param("name") String name, @Param("excludeId") UUID excludeId);

    @Query("select s from Store s where s.publicId = :publicId and s.published = true and s.acceptingOrders = true")
    Optional<Store> findVisibleByPublicId(@Param("publicId") String publicId);

    @Query("select s from Store s where s.published = true and s.acceptingOrders = true")
    Page<Store> findVisible(Pageable pageable);

    /** {@code pattern} comes from {@code Pagination.likeContains} (wildcards escaped). */
    @Query("""
            select s from Store s
            where s.published = true and s.acceptingOrders = true and lower(s.name) like :pattern escape '\\'
            """)
    Page<Store> searchVisible(@Param("pattern") String pattern, Pageable pageable);

    @Query("select s from Store s where lower(s.name) like :pattern escape '\\'")
    Page<Store> searchAll(@Param("pattern") String pattern, Pageable pageable);

    /** Rating aggregates from the visible reviews; a bulk update, so it never conflicts with profile edits. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update stores set
                rating_avg = coalesce((select round(avg(r.rating)::numeric, 2) from store_reviews r
                                       where r.store_id = :storeId and r.hidden = false), 0),
                rating_count = (select count(*) from store_reviews r where r.store_id = :storeId and r.hidden = false)
            where id = :storeId
            """, nativeQuery = true)
    int refreshRating(@Param("storeId") UUID storeId);
}
