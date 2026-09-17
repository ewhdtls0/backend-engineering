package study.refund.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import study.refund.exception.RefundProcessingException;

@Configuration(proxyBeanMethods = false)
public class GatewayConfiguration {
    @Bean
    @ConditionalOnMissingBean(PaymentGateway.class)
    PaymentGateway unconfiguredPaymentGateway() {
        return (key, amount) -> {
            throw new RefundProcessingException("실제 결제 연동은 제공되지 않습니다. 테스트 Fake/Mock을 사용하세요.");
        };
    }
}
