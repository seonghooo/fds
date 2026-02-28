package com.seongho.fds.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
        uniqueConstraints = @UniqueConstraint(name = "uq_trusted_receiver", columnNames = {"sender_id", "receiver_id"}),
        indexes = @Index(name = "idx_trusted_receiver_sender_id", columnList = "sender_id")
)
public class TrustedReceiver {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sender_id")
    private String senderId;

    @Column(name = "receiver_id")
    private String receiverId;
}
