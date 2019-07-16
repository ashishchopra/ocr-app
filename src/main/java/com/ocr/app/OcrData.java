package com.ocr.app;

import com.google.cloud.vision.v1.BoundingPoly;

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


//mvn -q clean compile exec:java  -Dexec.args="/home/zadmin/workspace/ocr-app/src/sa^Clebills/itemized-doctor-bill-template-medical-example-ideal-dr-office-invoice-billing-statement-com-images.jpg"
