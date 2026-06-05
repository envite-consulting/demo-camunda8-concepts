package de.envite.bpm.incidents;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentAlertRepository extends JpaRepository<IncidentAlert, Long> {}
