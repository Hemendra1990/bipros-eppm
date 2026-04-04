package com.bipros.security.domain.model;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(
        name = "user_roles",
        schema = "public",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "role_id"})
)
@Getter
@Setter
@NoArgsConstructor
public class UserRole extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @ManyToOne
    @JoinColumn(name = "user_id", insertable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_user_roles_user"))
    private User user;

    @ManyToOne
    @JoinColumn(name = "role_id", insertable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_user_roles_role"))
    private Role role;

    public UserRole(UUID userId, UUID roleId) {
        this.userId = userId;
        this.roleId = roleId;
    }
}
