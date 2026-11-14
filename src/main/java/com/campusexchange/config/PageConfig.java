package com.campusexchange.config;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.*;
import jakarta.servlet.http.*;
import java.util.*;
@Configuration public class PageConfig implements WebMvcConfigurer {
 @org.springframework.beans.factory.annotation.Autowired private com.campusexchange.service.EventHub events;
 @Override public void addInterceptors(InterceptorRegistry registry){registry.addInterceptor(new HandlerInterceptor(){
  @Override public boolean preHandle(HttpServletRequest req,HttpServletResponse resp,Object handler){req.setAttribute("eventRevision",events.revision());return true;}
  @Override public void postHandle(HttpServletRequest req,HttpServletResponse resp,Object handler,ModelAndView view){if(view==null||view.getViewName()==null||view.getViewName().startsWith("redirect:"))return;boolean paginated=false,next=false;for(String key:List.of("listings","ledgers","users","disputes")){Object value=view.getModel().get(key);if(value instanceof List<?> records && !req.getServletPath().startsWith("/handover/ledger/")){paginated=true;next|=records.size()>24;if(records.size()>24)view.addObject(key,records.subList(0,24));}}if(paginated){view.addObject("page",com.campusexchange.util.Pagination.page());view.addObject("hasNext",next);}}
 });}
}
