package com.campusexchange.service;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.servlet.http.HttpSession;
@Service public class GlobalEventService {
 @Autowired private EventHub hub;
 public SseEmitter subscribe(int user,HttpSession session){return hub.subscribe("marketplace",user,session);}
 public void publish(String event,String data){hub.publish("marketplace",event,data);}
}
