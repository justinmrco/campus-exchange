package com.campusexchange.service;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.servlet.http.HttpSession;
import com.campusexchange.dao.UserDAO;
import java.util.concurrent.*;
@Component
public class EventHub {
 private final java.util.concurrent.atomic.AtomicLong revision=new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis());
 public long revision(){return revision.get();}
 private record Client(String channel,int user,HttpSession session,SseEmitter emitter){}
 private final CopyOnWriteArrayList<Client> clients=new CopyOnWriteArrayList<>();
 @Autowired private UserDAO users;
 public synchronized SseEmitter subscribe(String channel,int user,HttpSession session){
  if(clients.size()>=1000||clients.stream().filter(c->c.user()==user).count()>=6)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,"Too many live connections");
  SseEmitter e=new SseEmitter(120000L);Client c=new Client(channel,user,session,e);clients.add(c);
  e.onCompletion(()->clients.remove(c));e.onTimeout(()->{clients.remove(c);e.complete();});e.onError(error->clients.remove(c));send(c,"connected","{}");return e;
 }
 private void send(Client c,String event,String data){try{c.emitter().send(SseEmitter.event().name(event).data(data));}catch(Exception error){clients.remove(c);c.emitter().complete();}}
 public void publish(String channel,String event,String data){revision.incrementAndGet();for(Client c:clients)if(c.channel().equals(channel))send(c,event,data);}
 public void disconnect(int user){for(Client c:clients)if(c.user()==user){clients.remove(c);c.emitter().complete();}}
 @Scheduled(fixedDelay=15000) public void heartbeat(){for(Client c:clients){try{var user=users.findById(c.user());if(user==null||!user.isActive()||!Integer.valueOf(c.user()).equals(c.session().getAttribute("userId"))){clients.remove(c);c.emitter().complete();continue;}send(c,"heartbeat","{}");}catch(Exception error){clients.remove(c);c.emitter().complete();}}}
}
