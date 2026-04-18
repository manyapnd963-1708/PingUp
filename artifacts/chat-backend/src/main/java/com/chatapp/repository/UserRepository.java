package com.chatapp.repository;

import com.chatapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for User entity operations.
 *
 * Spring Data JPA auto-generates SQL from method names.
 * All queries are executed via the PostgreSQL JDBC driver.
 *
 * Scaling Note:
 * - Add @Cacheable (Redis) on findByUsername for JWT validation path —
 *   this is called on EVERY authenticated request.
 * - For user search at scale: use Elasticsearch instead of LIKE queries.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Used during JWT validation to load the authenticated user.
     * HOT PATH — cache this with Redis for high-traffic deployments.
     */
    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /**
     * Search users by username prefix (for contact search).
     * At scale: replace with Elasticsearch full-text search.
     */
    @Query("SELECT u FROM User u WHERE LOWER(u.username) LIKE LOWER(CONCAT(:prefix, '%'))")
    List<User> searchByUsernamePrefix(String prefix);
}
