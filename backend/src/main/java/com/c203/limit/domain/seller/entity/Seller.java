package com.c203.limit.domain.seller.entity;
import java.math.BigDecimal; import java.time.OffsetDateTime; import com.c203.limit.domain.member.entity.Member; import jakarta.persistence.*;
@Entity @Table(name="seller") public class Seller {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="seller_id") private Long id;
 @OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id",unique=true) private Member member;
 @Enumerated(EnumType.STRING) @Column(name="seller_type",nullable=false) private SellerType type; @Column(name="country_code",nullable=false,length=2) private String countryCode;
 @Column(name="business_name") private String businessName; @Column(nullable=false) private String status; @Column(name="product_limit",nullable=false) private int productLimit;
 @Column(name="sales_amount_limit",nullable=false) private BigDecimal salesAmountLimit; @Column(name="approved_at",nullable=false) private OffsetDateTime approvedAt;
 protected Seller(){} public static Seller approve(SellerApplication a){var s=new Seller();s.member=a.getMember();s.type=a.getType();s.countryCode=a.getCountryCode();s.businessName=a.getBusinessName();s.status="ACTIVE";s.productLimit=10;s.salesAmountLimit=BigDecimal.ZERO;s.approvedAt=OffsetDateTime.now();return s;}
 public void updateStatus(String status){this.status=status;} public void updateLimits(int productLimit,BigDecimal salesAmountLimit){if(productLimit<0||salesAmountLimit==null||salesAmountLimit.signum()<0)throw new com.c203.limit.global.exception.BusinessException(com.c203.limit.global.exception.ErrorCode.INVALID_INPUT_VALUE);this.productLimit=productLimit;this.salesAmountLimit=salesAmountLimit;}
 public Long getId(){return id;} public Member getMember(){return member;} public SellerType getType(){return type;} public String getCountryCode(){return countryCode;} public String getBusinessName(){return businessName;} public String getStatus(){return status;} public int getProductLimit(){return productLimit;} public BigDecimal getSalesAmountLimit(){return salesAmountLimit;} public OffsetDateTime getApprovedAt(){return approvedAt;}
}
