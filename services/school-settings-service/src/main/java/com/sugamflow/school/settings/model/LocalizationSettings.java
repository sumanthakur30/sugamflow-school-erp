package com.sugamflow.school.settings.model;

public class LocalizationSettings {

  private String language = "en";
  private String currency = "INR";
  private String dateFormat = "dd/MM/yyyy";
  private String timeFormat = "HH:mm";
  private String timeZone = "Asia/Kolkata";
  private String academicYearFormat = "YYYY-YYYY";
  private String marksFormat = "PERCENTAGE";
  private String gradeSystem = "A-F";
  private String numberFormat = "en-IN";
  private String addressFormat = "IN";
  private String paperSize = "A4";

  public String getLanguage() { return language; }
  public void setLanguage(String language) { this.language = language; }
  public String getCurrency() { return currency; }
  public void setCurrency(String currency) { this.currency = currency; }
  public String getDateFormat() { return dateFormat; }
  public void setDateFormat(String dateFormat) { this.dateFormat = dateFormat; }
  public String getTimeFormat() { return timeFormat; }
  public void setTimeFormat(String timeFormat) { this.timeFormat = timeFormat; }
  public String getTimeZone() { return timeZone; }
  public void setTimeZone(String timeZone) { this.timeZone = timeZone; }
  public String getAcademicYearFormat() { return academicYearFormat; }
  public void setAcademicYearFormat(String academicYearFormat) { this.academicYearFormat = academicYearFormat; }
  public String getMarksFormat() { return marksFormat; }
  public void setMarksFormat(String marksFormat) { this.marksFormat = marksFormat; }
  public String getGradeSystem() { return gradeSystem; }
  public void setGradeSystem(String gradeSystem) { this.gradeSystem = gradeSystem; }
  public String getNumberFormat() { return numberFormat; }
  public void setNumberFormat(String numberFormat) { this.numberFormat = numberFormat; }
  public String getAddressFormat() { return addressFormat; }
  public void setAddressFormat(String addressFormat) { this.addressFormat = addressFormat; }
  public String getPaperSize() { return paperSize; }
  public void setPaperSize(String paperSize) { this.paperSize = paperSize; }
}
