package com.clinica.escuta.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.clinica.escuta.model.User;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    String LIGHT_USER_PROJECTION = "SELECT new com.clinica.escuta.model.User(" +
            "u.id, u.username, u.email, u.status, u.accessLevel, u.professionId, u.FirstLogin, u.mustCompleteTour, " +
            "u.phone, u.cpf, u.address, u.cep, " +
            "CASE WHEN u.photo IS NOT NULL AND u.photo <> '' THEN 'has_photo' ELSE NULL END, " +
            "u.boardNumber) FROM User u ";

    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    List<User> findByStatusTrue();
    Optional<User> findByIdAndStatusTrue(Integer id);

    @Query(LIGHT_USER_PROJECTION)
    List<User> findAllLightweight();

    @Query(LIGHT_USER_PROJECTION + "WHERE u.id = :id")
    Optional<User> findByIdLightweight(@Param("id") Integer id);

    @Query("SELECT u.id FROM User u WHERE u.username = :username")
    Optional<Integer> findIdByUsername(@Param("username") String username);
}