package com.interview.common.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class EventJsonMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private EventJsonMapper() {}

    public static String toJson(DomainEvent event) {
        try {
            return MAPPER.writeValueAsString(event);
        } catch (Exception e) {
            throw new EventSerializationException("Failed to serialize event: " + event.getEventId(), e);
        }
    }

    public static <T extends DomainEvent> T fromJson(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new EventSerializationException("Failed to deserialize event", e);
        }
    }

    public static ObjectMapper getMapper() {
        return MAPPER;
    }
}
