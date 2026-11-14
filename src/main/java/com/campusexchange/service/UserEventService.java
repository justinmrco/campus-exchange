package com.campusexchange.service;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.servlet.http.HttpSession;
@Service public class UserEventService {
 @Autowired private EventHub hub;
 public SseEmitter subscribe(int user,HttpSession session){return hub.subscribe("user:"+user,user,session);}
 public void publish(int user,String event,String data){hub.publish("user:"+user,event,data);}
 public void disconnect(int user){hub.disconnect(user);}
}
