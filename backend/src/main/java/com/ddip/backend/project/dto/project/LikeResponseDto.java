package com.ddip.backend.project.dto.project;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LikeResponseDto {
    private boolean liked;
    private long likeCount;
}
