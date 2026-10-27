package com.fidiacorp.credicasa.infrastructure.persistence.repository;
import com.fidiacorp.credicasa.infrastructure.persistence.entity.AccountJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
public interface AccountJpaRepository extends JpaRepository<AccountJpaEntity, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from AccountJpaEntity a where a.id = :id")
    Optional<AccountJpaEntity> findForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
    Optional<AccountJpaEntity> findByEmail(String email);
}
