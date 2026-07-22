package com.c203.limit.domain.notification.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Domain controller. Unimplemented methods return null until real implementations are added. */
@RestController
public class NotificationController implements NotificationApi {

    @Override
    public ResponseEntity<Void> notification01() {
        return null;
    }

    @Override
    public ResponseEntity<Void> notification02(Object body) {
        return null;
    }
}
