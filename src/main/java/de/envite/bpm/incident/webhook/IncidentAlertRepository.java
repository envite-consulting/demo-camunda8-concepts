package de.envite.bpm.incident.webhook;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentAlertRepository extends JpaRepository<IncidentAlert, Long> {}
