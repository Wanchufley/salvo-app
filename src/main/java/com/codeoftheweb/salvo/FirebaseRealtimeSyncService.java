package com.codeoftheweb.salvo;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FirebaseRealtimeSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FirebaseRealtimeSyncService.class);
    private static final String FIREBASE_APP_NAME = "salvo-live-sync";

    private final boolean enabled;
    private final String databaseUrl;
    private final DatabaseReference liveGamesReference;

    public FirebaseRealtimeSyncService(
        @Value("${firebase.project-id:}") String projectId,
        @Value("${firebase.database-url:}") String databaseUrl,
        @Value("${firebase.credentials-base64:}") String credentialsBase64,
        @Value("${firebase.credentials-json:}") String credentialsJson
    ) {
        this.databaseUrl = normalizeDatabaseUrl(databaseUrl);
        boolean syncEnabled = false;
        DatabaseReference reference = null;
        String resolvedCredentialsJson = resolveCredentialsJson(credentialsBase64, credentialsJson);

        if (isBlank(projectId) || isBlank(this.databaseUrl) || isBlank(resolvedCredentialsJson)) {
            LOGGER.info("Firebase live sync is disabled because credentials or database settings are missing.");
        } else {
            try {
                FirebaseApp app = findOrCreateFirebaseApp(projectId, this.databaseUrl, resolvedCredentialsJson);
                reference = FirebaseDatabase.getInstance(app).getReference("live-games");
                syncEnabled = true;
                LOGGER.info("Firebase live sync is enabled.");
            } catch (Exception exception) {
                LOGGER.warn("Firebase live sync could not be initialized. Live updates will be disabled.", exception);
            }
        }

        this.enabled = syncEnabled;
        this.liveGamesReference = reference;
    }

    public void publishGameChange(Game game, String eventType, Long changedByGamePlayerId) {
        if (!enabled || liveGamesReference == null || game == null) {
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("gameId", game.getId());
        payload.put("updatedAt", System.currentTimeMillis());
        payload.put("eventType", eventType);
        payload.put("changedByGamePlayerId", changedByGamePlayerId);
        payload.put("gamePlayerCount", game.getGamePlayers().size());
        payload.put("isFinished", !game.getScores().isEmpty());

        try {
            liveGamesReference.child(String.valueOf(game.getId()))
                .setValueAsync(payload)
                .get(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            LOGGER.warn("Failed to publish Firebase live update for game {}", game.getId(), exception);
        }
    }

    public String getGameStreamUrl(long gameId) {
        if (!enabled || isBlank(databaseUrl)) {
            return null;
        }
        return databaseUrl + "/live-games/" + gameId + ".json";
    }

    private String resolveCredentialsJson(String credentialsBase64, String credentialsJson) {
        if (!isBlank(credentialsBase64)) {
            try {
                byte[] decodedBytes = Base64.getDecoder().decode(credentialsBase64.trim());
                return new String(decodedBytes, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException exception) {
                LOGGER.warn("Firebase base64 credentials could not be decoded.");
                return null;
            }
        }
        return credentialsJson;
    }

    private FirebaseApp findOrCreateFirebaseApp(String projectId, String databaseUrl, String credentialsJson) throws Exception {
        for (FirebaseApp app : FirebaseApp.getApps()) {
            if (FIREBASE_APP_NAME.equals(app.getName())) {
                return app;
            }
        }

        GoogleCredentials credentials = GoogleCredentials.fromStream(
            new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8))
        );
        FirebaseOptions options = FirebaseOptions.builder()
            .setCredentials(credentials)
            .setProjectId(projectId)
            .setDatabaseUrl(databaseUrl)
            .build();
        return FirebaseApp.initializeApp(options, FIREBASE_APP_NAME);
    }

    private String normalizeDatabaseUrl(String value) {
        if (isBlank(value)) {
            return value;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
