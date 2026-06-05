package de.envite.bpm.taskstatus;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTaskStatusEventRepository extends JpaRepository<UserTaskStatusEvent, Long> {}
