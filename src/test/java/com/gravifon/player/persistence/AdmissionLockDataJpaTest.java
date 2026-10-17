package com.gravifon.player.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.gravifon.player.registry.repository.TrackRepository;
import com.gravifon.player.session.repository.UserRepository;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({UserRepository.class, TrackRepository.class})
class AdmissionLockDataJpaTest {
    @Autowired
    private JpaAdmissionLockStore admissionLocks;
    @Autowired
    private UserRepository users;
    @Autowired
    private TrackRepository tracks;
    @Autowired
    private DataSource dataSource;

    @Test
    void migrationSeedsEveryAdmissionScope() {
        assertThat(admissionLocks.findAll()).hasSize(AdmissionScope.values().length);
        for (AdmissionScope scope : AdmissionScope.values()) {
            assertThat(admissionLocks.findLockedByScope(scope)).as(scope.name()).isPresent();
        }
    }

    @Test
    void creationLocksAreAcquirableInsideTransaction() {
        users.lockCreation();
        tracks.lockCreation();
    }

    @Test
    void datasourceDefaultIsolationIsReadCommitted() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getTransactionIsolation()).isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
        }
    }
}
