package com.example.pogun.config.startup;

import com.example.pogun.service.community.CommunityService;
import com.example.pogun.service.missingpet.MissingPetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class StartupWarmupRunner implements ApplicationRunner {

    private final StartupWarmupState startupWarmupState;
    private final JdbcTemplate jdbcTemplate;
    private final CommunityService communityService;
    private final MissingPetService missingPetService;

    @Value("${app.startup-warmup.enabled:true}")
    private boolean warmupEnabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!warmupEnabled) {
            startupWarmupState.markReady();
            log.info("Startup warmup skipped (app.startup-warmup.enabled=false)");
            return;
        }

        long start = System.currentTimeMillis();
        try {
            jdbcTemplate.queryForObject("select 1", Integer.class);
            CompletableFuture<Void> communityWarmup = CompletableFuture.runAsync(
                    () -> communityService.getPostList("LATEST", null, null, null, 0, 1)
            );
            CompletableFuture<Void> missingPetWarmup = CompletableFuture.runAsync(
                    () -> missingPetService.getMissingPetList(null, null, null, null, null, null, false, "createdAt,desc", 0, 1)
            );
            CompletableFuture.allOf(communityWarmup, missingPetWarmup).join();
        } catch (Exception e) {
            log.warn("Startup warmup encountered error; traffic will still open", e);
        } finally {
            startupWarmupState.markReady();
            log.info("Startup warmup completed in {} ms", System.currentTimeMillis() - start);
        }
    }
}
