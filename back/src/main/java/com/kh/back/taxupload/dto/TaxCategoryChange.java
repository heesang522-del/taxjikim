package com.kh.back.taxupload.dto;

public record TaxCategoryChange(Long transactionId, String category) {

    public record Feedback(Long transactionId, String transactionType, String description, String category) {
    }
}
