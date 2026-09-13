package com.balbalad.monopoly.feedback;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * تخزين بسيط للتقييمات بملف محلي (data/ratings.jsonl)، سطر JSON
 * واحد لكل تقييم. كافي لـ V1 بدون الحاجة لقاعدة بيانات كاملة.
 * ملاحظة: إشعار الإيميل المذكور بالوثيقة الأصلية مش مبني هون -
 * بيحتاج إعداد خدمة بريد فعلية (SMTP) خارج نطاق هاد المشروع.
 */
@Service
public class RatingService {

    private static final Path FILE = Path.of("data", "ratings.jsonl");
    private final ObjectMapper mapper = new ObjectMapper();

    public synchronized void save(RatingRequest req) {
        try {
            Files.createDirectories(FILE.getParent());
            Rating rating = new Rating(req.roomCode(), req.stars(), req.type(), req.text(), Instant.now().toString());
            String line = mapper.writeValueAsString(rating) + System.lineSeparator();
            Files.writeString(FILE, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public synchronized List<Rating> loadAll() {
        if (!Files.exists(FILE)) return List.of();
        try {
            List<String> lines = Files.readAllLines(FILE, StandardCharsets.UTF_8);
            List<Rating> ratings = new ArrayList<>();
            for (String line : lines) {
                if (line.isBlank()) continue;
                ratings.add(mapper.readValue(line, Rating.class));
            }
            Collections.reverse(ratings); // الأحدث أول
            return ratings;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
