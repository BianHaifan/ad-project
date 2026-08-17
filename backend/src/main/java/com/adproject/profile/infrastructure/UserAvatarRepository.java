package com.adproject.profile.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAvatarRepository extends JpaRepository<UserAvatarEntity, String> {
}
