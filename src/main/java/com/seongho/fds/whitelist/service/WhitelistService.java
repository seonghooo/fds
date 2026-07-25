package com.seongho.fds.whitelist.service;

import com.seongho.fds.whitelist.domain.TrustedReceiver;
import com.seongho.fds.whitelist.dto.WhitelistRequest;
import com.seongho.fds.global.exception.DuplicateResourceException;
import com.seongho.fds.whitelist.repository.TrustedReceiverRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WhitelistService {
    private final TrustedReceiverRepository trustedReceiverRepository;

    @Transactional
    public void registerWhitelist(WhitelistRequest request){
        if (trustedReceiverRepository.existsBySenderIdAndReceiverId(request.getSenderId(), request.getReceiverId())) {
            throw new DuplicateResourceException("이미 화이트리스트에 등록된 수취인입니다.");
        }

        TrustedReceiver receiver = TrustedReceiver.builder()
                .senderId(request.getSenderId())
                .receiverId(request.getReceiverId())
                .build();
        trustedReceiverRepository.save(receiver);
    }

    public List<TrustedReceiver> getAll() {
        return trustedReceiverRepository.findAll();
    }

    @Transactional
    public void delete(Long id) {
        trustedReceiverRepository.deleteById(id);
    }
}
