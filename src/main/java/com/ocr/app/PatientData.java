package com.ocr.app;

public class PatientData {

    private String patientNumber;
    private String patientName;


    public PatientData(String patientNumber, String patientName) {
        this.patientNumber = patientNumber;
        this.patientName = patientName;
    }

    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    public String getPatientNumber() {
        return patientNumber;
    }

    public void setPatientNumber(String patientName) {
        this.patientNumber = patientNumber;
    }
}
