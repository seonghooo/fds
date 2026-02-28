package com.seongho.fds.service;

import com.seongho.fds.dto.TransactionRequest;
import com.seongho.fds.dto.TransactionResponse;
import com.seongho.fds.repository.TrustedReceiverRepository;
import com.seongho.fds.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.hibernate.Transaction;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Setter
// 탐지 서비스
public class FdsService {

    private final KieContainer kieContainer;

    private final RedisUtil redisUtil;

    private final TrustedReceiverRepository trustedReceiverRepository;

    public TransactionResponse validateTransaction(TransactionRequest request){

        // 1. 화이트리스트 여부 확인(DB조회)
        boolean isTrusted = trustedReceiverRepository.existsBySenderIdAndReceiverId(
                request.getSenderId(),
                request.getReceiverId()
        );

        // 2. 요청 객체에 결과 저장
        request.setTrusted(isTrusted);

        String redisKey = "tx_count:" + request.getSenderId();
        long count = redisUtil.incrementAndGet(redisKey, 1);
        TransactionResponse response = new TransactionResponse();

        //요청 객체에 빈도 정보 주입
        request.setRecentTransactionCount(count);

        // 1. KieSession 생성 (룰 실행을 위한 세션)
        KieSession kieSession = kieContainer.newKieSession();

        try{
            // 2. 엔진에 데이터 주입 (Global 변수를 활용해 결과값을 담아옴)
            kieSession.setGlobal("response", response);
            kieSession.insert(request);

            // 3. 모든 룰 실행
            kieSession.fireAllRules();
        } finally{
            // 4. 세션 종료 (메모리 누수 방지 필수)
            kieSession.dispose();
        }

        return response;
    }
}
