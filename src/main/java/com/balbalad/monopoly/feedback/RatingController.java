package com.balbalad.monopoly.feedback;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
public class RatingController {

    private final RatingService ratingService;

    @Value("${admin.key:change-me}")
    private String adminKey;

    public RatingController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    @PostMapping("/api/ratings")
    public void submit(@RequestBody RatingRequest req) {
        if (req.stars() < 1 || req.stars() > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "التقييم لازم يكون من 1 لـ 5");
        }
        ratingService.save(req);
    }

    /** لوحة الإدارة: محمية بمفتاح بسيط (application.properties: admin.key). */
    @GetMapping("/api/admin/ratings")
    public List<Rating> list(@RequestParam String key) {
        if (!adminKey.equals(key)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "مفتاح خاطئ");
        }
        return ratingService.loadAll();
    }
}
