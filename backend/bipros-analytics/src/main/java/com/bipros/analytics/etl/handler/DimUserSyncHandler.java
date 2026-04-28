package com.bipros.analytics.etl.handler;

import com.bipros.analytics.etl.support.BaseJpaToClickHouseSyncHandler;
import com.bipros.analytics.etl.support.HandlerSupport;
import com.bipros.security.domain.model.User;
import com.bipros.security.domain.repository.UserRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class DimUserSyncHandler extends BaseJpaToClickHouseSyncHandler<User> {

    private static final String SQL =
            "INSERT INTO dim_user (id, username, full_name, first_name, last_name, email, mobile, " +
            "employee_code, designation, primary_icpms_role, department, organisation_id, " +
            "presence_status, joining_date, contract_end_date, enabled, account_locked, " +
            "created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final UserRepository repo;

    public DimUserSyncHandler(@Qualifier("clickhouseJdbcTemplate") JdbcTemplate clickhouse,
                              UserRepository repo) {
        super(clickhouse);
        this.repo = repo;
    }

    @Override public String tableName() { return "dim_user"; }
    @Override protected String insertSql() { return SQL; }

    @Override
    protected Page<User> fetchPage(Instant since, Pageable pageable) {
        return repo.findByUpdatedAtAfter(since, pageable);
    }

    @Override
    protected Object[] mapRow(User u) {
        String fullName = joinName(u.getFirstName(), u.getLastName());
        return new Object[]{
                HandlerSupport.uuidOrEmpty(u.getId()),
                u.getUsername(),
                fullName,
                u.getFirstName(),
                u.getLastName(),
                u.getEmail(),
                u.getMobile(),
                u.getEmployeeCode(),
                u.getDesignation(),
                u.getPrimaryIcpmsRole(),
                HandlerSupport.enumName(u.getDepartment()),
                HandlerSupport.uuidOrNull(u.getOrganisationId()),
                HandlerSupport.enumName(u.getPresenceStatus()),
                HandlerSupport.toSqlDate(u.getJoiningDate()),
                HandlerSupport.toSqlDate(u.getContractEndDate()),
                u.isEnabled() ? 1 : 0,
                u.isAccountLocked() ? 1 : 0,
                HandlerSupport.toSqlTs(u.getCreatedAt()),
                HandlerSupport.toSqlTs(u.getUpdatedAt())
        };
    }

    private static String joinName(String first, String last) {
        if (first == null && last == null) return null;
        if (first == null) return last;
        if (last == null) return first;
        return (first + " " + last).trim();
    }
}
