package com.github.diudiuseven.dashboardbuilderenginejava.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Deep-copies domain objects via JSON round-trip, guaranteeing isolation
 * between stored state and objects handed to callers.
 */
@Component
public class JsonDeepCopier {

    private final ObjectMapper mapper;

    public JsonDeepCopier() {
        this.mapper = new ObjectMapper();
        this.mapper.findAndRegisterModules();
    }

    public <T> T copy(T source, Class<T> type) {
        if (source == null) {
            return null;
        }
        try {
            byte[] json = mapper.writeValueAsBytes(source);
            return mapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deep-copy " + type.getSimpleName(), e);
        }
    }

    public <T> T copy(T source, TypeReference<T> type) {
        if (source == null) {
            return null;
        }
        try {
            byte[] json = mapper.writeValueAsBytes(source);
            return mapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deep-copy " + type.getType(), e);
        }
    }
}
