package com.ktb.chatapp.dto;

import java.time.Instant;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresignedUrlResponse {

    private boolean success;

    private String url;

    private Instant expiresAt;

    private Map<String, String> headers;

    private String method;
    
    private String objectKey;
}
