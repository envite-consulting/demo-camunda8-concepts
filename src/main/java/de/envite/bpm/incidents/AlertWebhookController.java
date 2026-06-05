package de.envite.bpm.incidents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/alerts")
@RequiredArgsConstructor
@Slf4j
public class AlertWebhookController {

  private static final String SIGNATURE_HEADER = "X-Camunda-Signature-256";
  private static final String HMAC_ALGORITHM = "HmacSHA256";

  private final IncidentAlertRepository repository;

  @Value("${app.alerts.webhook.hmac-secret:}")
  private String hmacSecret;

  private final ObjectMapper objectMapper =
      JsonMapper.builder().addModule(new JavaTimeModule()).build();

  @PostMapping(path = "/incidents", consumes = "application/json")
  public ResponseEntity<Void> receive(
      @RequestHeader(name = SIGNATURE_HEADER, required = false) String signature,
      @RequestBody byte[] rawBody) {

    if (!hmacSecret.isBlank() && !signatureMatches(rawBody, signature)) {
      log.warn("Rejecting alert webhook: HMAC signature missing or invalid");
      return ResponseEntity.status(401).build();
    }

    AlertWebhookPayload payload;
    try {
      payload = objectMapper.readValue(rawBody, AlertWebhookPayload.class);
    } catch (Exception e) {
      log.warn("Rejecting alert webhook: failed to parse JSON body: {}", e.getMessage());
      return ResponseEntity.badRequest().build();
    }

    List<AlertWebhookPayload.Alert> alerts =
        payload.alerts() == null ? List.of() : payload.alerts();
    Instant receivedAt = Instant.now();

    alerts.stream()
        .map(alert -> IncidentAlert.build(alert, payload, receivedAt))
        .map(repository::save)
        .forEach(saved -> log.info("Persisted incident alert: {}", saved));

    return ResponseEntity.noContent().build();
  }

  private boolean signatureMatches(byte[] rawBody, String providedSignatureHex) {
    if (providedSignatureHex == null || providedSignatureHex.isBlank()) {
      return false;
    }
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
      byte[] expected = mac.doFinal(rawBody);
      byte[] provided;
      try {
        provided = HexFormat.of().parseHex(providedSignatureHex.trim());
      } catch (IllegalArgumentException e) {
        return false;
      }
      return MessageDigest.isEqual(expected, provided);
    } catch (Exception e) {
      log.error("HMAC verification failed unexpectedly", e);
      return false;
    }
  }
}
