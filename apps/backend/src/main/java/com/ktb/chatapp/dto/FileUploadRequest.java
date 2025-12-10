package com.ktb.chatapp.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadRequest {

    @NotBlank
    private String originalFilename;

    @NotBlank
    private String contentType;

    @NotNull
    @Min(1)
    private Long size;
}
