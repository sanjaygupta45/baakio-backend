package com.baakio.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class AccountIdentityRepository {
    private final JdbcTemplate jdbc;

    public AccountIdentityRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<String> accountHolderId(String provider, String subject) {
        List<String> rows = jdbc.query("""
                SELECT account_holder_id FROM account_identity WHERE provider = ? AND subject = ?
                """, (rs, row) -> rs.getString(1), provider, subject);
        return rows.stream().findFirst();
    }

    public void add(String provider, String subject, String accountHolderId) {
        jdbc.update("""
                INSERT INTO account_identity (provider, subject, account_holder_id) VALUES (?, ?, ?)
                """, provider, subject, accountHolderId);
    }
}
