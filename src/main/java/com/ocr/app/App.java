package com.ocr.app;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        String filePath = args[0];
        System.out.println(filePath);
        TextAnalyzer myTextAnalyzer = new TextAnalyzer();
        myTextAnalyzer.renderFromPostCall(filePath);
    }
}
