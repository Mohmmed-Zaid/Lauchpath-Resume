package com.launchpath.resume_service.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@EnableMongoAuditing
@EnableMongoRepositories(basePackages = "launchpath.resumeservice.repository")
@RequiredArgsConstructor
public class MongoConfig {

    private final MongoClient mongoClient;

    @PostConstruct
    public void createIndexes() {
        MongoDatabase db = mongoClient.getDatabase("launchpath_resumes");
        log.info("Creating MongoDB indexes...");

        // ── resumes collection indexes ────────────────────────

        MongoCollection<Document> resumes = db.getCollection("resumes");

        // userId index — most frequent query filter
        resumes.createIndex(
                Indexes.ascending("userId"),
                new IndexOptions().name("idx_resumes_userId")
        );

        // userId + status compound — dashboard query
        resumes.createIndex(
                Indexes.compoundIndex(
                        Indexes.ascending("userId"),
                        Indexes.ascending("status")
                ),
                new IndexOptions().name("idx_resumes_userId_status")
        );

        // templateId — find resumes using a template
        resumes.createIndex(
                Indexes.ascending("templateId"),
                new IndexOptions().name("idx_resumes_templateId")
        );

        // isLocked + lockedBy — WebSocket lock queries
        resumes.createIndex(
                Indexes.compoundIndex(
                        Indexes.ascending("isLocked"),
                        Indexes.ascending("lockedBy")
                ),
                new IndexOptions().name("idx_resumes_lock")
        );

        // ── templates collection indexes ──────────────────────

        MongoCollection<Document> templates = db.getCollection("templates");

        // category + isActive — template gallery filter
        templates.createIndex(
                Indexes.compoundIndex(
                        Indexes.ascending("category"),
                        Indexes.ascending("isActive")
                ),
                new IndexOptions().name("idx_templates_category_active")
        );

        // isPremium + isActive — free tier filter
        templates.createIndex(
                Indexes.compoundIndex(
                        Indexes.ascending("isPremium"),
                        Indexes.ascending("isActive")
                ),
                new IndexOptions().name("idx_templates_premium_active")
        );

        // usageCount — popularity sort
        templates.createIndex(
                Indexes.descending("usageCount"),
                new IndexOptions().name("idx_templates_usageCount")
        );

        // ── parsed_resumes collection indexes ─────────────────

        MongoCollection<Document> parsed = db.getCollection("parsed_resumes");

        // userId — one per user check
        parsed.createIndex(
                Indexes.ascending("userId"),
                new IndexOptions()
                        .name("idx_parsed_userId")
                        .unique(true) // one parsed resume per user at a time
        );

        // TTL index — auto-deletes documents after 24 hours
        // MongoDB daemon checks every 60 seconds and removes expired docs
        parsed.createIndex(
                Indexes.ascending("expiresAt"),
                new IndexOptions()
                        .name("idx_parsed_ttl")
                        .expireAfter(0L, TimeUnit.SECONDS)
        );

        log.info("MongoDB indexes created successfully");
    }
}