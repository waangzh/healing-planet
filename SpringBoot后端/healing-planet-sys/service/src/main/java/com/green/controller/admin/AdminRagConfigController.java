package com.green.controller.admin;

import com.green.common.api.Result;
import com.green.service.RagAdminClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/rag-config")
public class AdminRagConfigController {
    private final RagAdminClient ragAdminClient;

    public AdminRagConfigController(RagAdminClient ragAdminClient) {
        this.ragAdminClient = ragAdminClient;
    }

    @GetMapping("/current")
    public Result<Object> current() {
        return Result.success(ragAdminClient.current());
    }

    @GetMapping("/revisions")
    public Result<Object> revisions() {
        return Result.success(ragAdminClient.revisions());
    }

    @GetMapping("/revisions/{revision}")
    public Result<Object> revision(@PathVariable long revision) {
        return Result.success(ragAdminClient.revision(revision));
    }

    @PostMapping("/drafts")
    public Result<Object> saveDraft(@RequestBody Map<String, Object> request) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>(request);
        payload.put("operator", currentOperator());
        return Result.success(ragAdminClient.saveDraft(payload));
    }

    @PostMapping("/drafts/{revision}/validate")
    public Result<Object> validate(@PathVariable long revision) {
        return Result.success(ragAdminClient.validate(revision, currentOperator()));
    }

    @PostMapping("/drafts/{revision}/publish")
    public Result<Object> publish(@PathVariable long revision) {
        return Result.success(ragAdminClient.publish(revision, currentOperator()));
    }

    @PostMapping("/revisions/{revision}/rollback")
    public Result<Object> rollback(@PathVariable long revision) {
        return Result.success(ragAdminClient.rollback(revision, currentOperator()));
    }

    private String currentOperator() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "unknown-admin" : authentication.getName();
    }
}
