package com.campusexchange.util;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public class SseUtil {

    public static SseEmitter closedEmitter() {
        SseEmitter e = new SseEmitter();
        e.complete();
        return e;
    }

    public static void setHeaders(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Connection", "keep-alive");
    }
}
