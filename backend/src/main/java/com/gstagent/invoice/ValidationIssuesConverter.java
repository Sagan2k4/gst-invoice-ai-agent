package com.gstagent.invoice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gstagent.validation.ValidationIssue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

@Converter
public class ValidationIssuesConverter implements AttributeConverter<List<ValidationIssue>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<ValidationIssue>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<ValidationIssue> issues) {
        try {
            return MAPPER.writeValueAsString(issues == null ? List.of() : issues);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public List<ValidationIssue> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(MAPPER.readValue(json, TYPE));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
