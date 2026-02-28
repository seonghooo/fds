package com.seongho.fds.service;

import com.seongho.fds.config.FdsProperties;
import com.seongho.fds.dto.TransactionRequest;
import com.seongho.fds.dto.TransactionResponse;
import com.seongho.fds.repository.TrustedReceiverRepository;
import com.seongho.fds.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FdsService {

    private final KieContainer kieContainer;
    private final RedisUtil redisUtil;
    private final TrustedReceiverRepository trustedReceiverRepository;
    private final FdsProperties fdsProperties;

    public TransactionResponse validateTransaction(TransactionRequest request) {
        // 1. 화이트리스트 여부 확인 (DB 조회)
        boolean isTrusted = trustedReceiverRepository.existsBySenderIdAndReceiverId(
                request.getSenderId(), request.getReceiverId());
        request.setTrusted(isTrusted);

        // 2. Redis로 1분 내 송금 횟수 카운팅
        long count = redisUtil.incrementAndGet("tx_count:" + request.getSenderId(), 1);
        request.setRecentTransactionCount(count);

        TransactionResponse response = new TransactionResponse();
        KieSession kieSession = kieContainer.newKieSession();

        try {
            // 3. 룰 임계값을 global로 주입 (application.yml에서 설정)
            kieSession.setGlobal("response", response);
            kieSession.setGlobal("amountThreshold", fdsProperties.getAmountThreshold());
            kieSession.setGlobal("frequencyThreshold", fdsProperties.getFrequencyThreshold());
            kieSession.setGlobal("blacklistedIpPattern", fdsProperties.getBlacklistedIpPattern());
            kieSession.insert(request);
            kieSession.fireAllRules();
        } finally {
            kieSession.dispose();
        }

        return response;
    }
}
