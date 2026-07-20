package com.c203.limit.domain.statistics.controller;

import java.time.LocalDate;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Domain controller. Unimplemented methods return null until real implementations are added. */
@RestController
public class StatisticsController implements StatisticsApi {

    @Override
    public ResponseEntity<Void> statistics1(Long dropId) {
        return null;
    }

    @Override
    public ResponseEntity<Void> statistics2(Long auctionId) {
        return null;
    }

    @Override
    public ResponseEntity<Void> statistics3(Long sellerId, String periodType, LocalDate from, LocalDate to) {
        return null;
    }

    @Override
    public ResponseEntity<Void> statistics4(Long categoryId, Integer limit) {
        return null;
    }

    @Override
    public ResponseEntity<Void> statistics5(Integer limit) {
        return null;
    }

    @Override
    public ResponseEntity<Void> statistics6(Integer limit, String List) {
        return null;
    }
}
