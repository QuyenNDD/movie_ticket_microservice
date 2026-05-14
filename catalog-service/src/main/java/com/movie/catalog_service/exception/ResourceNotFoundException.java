package com.movie.catalog_service.exception;

public class ResourceNotFoundException extends RuntimeException{
    String resourceName;
    String filed;
    String fieldName;
    Long fieldId;

    public ResourceNotFoundException(String message){
        super(message);
    }

    public ResourceNotFoundException(String resourceName, String filed, String fieldName) {
        super(String.format("%s not found with %s: %s", resourceName, filed, fieldName));
        this.resourceName = resourceName;
        this.filed = filed;
        this.fieldName = fieldName;
    }

    public ResourceNotFoundException(String resourceName, String filed, Long fieldId) {
        super(String.format("%s not found with %s: %s", resourceName, filed, fieldId));
        this.resourceName = resourceName;
        this.filed = filed;
        this.fieldId = fieldId;
    }
}
