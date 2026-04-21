package com.authentication.repo;


import com.authentication.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmailVerificationToken(String token);

    Optional<User> findByPasswordResetToken(String token);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    // Direct JPQL update — bypasses Hibernate entity tracking entirely.
    // Guarantees the new hashed password is flushed to DB in the same transaction.
    @Modifying
    @Query("UPDATE User u SET u.password = :password, " +
           "u.passwordResetToken = NULL, " +
           "u.passwordResetExpires = NULL " +
           "WHERE u.id = :userId")
    int updatePasswordAndClearResetToken(@Param("userId") Long userId,
                                         @Param("password") String password);

    // Direct JPQL update for email verification — same reason
    @Modifying
    @Query("UPDATE User u SET u.emailVerified = TRUE, " +
           "u.emailVerificationToken = NULL " +
           "WHERE u.id = :userId")
    int markEmailVerified(@Param("userId") Long userId);

    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.name = 'ROLE_STUDENT'")
    List<User> findAllStudents();

    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.name = 'ROLE_RECRUITER'")
    List<User> findAllRecruiters();
}