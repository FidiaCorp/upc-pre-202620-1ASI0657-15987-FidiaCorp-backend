package com.fidiacorp.credicasa.infrastructure.persistence.entity;
import jakarta.persistence.*;
import java.util.UUID;
@Entity
@Table(name="accounts", uniqueConstraints=@UniqueConstraint(name="uk_accounts_email", columnNames="email"))
public class AccountJpaEntity {
    @Id public UUID id;
    @Column(nullable=false, length=100) public String firstName;
    @Column(nullable=false, length=100) public String lastName;
    @Column(nullable=false, length=254) public String email;
    @Column(nullable=false, length=100) public String passwordHash;
    @Column(nullable=false, length=100) public String roles;
    @Version public Long version;
    public AccountJpaEntity() {}
}
