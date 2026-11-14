package com.campusexchange.util;
import org.springframework.web.context.request.*;
public final class Pagination {
 public static int page(){var a=RequestContextHolder.getRequestAttributes();if(!(a instanceof ServletRequestAttributes s))return 0;String p=s.getRequest().getParameter("page");try{int n=p==null?0:Integer.parseInt(p);if(n<0||n>20000)throw new NumberFormatException();return n;}catch(NumberFormatException e){throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Invalid page");}}
 public static String limit(){return " LIMIT 25 OFFSET "+(page()*24);}
}
