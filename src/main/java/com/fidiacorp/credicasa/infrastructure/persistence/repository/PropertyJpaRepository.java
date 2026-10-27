package com.fidiacorp.credicasa.infrastructure.persistence.repository;
import com.fidiacorp.credicasa.infrastructure.persistence.entity.PropertyJpaEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;
public interface PropertyJpaRepository extends JpaRepository<PropertyJpaEntity,UUID>,JpaSpecificationExecutor<PropertyJpaEntity> {
    Optional<PropertyJpaEntity> findByIdAndDeletedAtIsNull(UUID id);
    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("update PropertyJpaEntity p set p.deletedAt=:at where p.id=:id and p.ownerId=:ownerId and p.deletedAt is null")
    int softDelete(@Param("id") UUID id,@Param("ownerId") UUID ownerId,@Param("at") Instant at);
}
