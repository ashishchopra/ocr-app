package com.ocr.app;

import com.google.api.services.vision.v1.model.BoundingPoly;
import com.google.cloud.vision.v1.*;
import com.google.api.services.vision.v1.model.Vertex;
import com.google.protobuf.ByteString;
import me.xdrop.fuzzywuzzy.FuzzySearch;
import me.xdrop.fuzzywuzzy.model.BoundExtractedResult;

import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

enum FeatureType{
    PAGE,BLOCK,PARA,WORD,SYMBOL,NONE;
}


public class TextAnalyzer {

    public  void detectDocumentText(String filePath,  FeatureType featureType) throws Exception,
            IOException {
        List<AnnotateImageRequest> requests = new ArrayList<>();

        ByteString imgBytes = ByteString.readFrom(new FileInputStream(filePath));

        Image img = Image.newBuilder().setContent(imgBytes).build();
        Feature feat = Feature.newBuilder().setType(Feature.Type.DOCUMENT_TEXT_DETECTION).build();
        AnnotateImageRequest request =
                AnnotateImageRequest.newBuilder().addFeatures(feat).setImage(img).build();
        requests.add(request);

        try (ImageAnnotatorClient client = ImageAnnotatorClient.create()) {
            BatchAnnotateImagesResponse response = client.batchAnnotateImages(requests);
            List<AnnotateImageResponse> responses = response.getResponsesList();
            client.close();

            for (AnnotateImageResponse res : responses) {
                if (res.hasError()) {
                    System.out.printf("Error: %s\n", res.getError().getMessage());
                    return;
                }

                // For full list of available annotations, see http://g.co/cloud/vision/docs
                TextAnnotation annotation = res.getFullTextAnnotation();
                for (Page page: annotation.getPagesList()) {
                    String pageText = "";
                    for (Block block : page.getBlocksList()) {
                        String blockText = "";
                        for (Paragraph para : block.getParagraphsList()) {
                            String paraText = "";
                            for (Word word: para.getWordsList()) {
                                String wordText = "";
                                for (Symbol symbol: word.getSymbolsList()) {
                                    wordText = wordText + symbol.getText();
                                    // out.format("Symbol text: %s (confidence: %f)\n", symbol.getText(),
                                    //     symbol.getConfidence());
                                }
                                System.out.format("Word text: %s (confidence: %f)\n\n", wordText, word.getConfidence());
                                paraText = String.format("%s %s", paraText, wordText);
                            }
                            // Output Example using Paragraph:
                            // out.println("\nParagraph: \n" + paraText);
                            // out.format("Paragraph Confidence: %f\n", para.getConfidence());
                            blockText = blockText + paraText;
                        }
                        pageText = pageText + blockText;
                    }
                }
                // out.println("\nComplete annotation:");
                // out.println(annotation.getText());
            }
        }
    }

    private float area(int x1, int y1, int x2, int y2, int x3, int y3){
        return (float) Math.abs(((x1 * (y2 - y3) +
                x2 * (y3 - y1) +
                x3 * (y1 - y2)) / 2.0));
    }

    private boolean check(Vertex vertex,BoundingPoly bound){
        int x = vertex.getX();
        int y = vertex.getY();
        int x1 = bound.getVertices().get(0).getX() - 2;
        int y1 = bound.getVertices().get(0).getY() - 2;
        int x2 = bound.getVertices().get(1).getX() + 2;
        int y2 = bound.getVertices().get(1).getY() - 2;
        int x3 = bound.getVertices().get(2).getX() + 2;
        int y3 = bound.getVertices().get(2).getY() + 2;
        int x4 = bound.getVertices().get(3).getX() - 2;
        int y4 = bound.getVertices().get(3).getY() + 2;

        float A = (area(x1, y1, x2, y2, x3, y3) +
                        area(x1, y1, x4, y4, x3, y3));
        float A1 = area(x, y, x1, y1, x2, y2);
        float A2 = area(x, y, x2, y2, x3, y3);
        float A3 = area(x, y, x3, y3, x4, y4);
        float A4 = area(x, y, x1, y1, x4, y4);

        return (A == A1 + A2 + A3 + A4);
    }

    private boolean isWordInBlock(BoundingPoly wordBound, BoundingPoly blockBound){
        if (check(wordBound.getVertices().get(0),blockBound) &&
        check(wordBound.getVertices().get(1),blockBound) &&
        check(wordBound.getVertices().get(2),blockBound) &&
        check(wordBound.getVertices().get(3),blockBound)){
            return true;
        }
      else return false;
    }

    private BoundingPoly  getContainerBound(BoundingPoly wordBound, List<BoundingPoly> containerList){
        for (BoundingPoly container : containerList){
            BoundingPoly word_bound = null;
            if(isWordInBlock(word_bound,container)) return container;
            return containerList.get(0);
        }
    }

    private List<String> getAllWordsWithinContainer(BoundingPoly containerBound, List<MpWord> wordList){
        ArrayList<String> wordsWithin = new ArrayList<String>();
        for(MpWord word : wordList){
            if(isWordInBlock(word.getBound(), containerBound))wordsWithin.add(word.getDescription());
        }
        return wordsWithin;
    }

    private List<BoundingPoly> getMatchedWord(String regex, List <MpWord> dictList) {
        List<BoundingPoly> allWords = null;
        Pattern r = Pattern.compile(regex);
        List<BoundingPoly> all_words = null;
        for (MpWord dict : dictList) {
            Matcher mDict = r.matcher(dict.getDescription());
            if (mDict.matches()) {
                all_words.add(dict.getBound());
            }
        }
        return allWords;
    }

    private BoundingPoly resizeContainerBuffer(BoundingPoly bound, int buffer){
        bound.getVertices().get(0).setX(bound.getVertices().get(0).getX() - buffer);
        bound.getVertices().get(0).setY(bound.getVertices().get(0).getY() - buffer);
        bound.getVertices().get(1).setX(bound.getVertices().get(1).getX() + buffer);
        bound.getVertices().get(1).setY(bound.getVertices().get(1).getY() - buffer);
        bound.getVertices().get(2).setX(bound.getVertices().get(2).getX() + buffer);
        bound.getVertices().get(2).setY(bound.getVertices().get(2).getY() + buffer);
        bound.getVertices().get(3).setX(bound.getVertices().get(3).getX() - buffer);
        bound.getVertices().get(3).setY(bound.getVertices().get(3).getY() + buffer);
        return bound;
    }

    private float distanceBetweenPoint(Vertex p1, Vertex p2){
        return (float) (Math.pow(p1.getX()-p2.getX(),2) + Math.pow(p1.getY()-p2.getY(),2));
    }

    private List<BoundingPoly> getWordBound(String word,List<MpWord>dictList){
        List<BoundingPoly> allWords = null;
        for (MpWord dict : dictList){
            if((dict.getDescription() == word))allWords.add(dict.getBound());
        }
        return allWords;
    }
    private String getFarthestRegex(List<MpWord> boundsTest,String regex){
        return null;
    }

    public String getProviderAddress(String img, String providerName, List<MpWord> boundsTest, List<BoundingPoly> boundsBlock){
        List<BoundingPoly> pincodeBound = getMatchedWord("^[0-9]{5}(?:-[0-9]{4})?$", boundsTest);
        ArrayList<String> finalResult = null;
        for (BoundingPoly pincode:
                pincodeBound) {
            List<String> result = null;
            for (MpWord bound:
                    boundsTest) {
                if(isWordInBlock(bound.getBound(), getContainerBound(pincode, boundsBlock))) {
                    result.add(bound.getDescription());
                }
            }
            finalResult.add(String.join(" ", result));
        }
        BoundExtractedResult<String> match = FuzzySearch.extractOne(providerName, finalResult, x -> x.toString());
        String matchFoo = match.getReferent();
        return matchFoo;
    }

    public void renderFromPostCall(String b64String) {
        OcrData ocrDataNone = detectDocumentText(b64String, FeatureType.NONE);
        String amountValue;

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
        provider_name = get_provider_name(b64_string, boundsBlock, boundsTest);
        String providerAddress = getProviderAddress(b64_string, provider_name, boundsTest, boundsBlock);
        guarantor_number, guarantor_name = get_guarantor_number(b64_string, boundsTest, boundsBlock);
    }

    private String getProviderName(String b64_string,List<BoundingPoly> boundsBlock,List<MpWord>boundsTest){
        int minDistance = 10000;
        BoundingPoly providerNameContainer = boundsBlock.get(0);
        origin = {"x":0, "y":0}
        for (BoundingPoly block : boundsBlock){
            if(distanceBetweenPoint(block.getVertices().get(0),origin) < minDistance){
                minDistance = distanceBetweenPoint(block.vertices[0],origin);
                providerNameContainer = block;
            }
        }


        resize_container_with_buffer(provider_name_container,10)
        list_all = get_all_words_within_container(provider_name_container,boundsTest)
    # print(list_all)
        image = Image.open(b64_string)
        draw_boxes(image,boundsBlock,'green')
        rgb_im = image.convert('RGB')
        rgb_im.show()
        return ' '.join(list_all)
    }

}