package com.example.materialpull.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class ReplenishmentTaskRepositoryTest {
    @Autowired
    private ReplenishmentTaskRepository repository;

    @Test
    void scopedDerivedQueriesAreParsedWhenRepositoryStarts() {
        assertNotNull(repository);
    }
}
