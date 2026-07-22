package com.c203.limit.domain.seller.entity;

import java.time.OffsetDateTime;
import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import jakarta.persistence.*;

@Entity @Table(name = "seller_application")
public class SellerApplication {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name="application_id") private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="user_id") private Member member;
    @Column(name="application_version", nullable=false) private int version;
    @Enumerated(EnumType.STRING) @Column(name="seller_type", nullable=false) private SellerType type;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private SellerApplicationStatus status;
    @Column(name="applicant_name") private String applicantName;
    @Column(name="applicant_email") private String applicantEmail;
    @Column(name="applicant_phone") private String applicantPhone;
    @Column(name="country_code", length=2) private String countryCode;
    @Column(name="business_name") private String businessName;
    @Column(name="business_number") private String businessNumber;
    @Column(name="business_address") private String businessAddress;
    @Column(name="settlement_bank_name") private String settlementBankName;
    @Column(name="settlement_account", length=500) private String settlementAccount;
    @Column(name="settlement_account_holder") private String settlementAccountHolder;
    @Column(name="planned_category") private String plannedCategory;
    @Column(name="terms_agreed_at") private OffsetDateTime termsAgreedAt;
    @Column(name="rejection_reason") private String rejectionReason;
    @Column(name="processed_admin_id") private Long processedAdminId;
    @Column(name="submitted_at") private OffsetDateTime submittedAt;
    @Column(name="reviewed_at") private OffsetDateTime reviewedAt;
    @Column(name="created_at", nullable=false, updatable=false) private OffsetDateTime createdAt;
    @Column(name="updated_at", nullable=false) private OffsetDateTime updatedAt;
    protected SellerApplication() {}
    public static SellerApplication draft(Member member, int version, SellerType type) {
        SellerApplication a=new SellerApplication(); a.member=member; a.version=version; a.type=type;
        a.status=SellerApplicationStatus.DRAFT; a.createdAt=OffsetDateTime.now(); a.updatedAt=a.createdAt; return a;
    }
    public void update(String name,String email,String phone,String country,String businessName,String businessNumber,
            String businessAddress,String bank,String account,String holder,String category,boolean termsAgreed) {
        requireEditable(); applicantName=name; applicantEmail=email; applicantPhone=phone; countryCode=country;
        this.businessName=businessName; this.businessNumber=businessNumber; this.businessAddress=businessAddress;
        settlementBankName=bank; settlementAccount=account; settlementAccountHolder=holder; plannedCategory=category;
        termsAgreedAt=termsAgreed?OffsetDateTime.now():null; rejectionReason=null; status=SellerApplicationStatus.DRAFT;
        updatedAt=OffsetDateTime.now();
    }
    public void submit(boolean hasDocument) {
        requireEditable();
        if (!hasDocument || blank(applicantName)||blank(applicantEmail)||blank(applicantPhone)||blank(countryCode)
                ||blank(settlementBankName)||blank(settlementAccount)||blank(settlementAccountHolder)||termsAgreedAt==null
                ||(type==SellerType.BUSINESS&&(blank(businessName)||blank(businessNumber))))
            throw new BusinessException(ErrorCode.SELLER_APPLICATION_INCOMPLETE);
        status=SellerApplicationStatus.SUBMITTED; submittedAt=OffsetDateTime.now(); updatedAt=submittedAt;
    }
    public void cancel() {
        if (!(status==SellerApplicationStatus.DRAFT||status==SellerApplicationStatus.SUBMITTED||status==SellerApplicationStatus.UNDER_REVIEW))
            throw new BusinessException(ErrorCode.SELLER_APPLICATION_NOT_CANCELABLE);
        status=SellerApplicationStatus.CANCELED; updatedAt=OffsetDateTime.now();
    }
    public void startReview(Long adminId){ if(status!=SellerApplicationStatus.SUBMITTED) throw new BusinessException(ErrorCode.SELLER_APPLICATION_NOT_EDITABLE); status=SellerApplicationStatus.UNDER_REVIEW; processedAdminId=adminId; updatedAt=OffsetDateTime.now(); }
    public void approve(Long adminId){ if(status!=SellerApplicationStatus.UNDER_REVIEW) throw new BusinessException(ErrorCode.SELLER_APPLICATION_NOT_EDITABLE); status=SellerApplicationStatus.APPROVED; processedAdminId=adminId; reviewedAt=OffsetDateTime.now(); updatedAt=reviewedAt; }
    public void reject(Long adminId,String reason){ if(status!=SellerApplicationStatus.UNDER_REVIEW||blank(reason)) throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE); status=SellerApplicationStatus.REJECTED; processedAdminId=adminId; rejectionReason=reason; reviewedAt=OffsetDateTime.now(); updatedAt=reviewedAt; }
    public void requireEditable(){ if(!(status==SellerApplicationStatus.DRAFT||status==SellerApplicationStatus.REJECTED)) throw new BusinessException(ErrorCode.SELLER_APPLICATION_NOT_EDITABLE); }
    private boolean blank(String v){return v==null||v.isBlank();}
    public Long getId(){return id;} public Member getMember(){return member;} public int getVersion(){return version;} public SellerType getType(){return type;} public SellerApplicationStatus getStatus(){return status;}
    public String getApplicantName(){return applicantName;} public String getApplicantEmail(){return applicantEmail;} public String getApplicantPhone(){return applicantPhone;} public String getCountryCode(){return countryCode;} public String getBusinessName(){return businessName;} public String getPlannedCategory(){return plannedCategory;} public String getRejectionReason(){return rejectionReason;}
    public String getSettlementAccount(){return settlementAccount;} public OffsetDateTime getSubmittedAt(){return submittedAt;} public OffsetDateTime getReviewedAt(){return reviewedAt;} public OffsetDateTime getCreatedAt(){return createdAt;} public OffsetDateTime getUpdatedAt(){return updatedAt;}
}
