package com.campusexchange.service;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.servlet.http.HttpSession;
@Service public class LedgerEventService {
 @Autowired private EventHub hub;
 public SseEmitter subscribe(int ledger,int user,HttpSession session){return hub.subscribe("ledger:"+ledger,user,session);}
 public void publishReload(int ledger){hub.publish("ledger:"+ledger,"reload","{}");}
}
