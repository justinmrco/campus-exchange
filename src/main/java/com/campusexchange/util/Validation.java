package com.campusexchange.util;
import java.math.BigDecimal;
import java.time.*;
import java.util.Set;
public final class Validation {
 public static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
 public static final Set<String> CATEGORIES=Set.of("Textbooks","Electronics","Furniture","Clothing","Stationery","Bicycles","Other");
 public static String text(String value,int min,int max,String label){String s=value==null?"":value.trim();if(s.length()<min||s.length()>max)throw new IllegalArgumentException(label+" must contain "+min+"-"+max+" characters.");return s;}
 public static BigDecimal price(String value){try{BigDecimal p=new BigDecimal(value==null?"":value.trim());if(p.scale()>2||p.compareTo(new BigDecimal("0.01"))<0||p.compareTo(new BigDecimal("999999.00"))>0)throw new NumberFormatException();return p;}catch(NumberFormatException e){throw new IllegalArgumentException("Price must be 0.01-999999.00 with at most two decimal places.");}}
 public static String category(String value){if(value==null||!CATEGORIES.contains(value))throw new IllegalArgumentException("Choose a valid category.");return value;}
 public static LocalDateTime future(String value){try{LocalDateTime t=LocalDateTime.parse(value);if(!t.isAfter(LocalDateTime.now(ZONE)))throw new IllegalArgumentException("Choose a future time (Asia/Shanghai).");return t;}catch(java.time.format.DateTimeParseException|NullPointerException e){throw new IllegalArgumentException("Choose a valid time (Asia/Shanghai).");}}
}
