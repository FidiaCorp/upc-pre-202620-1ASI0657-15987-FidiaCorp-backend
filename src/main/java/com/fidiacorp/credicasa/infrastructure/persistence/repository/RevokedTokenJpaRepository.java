package com.fidiacorp.credicasa.infrastructure.persistence.repository;
import com.fidiacorp.credicasa.infrastructure.persistence.entity.RevokedTokenJpaEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
public interface RevokedTokenJpaRepository extends JpaRepository<RevokedTokenJpaEntity,String> {
    boolean existsByTokenHashAndExpiresAtAfter(String hash,Instant now);
    @Modifying
    @Query("delete from RevokedTokenJpaEntity r where r.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);
}
