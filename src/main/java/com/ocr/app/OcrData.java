package com.ocr.app;

import com.google.api.services.vision.v1.model.BoundingPoly;

import java.util.List;

public class OcrData {

    private List<MpWord> boundsTest;
    private List<BoundingPoly> boundsBlock;

    public OcrData(List<MpWord> boundsTest, List<BoundingPoly> boundsBlock) {
        this.boundsTest = boundsTest;
        this.boundsBlock = boundsBlock;
    }


    public List<MpWord> getBoundsTest() {
        return boundsTest;
    }

    public void setBoundsTest(List<MpWord> boundsTest) {
        this.boundsTest = boundsTest;
    }

    public List<BoundingPoly> getBoundsBlock() {
        return boundsBlock;
    }

    public void setBoundsBlock(List<BoundingPoly> boundsBlock) {
        this.boundsBlock = boundsBlock;
    }
}
