package com.lulu.luluaiagent.controller;

import com.lulu.luluaiagent.auth.AuthSupport;
import com.lulu.luluaiagent.model.ModelRouter;
import com.lulu.luluaiagent.model.runtime.ModelDescriptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/ai/system/models")
public class ModelController {

    private final ModelRouter modelRouter;

    public ModelController(ModelRouter modelRouter) {
        this.modelRouter = modelRouter;
    }

    @GetMapping
    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deepSeekAvailable", modelRouter.deepSeekAvailable());
        result.put("coachPrimary", modelRouter.coachPrimaryModelName());
        result.put("agentPrimary", modelRouter.agentPrimaryModelName());
        result.put("fastModel", modelRouter.fastModelName());
        result.put("memoryModel", modelRouter.memoryModelName());
        result.put("routes", modelRouter.routes());
        result.put("preferredRoutes", modelRouter.preferredRoutes());
        result.put("models", modelRouter.catalog());
        return result;
    }

    @PostMapping("/routes/{route}")
    public Map<String, Object> select(
            @PathVariable String route,
            @RequestBody RouteSelection request,
            HttpServletRequest httpRequest) {
        AuthSupport.requireAdmin(httpRequest);
        try {
            ModelDescriptor selected = modelRouter.select(route, request.candidate());
            return Map.of(
                    "route", route,
                    "selected", selected,
                    "routes", modelRouter.routes());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @DeleteMapping("/routes/{route}")
    public Map<String, Object> clear(
            @PathVariable String route,
            HttpServletRequest httpRequest) {
        AuthSupport.requireAdmin(httpRequest);
        try {
            modelRouter.clearSelection(route);
            return Map.of(
                    "route", route,
                    "routes", modelRouter.routes());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    public record RouteSelection(String candidate) {}
}
