package com.c203.limit.admin.controller;

import java.time.LocalDate;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Domain controller. Unimplemented methods return null until real implementations are added. */
@RestController
public class AdminController implements AdminApi {

    @Override
    public ResponseEntity<Void> adminauth01(Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> adminmember01(Integer page, Integer size, String email, String nickname, String status, String role, LocalDate createdFrom, LocalDate createdTo) {
        return null;
    }

    @Override
    public ResponseEntity<Void> adminmember02(Long memberId) {
        return null;
    }

    @Override
    public ResponseEntity<Void> adminmember03(Long memberId, Integer page, Integer size, String status) {
        return null;
    }

    @Override
    public ResponseEntity<Void> adminmember04(Long memberId, Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> adminmember05(Long restrictionId, Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> adminwithdrawal01(Integer page, Integer size, String status, String requestedFrom, String requestedTo) {
        return null;
    }

    @Override
    public ResponseEntity<Void> adminwithdrawal02(Long withdrawalRequestId) {
        return null;
    }

    @Override
    public ResponseEntity<Void> adminwithdrawal03(Long withdrawalRequestId, Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> adminsellerapp01(Integer page, Integer size, String status, String sellerType, String countryCode, String submittedFrom, String submittedTo) {
        return null;
    }

    @Override
    public ResponseEntity<Void> adminsellerapp02(Long applicationId) {
        return null;
    }

    @Override
    public ResponseEntity<Void> adminsellerapp03(Long documentId, String expiresInSeconds) {
        return null;
    }

    @Override
    public ResponseEntity<Void> adminsellerapp04(Long applicationId) {
        return null;
    }

    @Override
    public ResponseEntity<Void> adminsellerapp05(Long applicationId, Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> adminsellerapp06(Long applicationId, Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> adminseller01(Integer page, Integer size, String status, String sellerType, String countryCode, String keyword) {
        return null;
    }

    @Override
    public ResponseEntity<Void> adminseller02(Long sellerProfileId) {
        return null;
    }

    @Override
    public ResponseEntity<Void> adminseller03(Long sellerProfileId, Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> adminseller04(Long sellerProfileId, Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> admininquiry01(Integer page, Integer size, String status, String category, Long memberId, LocalDate createdFrom, LocalDate createdTo) {
        return null;
    }

    @Override
    public ResponseEntity<Void> admininquiry02(Long inquiryId) {
        return null;
    }

    @Override
    public ResponseEntity<Void> admininquiry03(Long inquiryId, Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> admininquiry04(Long inquiryId, Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> adminrole01() {
        return null;
    }

    @Override
    public ResponseEntity<Void> adminrole02(Long memberId, Object body) {
        return null;
    }

    @Override
    public ResponseEntity<Void> adminrole03(Long memberId, Long roleId) {
        return null;
    }

    @Override
    public ResponseEntity<Void> adminlog01(Integer page, Integer size, Long adminMemberId, String actionType, String targetType, Long targetId, LocalDate createdFrom, LocalDate createdTo) {
        return null;
    }

    @Override
    public ResponseEntity<Void> adminlog02(Long adminActionLogId) {
        return null;
    }
}
