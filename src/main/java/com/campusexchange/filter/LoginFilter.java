package com.campusexchange.filter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import com.campusexchange.dao.UserDAO;
import com.campusexchange.entity.User;
import com.campusexchange.service.UserEventService;
import java.io.IOException;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
@Component @Order(1)
public class LoginFilter implements Filter {
 @Autowired private UserDAO users;
 @Autowired private UserEventService events;
 private record Window(long started,int count){}
 private final Map<String,Window> attempts=new HashMap<>();
 private synchronized boolean allow(String key,int max){long now=System.currentTimeMillis();attempts.entrySet().removeIf(e->now-e.getValue().started()>600000);if(attempts.size()>10000&&!attempts.containsKey(key))return false;Window old=attempts.get(key);if(old==null)old=new Window(now,0);attempts.put(key,new Window(old.started(),old.count()+1));return old.count()<max;}
 public void doFilter(ServletRequest request,ServletResponse response,FilterChain chain)throws IOException,ServletException{
  HttpServletRequest req=(HttpServletRequest)request;HttpServletResponse resp=(HttpServletResponse)response;
  String path=req.getServletPath();resp.setHeader("X-Content-Type-Options","nosniff");resp.setHeader("Referrer-Policy","same-origin");resp.setHeader("X-Frame-Options","DENY");
  if(path.startsWith("/css/")||path.startsWith("/js/")||path.startsWith("/images/demo/")||path.equals("/error")||path.equals("/favicon.ico")){chain.doFilter(req,resp);return;}
  resp.setHeader("Cache-Control","no-store");HttpSession session=req.getSession(true);
  String csrf=(String)session.getAttribute("csrf");if(csrf==null){csrf=UUID.randomUUID().toString();session.setAttribute("csrf",csrf);}req.setAttribute("csrf",csrf);
  boolean mutation=!Set.of("GET","HEAD","OPTIONS").contains(req.getMethod());
  if(mutation){String submitted;try{submitted=req.getParameter("_csrf");}catch(IllegalStateException error){resp.sendError(413,"Photo is too large. Choose a photo up to 5 MB and retry; reselect the file.");return;}if(submitted==null||!MessageDigest.isEqual(csrf.getBytes(StandardCharsets.UTF_8),submitted.getBytes(StandardCharsets.UTF_8))){resp.sendError(403,"Form expired. Reload and retry.");return;}}
  User user=null;Object id=session.getAttribute("userId");
  if(id instanceof Integer){try{user=users.findById((Integer)id);}catch(java.sql.SQLException e){resp.sendError(503,"Account verification unavailable. Retry later.");return;}if(user==null||!user.isActive()){events.disconnect((Integer)id);session.invalidate();resp.sendRedirect(req.getContextPath()+"/login?error=session");return;}user.setPasswordHash(null);req.setAttribute("loginUser",user);}
  boolean publicPath=Set.of("/","/login","/register").contains(path);
  if(!publicPath&&user==null){resp.sendRedirect(req.getContextPath()+"/login?error=session");return;}
  if((path.equals("/admin")||path.startsWith("/admin/"))&&!user.isAdmin()){resp.sendError(403);return;}
  if(mutation){String key=null;int max=20;if(path.equals("/login")){String name=Objects.toString(req.getParameter("username"),"").trim().toLowerCase(Locale.ROOT);if(name.length()>50){resp.sendError(400,"Username is too long.");return;}if(!allow("login-name:"+name,30)){resp.setHeader("Retry-After","600");resp.sendError(429,"Too many attempts; retry in ten minutes.");return;}key="login-ip:"+req.getRemoteAddr();max=60;}else if(path.equals("/register")){key="register:"+req.getRemoteAddr();max=10;}else if(path.startsWith("/handover/verify/")){key="verify:"+id+":"+path;max=10;}if(key!=null&&!allow(key,max)){resp.setHeader("Retry-After","600");resp.sendError(429,"Too many attempts; retry in ten minutes.");return;}}
  chain.doFilter(req,resp);
 }
}
