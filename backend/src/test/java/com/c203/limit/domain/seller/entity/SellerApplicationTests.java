package com.c203.limit.domain.seller.entity;
import static org.assertj.core.api.Assertions.*; import org.junit.jupiter.api.Test; import com.c203.limit.domain.member.entity.Member; import com.c203.limit.global.exception.*;
class SellerApplicationTests {
 @Test void completeDraftCanBeSubmitted(){var a=SellerApplication.draft(Member.createLocal("u@example.com","p","runner",null),1,SellerType.INDIVIDUAL);a.update("Woo","seller@example.com","+821012345678","US",null,null,"New York","Bank","123","Woo","SNEAKERS",true);a.submit(true);assertThat(a.getStatus()).isEqualTo(SellerApplicationStatus.SUBMITTED);assertThat(a.getSubmittedAt()).isNotNull();}
 @Test void incompleteDraftCannotBeSubmitted(){var a=SellerApplication.draft(Member.createLocal("u@example.com","p","runner",null),1,SellerType.BUSINESS);assertThatThrownBy(()->a.submit(false)).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SELLER_APPLICATION_INCOMPLETE));}
 @Test void approvedApplicationCannotBeCanceled(){var a=SellerApplication.draft(Member.createLocal("u@example.com","p","runner",null),1,SellerType.INDIVIDUAL);a.update("Woo","seller@example.com","+821012345678","US",null,null,null,"Bank","123","Woo","SNEAKERS",true);a.submit(true);a.startReview(1L);a.approve(1L);assertThatThrownBy(a::cancel).isInstanceOf(BusinessException.class);}
}
