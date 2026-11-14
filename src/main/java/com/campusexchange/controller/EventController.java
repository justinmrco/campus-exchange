package com.campusexchange.controller;

import com.campusexchange.entity.User;
import com.campusexchange.service.GlobalEventService;
import com.campusexchange.service.UserEventService;
import com.campusexchange.util.SseUtil;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class EventController {
    @Autowired private com.campusexchange.service.EventHub hub;
    private SseEmitter snapshot(SseEmitter emitter){try{emitter.send(SseEmitter.event().name("snapshot").data(java.util.Map.of("revision",hub.revision())));}catch(Exception error){emitter.complete();}return emitter;}

    @Autowired
    private UserEventService userEventService;

    @Autowired
    private GlobalEventService globalEventService;

    @GetMapping(value = "/events/user", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter userEvents(HttpSession session, HttpServletRequest request, HttpServletResponse response) {
        SseUtil.setHeaders(response);
        User user = (User) request.getAttribute("loginUser");
        if (user == null) return SseUtil.closedEmitter();
        return snapshot(userEventService.subscribe(user.getUserId(),session));
    }

    @GetMapping(value = "/events/marketplace", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter marketplaceEvents(HttpSession session, HttpServletRequest request, HttpServletResponse response) {
        SseUtil.setHeaders(response);
        if (request.getAttribute("loginUser") == null) return SseUtil.closedEmitter();
        return snapshot(globalEventService.subscribe(((User)request.getAttribute("loginUser")).getUserId(),session));
    }
}
