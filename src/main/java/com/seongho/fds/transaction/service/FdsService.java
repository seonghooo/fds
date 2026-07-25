package com.seongho.fds.transaction.service;

import com.seongho.fds.global.config.FdsProperties;
import com.seongho.fds.transaction.dto.TransactionRequest;
import com.seongho.fds.transaction.dto.TransactionResponse;
import com.seongho.fds.whitelist.repository.TrustedReceiverRepository;
import com.seongho.fds.global.redis.RedisUtil;
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
        request.setTrusted(Boolean.valueOf(isTrusted));

        // 2. Redis로 1분 내 송금 횟수 카운팅 (화이트리스트 수신자는 제외)
        long count;
        if (isTrusted) {
            count = redisUtil.getCount("tx_count:" + request.getSenderId());
        } else {
            count = redisUtil.incrementAndGet("tx_count:" + request.getSenderId(), 1);
        }
        request.setRecentTransactionCount(count);

        // 3. 1분 내 고유 수신자 수 카운팅 (분산 송금 감지용)
        long uniqueReceiverCount = redisUtil.addToSetAndGetSize(
                "tx_receivers:" + request.getSenderId(), request.getReceiverId(), 1);
        request.setRecentUniqueReceiverCount(uniqueReceiverCount);

        // 4. 유저의 과거 평균 송금액 조회 (이상 금액 감지용)
        long userAvgAmount = redisUtil.getUserAvgAmount(request.getSenderId());
        request.setUserAvgAmount(userAvgAmount);

        TransactionResponse response = new TransactionResponse();
        KieSession kieSession = kieContainer.newKieSession();

        try {
            // 5. 룰 임계값을 global로 주입 (application.yml에서 설정)
            kieSession.setGlobal("response", response);
            kieSession.setGlobal("amountThreshold", fdsProperties.getAmountThreshold());
            kieSession.setGlobal("frequencyThreshold", fdsProperties.getFrequencyThreshold());
            kieSession.setGlobal("blacklistedIpPattern", fdsProperties.getBlacklistedIpPattern());
            kieSession.setGlobal("nightAmountThreshold", fdsProperties.getNightAmountThreshold());
            kieSession.setGlobal("distributedReceiverThreshold", fdsProperties.getDistributedReceiverThreshold());
            kieSession.setGlobal("avgAmountMultiplier", fdsProperties.getAvgAmountMultiplier());
            kieSession.insert(request);
            kieSession.fireAllRules();
        } finally {
            kieSession.dispose();
        }

        // 6. 룰 검사 후 평균 송금액 갱신 (현재 거래를 다음 검사의 기준에 반영)
        redisUtil.updateAvgAmount(request.getSenderId(), request.getAmount());

        return response;
    }
}
