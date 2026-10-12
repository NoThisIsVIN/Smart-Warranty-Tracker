package org.example.repo;

import org.example.model.Warranty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface WarrantyRepository extends JpaRepository<Warranty, Long> {

    /** Updates only the image column, so a background image fetch can't overwrite concurrent edits. */
    @Modifying
    @Transactional
    @Query("update Warranty w set w.imageUrl = :url where w.id = :id")
    int updateImageUrl(@Param("id") Long id, @Param("url") String url);

    @Modifying
    @Transactional
    @Query("update Warranty w set w.notified = :notified where w.id = :id")
    int updateNotified(@Param("id") Long id, @Param("notified") boolean notified);
}
