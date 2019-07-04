package com.ocr.app;

import com.google.api.services.vision.v1.model.BoundingPoly;
import lombok.Data;

@Data
public class MpWord {
    private String description;
    private BoundingPoly bound;

}
