package com.ocr.app;

import com.google.cloud.vision.v1.BoundingPoly;
import lombok.Data;

@Data
public class MpWord {
    private String description;
    private BoundingPoly bound;

    public MpWord(String description, BoundingPoly bound){
        this.description = description;
        this.bound = bound;
    }
    public BoundingPoly getBound() {
        return this.bound;
    }

    public String getDescription() {
        return this.description;
    }
}
