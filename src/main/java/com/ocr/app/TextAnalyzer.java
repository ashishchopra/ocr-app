package com.ocr.app;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.BoundingPoly;
import com.google.cloud.vision.v1.*;
import com.google.cloud.vision.v1.Vertex;
import com.google.cloud.vision.v1.BatchAnnotateImagesResponse;
import com.google.protobuf.ByteString;
import me.xdrop.fuzzywuzzy.FuzzySearch;
import me.xdrop.fuzzywuzzy.model.BoundExtractedResult;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

enum FeatureType{
    PAGE,BLOCK,PARA,WORD,SYMBOL,NONE;
}


public class TextAnalyzer {

    private OcrData detectDocumentText(String filePath, FeatureType featureType) throws Exception,
            IOException {
        List<AnnotateImageRequest> requests = new ArrayList<>();

        ByteString imgBytes = ByteString.readFrom(new FileInputStream(filePath));

        Image img = Image.newBuilder().setContent(imgBytes).build();
        Feature feat = Feature.newBuilder().setType(Feature.Type.DOCUMENT_TEXT_DETECTION).build();
        AnnotateImageRequest request =
                AnnotateImageRequest.newBuilder().addFeatures(feat).setImage(img).build();
        requests.add(request);

        List<BoundingPoly> boundsBlock = new ArrayList<BoundingPoly>();
        List<MpWord> boundsTest = new ArrayList<MpWord>();

        try (ImageAnnotatorClient client = ImageAnnotatorClient.create()) {
            BatchAnnotateImagesResponse response = client.batchAnnotateImages(requests);
            List<AnnotateImageResponse> responses = response.getResponsesList();
            client.close();

            for (AnnotateImageResponse res : responses) {

                // For full list of available annotations, see http://g.co/cloud/vision/docs
                TextAnnotation annotation = res.getFullTextAnnotation();
                for (Page page : annotation.getPagesList()) {
                    String pageText = "";
                    for (Block block : page.getBlocksList()) {
                        String blockText = "";
                        for (Paragraph para : block.getParagraphsList()) {
                            String paraText = "";
                            for (Word word : para.getWordsList()) {
                                String wordText = "";
                                for (Symbol symbol : word.getSymbolsList()) {
                                    wordText = wordText + symbol.getText();
                                    // out.format("Symbol text: %s (confidence: %f)\n", symbol.getText(),
                                    //     symbol.getConfidence());
                                }
                                System.out.format("Word text: %s (confidence: %f)\n\n", wordText, word.getConfidence());
                                paraText = String.format("%s %s", paraText, wordText);
                                if (featureType == FeatureType.NONE) {
                                    MpWord currWord = new MpWord(paraText, word.getBoundingBox());
                                    boundsTest.add(currWord);
                                }
                            }
                            // Output Example using Paragraph:
                            // out.println("\nParagraph: \n" + paraText);
                            // out.format("Paragraph Confidence: %f\n", para.getConfidence());
                            blockText = blockText + paraText;
                            if (featureType == FeatureType.BLOCK) boundsBlock.add(block.getBoundingBox());
                        }
                        pageText = pageText + blockText;
                    }
                }
                // out.println("\nComplete annotation:");
                // out.println(annotation.getText());
            }
        }
        OcrData ocrData = new OcrData(boundsTest, boundsBlock);

        return ocrData;
    }

    private float area(int x1, int y1, int x2, int y2, int x3, int y3) {
        return (float) Math.abs(((x1 * (y2 - y3) +
                x2 * (y3 - y1) +
                x3 * (y1 - y2)) / 2.0));
    }

    private boolean check(Vertex vertex, BoundingPoly bound) {
        int x = vertex.getX();
        int y = vertex.getY();
        int x1 = bound.getVerticesList().get(0).getX() - 2;
        int y1 = bound.getVerticesList().get(0).getY() - 2;
        int x2 = bound.getVerticesList().get(1).getX() + 2;
        int y2 = bound.getVerticesList().get(1).getY() - 2;
        int x3 = bound.getVerticesList().get(2).getX() + 2;
        int y3 = bound.getVerticesList().get(2).getY() + 2;
        int x4 = bound.getVerticesList().get(3).getX() - 2;
        int y4 = bound.getVerticesList().get(3).getY() + 2;

        float A = (area(x1, y1, x2, y2, x3, y3) +
                area(x1, y1, x4, y4, x3, y3));
        float A1 = area(x, y, x1, y1, x2, y2);
        float A2 = area(x, y, x2, y2, x3, y3);
        float A3 = area(x, y, x3, y3, x4, y4);
        float A4 = area(x, y, x1, y1, x4, y4);

        return (A == A1 + A2 + A3 + A4);
    }

    private boolean isWordInBlock(BoundingPoly wordBound, BoundingPoly blockBound) {
        if (check(wordBound.getVerticesList().get(0), blockBound) &&
                check(wordBound.getVerticesList().get(1), blockBound) &&
                check(wordBound.getVerticesList().get(2), blockBound) &&
                check(wordBound.getVerticesList().get(3), blockBound)) {
            return true;
        } else return false;
    }

    private BoundingPoly getContainerBound(BoundingPoly wordBound, List<BoundingPoly> containerList) {
        for (BoundingPoly container : containerList) {
            if (isWordInBlock(wordBound, container)) return container;
            return containerList.get(0);
        }
        return null;
    }

    private List<String> getAllWordsWithinContainer(BoundingPoly containerBound, List<MpWord> wordList) {
        ArrayList<String> wordsWithin = new ArrayList<String>();
        for (MpWord word : wordList) {
            if (isWordInBlock(word.getBound(), containerBound)) wordsWithin.add(word.getDescription());
        }
        return wordsWithin;
    }

    private List<BoundingPoly> getMatchedWord(String regex, List<MpWord> dictList) {
        List<BoundingPoly> allWords = new ArrayList<>();
        Pattern r = Pattern.compile(regex);
        List<BoundingPoly> all_words = new ArrayList<>();
        for (MpWord dict : dictList) {
            Matcher mDict = r.matcher(dict.getDescription());
            if (mDict.matches()) {
                all_words.add(dict.getBound());
            }
        }
        return allWords;
    }

    private BoundingPoly resizeContainerBuffer(BoundingPoly bound, int buffer) {
        bound.getVerticesList().get(0).toBuilder().setX(bound.getVerticesList().get(0).getX() - buffer);
        bound.getVerticesList().get(0).toBuilder().setY(bound.getVerticesList().get(0).getY() - buffer);
        bound.getVerticesList().get(1).toBuilder().setX(bound.getVerticesList().get(1).getX() + buffer);
        bound.getVerticesList().get(1).toBuilder().setY(bound.getVerticesList().get(1).getY() - buffer);
        bound.getVerticesList().get(2).toBuilder().setX(bound.getVerticesList().get(2).getX() + buffer);
        bound.getVerticesList().get(2).toBuilder().setY(bound.getVerticesList().get(2).getY() + buffer);
        bound.getVerticesList().get(3).toBuilder().setX(bound.getVerticesList().get(3).getX() - buffer);
        bound.getVerticesList().get(3).toBuilder().setY(bound.getVerticesList().get(3).getY() + buffer);
        return bound;
    }

    private float distanceBetweenPointfromOrigin(Vertex p1) {
        return (float) (Math.pow(p1.getX() - 0, 2) + Math.pow(p1.getY() - 0, 2));
    }

    private List<BoundingPoly> getWordBound(String word, List<MpWord> dictList) {
        List<BoundingPoly> allWords = new ArrayList<BoundingPoly>();
        for (MpWord dict : dictList) {
            if ((dict.getDescription() == word)) allWords.add(dict.getBound());
        }
        return allWords;
    }

    private String getFarthestRegex(List<MpWord> boundsTest, String regex) {
        return "NA";
    }

    public String getProviderAddress(String img, String providerName, List<MpWord> boundsTest, List<BoundingPoly> boundsBlock) {
        List<BoundingPoly> pincodeBound = getMatchedWord("^[0-9]{5}(?:-[0-9]{4})?$", boundsTest);
        ArrayList<String> finalResult = new ArrayList<>();
        for (BoundingPoly pincode :
                pincodeBound) {
            List<String> result = new ArrayList<>();
            for (MpWord bound :
                    boundsTest) {
                if (isWordInBlock(bound.getBound(), getContainerBound(pincode, boundsBlock))) {
                    result.add(bound.getDescription());
                }
            }
            finalResult.add(String.join(" ", result));
        }
        List<BoundExtractedResult<String>> match = FuzzySearch.extractAll(providerName, finalResult, x -> x.toString(), 0);
        if(match.isEmpty()){return "";}
        String matchFoo = Collections.max(match).getReferent();
        return matchFoo;
    }

    public void renderFromPostCall(String b64String) {
        OcrData ocrDataNone = null;
        try {
            ocrDataNone = detectDocumentText(b64String, FeatureType.NONE);
        } catch (Exception e) {
            e.printStackTrace();
        }
        OcrData ocrDataBlock = null;
        try {
            ocrDataBlock = detectDocumentText(b64String, FeatureType.BLOCK);
        } catch (Exception e) {
            e.printStackTrace();
        }
        String amountValue;

        List<MpWord> boundsTest = ocrDataNone.getBoundsTest();
        List<BoundingPoly> boundsBlock = ocrDataBlock.getBoundsBlock();
        //boundsTest,boundsBlock = get_document_bounds(b64_string, FeatureType.BLOCK)
        //boundsTest = get_document_bounds(b64_string,FeatureType.NONE)

        List<BoundingPoly> amountBound = getWordBound("amount", boundsTest);
        List<BoundingPoly> dueBound = getWordBound("due", boundsTest);
        List<BoundingPoly> amtResultBound = new ArrayList<BoundingPoly>();
        amtResultBound.addAll(amountBound);
        amtResultBound.addAll(dueBound);
        ArrayList<List<String>> finalResult = new ArrayList<List<String>>();
        for (BoundingPoly amt : amtResultBound) {
            List<String> result = new ArrayList<String>();
            for (MpWord bounds : boundsTest) {
                if (isWordInBlock(bounds.getBound(), getContainerBound(amt, boundsBlock))) {
                    result.add(bounds.getDescription());
                }
            }
            finalResult.add(result);
        }
        ArrayList<List<String>> filteredResult = new ArrayList<List<String>>();
        ArrayList<String> newList = new ArrayList<String>();
        for (List<String> result : finalResult) {
            Pattern r = Pattern.compile("\"^\\$?[0-9]+\\.?[0-9]*$\"");
            for (String s : result) {
                if (r.matcher(s).matches()) {
                    newList.add(s);
                }
            }
        }
        if (!newList.isEmpty()) {
            filteredResult.add(newList);
        }
        if (filteredResult.isEmpty()) amountValue = getFarthestRegex(boundsTest, "\"^\\$[0-9]+\\.?[0-9]*$\"");
        else amountValue = filteredResult.get(0).get(0);
        String providerName = getProviderName(b64String, boundsBlock, boundsTest);
        String providerAddress = getProviderAddress(b64String, providerName, boundsTest, boundsBlock);
        PatientData guarantorData = getPatientData(b64String, boundsTest, boundsBlock);
        System.out.println(amountValue);
        System.out.println(providerName);
        System.out.println(providerAddress);
        System.out.println(guarantorData.getPatientName());
        System.out.println(guarantorData.getPatientNumber());
    }

    private String getProviderName(String b64String, List<BoundingPoly> boundsBlock, List<MpWord> boundsTest) {
        float minDistance = 10000;
        BoundingPoly providerNameContainer = boundsBlock.get(0);
        Vertex.Builder origin = Vertex.newBuilder();
        origin.setX(0);
        origin.setY(0);
        for (BoundingPoly block : boundsBlock) {
            if (distanceBetweenPointfromOrigin(block.getVerticesList().get(0)) < minDistance) {
                minDistance = distanceBetweenPointfromOrigin(block.getVerticesList().get(0));
                providerNameContainer = block;
            }
        }
        providerNameContainer = resizeContainerBuffer(providerNameContainer, 10);
        List<String> listAll = getAllWordsWithinContainer(providerNameContainer, boundsTest);
        String prName = String.join(" ", listAll);
        return prName;
    }

    private PatientData getPatientData(String b64String, List<MpWord> boundsTest, List<BoundingPoly> boundsBlock) {
        String patientNumber;
        String patientName = "";
        List<BoundingPoly> guarantorBound = getWordBound("patient", boundsTest);
        ArrayList<List<String>> finalResult = new ArrayList<List<String>>();
        for (BoundingPoly grnBound : guarantorBound) {
            List<String> result = new ArrayList<>();
            for (MpWord bounds : boundsTest) {
                if (isWordInBlock(bounds.getBound(), getContainerBound(grnBound, boundsBlock)))
                    result.add(bounds.getDescription());
            }
            finalResult.add(result);
        }
        ArrayList<List<String>> filteredResult = new ArrayList<List<String>>();
        ArrayList<String> newList = new ArrayList<String>();
        for (List<String> result : finalResult) {
            Pattern r = Pattern.compile("\"^(d{9})$\"");
            for (String s : result) {
                if (r.matcher(s).matches()) {
                    newList.add(s);
                }
            }
            if (!newList.isEmpty()) filteredResult.add(newList);
        }

        if (filteredResult.isEmpty()) patientNumber = "";
        else patientNumber = filteredResult.get(0).get(0);

        PatientData patientData = new PatientData(patientNumber, patientName);
        return patientData;
    }
}

