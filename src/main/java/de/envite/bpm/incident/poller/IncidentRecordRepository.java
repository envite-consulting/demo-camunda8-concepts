package de.envite.bpm.incident.poller;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRecordRepository extends JpaRepository<IncidentRecord, Long> {}
