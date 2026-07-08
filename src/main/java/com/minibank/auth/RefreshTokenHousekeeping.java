package com.minibank.auth;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Purges expired and long-revoked refresh tokens so the table stays small. */
@Component
public class RefreshTokenHousekeeping {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenHousekeeping.class);

    private final RefreshTokenRepository refreshTokens;
    private final Duration retention;

    public RefreshTokenHousekeeping(RefreshTokenRepository refreshTokens,
                                    @Value("${minibank.housekeeping.retention:30d}") Duration retention) {
        this.refreshTokens = refreshTokens;
        this.retention = retention;
    }

    @Scheduled(fixedDelayString = "${minibank.housekeeping.interval:6h}")
    @Transactional
    public void purgeStaleTokens() {
        int deleted = refreshTokens.deleteStale(Instant.now().minus(retention));
        if (deleted > 0) {
            log.info("Purged {} stale refresh tokens", deleted);
        }
    }
}
