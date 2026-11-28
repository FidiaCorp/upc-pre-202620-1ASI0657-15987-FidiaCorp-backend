package com.fidiacorp.credicasa.communication.infrastructure.persistence.repository;

import com.fidiacorp.credicasa.communication.infrastructure.persistence.entity.UserBlockJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserBlockJpaRepository extends JpaRepository<UserBlockJpaEntity, UUID> {

    boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    @Query("SELECT COUNT(b) > 0 FROM UserBlockJpaEntity b WHERE " +
            "(b.blockerId = :userA AND b.blockedId = :userB) OR " +
            "(b.blockerId = :userB AND b.blockedId = :userA)")
    boolean isBlockedBetween(@Param("userA") UUID userA, @Param("userB") UUID userB);

    void deleteByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    List<UserBlockJpaEntity> findByBlockerIdOrderByCreatedAtDesc(UUID blockerId);
}
