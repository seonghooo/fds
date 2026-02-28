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

        TrustedReceiver receiver = TrustedReceiver.builder()
                .senderId(request.getSenderId())
                .receiverId(request.getReceiverId())
                .build();
        trustedReceiverRepository.save(receiver);
    }
}
