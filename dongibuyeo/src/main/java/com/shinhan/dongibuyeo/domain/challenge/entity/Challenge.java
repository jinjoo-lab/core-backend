package com.shinhan.dongibuyeo.domain.challenge.entity;

import com.github.f4b6a3.ulid.UlidCreator;
import com.shinhan.dongibuyeo.domain.account.entity.Account;
import com.shinhan.dongibuyeo.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at is null")
public class Challenge extends BaseEntity {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id = UlidCreator.getMonotonicUlid().toUuid();

    @Enumerated(EnumType.STRING)
    private ChallengeType type;

    @OneToOne
    @JoinColumn(name = "account_id", unique = true)
    private Account account;

    private LocalDate startDate;
    private LocalDate endDate;

    private String title;
    private String description;

    private AtomicLong totalDeposit = new AtomicLong(0);

    private AtomicInteger participants = new AtomicInteger(0);

    @OneToMany(mappedBy = "challenge")
    private List<MemberChallenge> challengeMembers = new ArrayList<>();

    public void addMember(MemberChallenge memberChallenge) {
        challengeMembers.add(memberChallenge);
        totalDeposit.getAndAdd(memberChallenge.getDeposit());
        participants.incrementAndGet();
    }

    public void removeMember(MemberChallenge memberChallenge) {
        challengeMembers.remove(memberChallenge);
        totalDeposit.getAndAdd(-memberChallenge.getDeposit());
        participants.decrementAndGet();
    }

    public void updateTitle(String title) {
        this.title = title;
    }

    public void updateDescription(String description) {
        this.description = description;
    }

    public void updateStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public void updateEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public void updateChallengeType(ChallengeType type) {
        this.type = type;
    }
}
