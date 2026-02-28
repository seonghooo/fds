package com.seongho.fds.service;

import com.seongho.fds.domain.TrustedReceiver;
import com.seongho.fds.dto.WhitelistRequest;
import com.seongho.fds.repository.TrustedReceiverRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WhitelistService {
    private final TrustedReceiverRepository trustedReceiverRepository;

    @Transactional
    public void registerWhitelist(WhitelistRequest request){
        if (trustedReceiverRepository.existsBySenderIdAndReceiverId(request.getSenderId(), request.getReceiverId())) {
            throw new IllegalArgumentException("이미 화이트리스트에 등록된 수취인입니다.");
        }

        TrustedReceiver receiver = TrustedReceiver.builder()
                .senderId(request.getSenderId())
                .receiverId(request.getReceiverId())
                .build();
        trustedReceiverRepository.save(receiver);
    }
}
