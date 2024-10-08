package com.shinhan.dongibuyeo.domain.challenge.service;

import com.shinhan.dongibuyeo.domain.account.dto.request.TransactionHistoryRequest;
import com.shinhan.dongibuyeo.domain.account.dto.response.TransactionHistory;
import com.shinhan.dongibuyeo.domain.challenge.entity.*;
import com.shinhan.dongibuyeo.domain.challenge.repository.DailyScoreRepository;
import com.shinhan.dongibuyeo.domain.challenge.score.scheduler.FeverTimeInfo;
import com.shinhan.dongibuyeo.domain.consume.dto.request.ConsumptionRequest;
import com.shinhan.dongibuyeo.domain.consume.service.ConsumeService;
import com.shinhan.dongibuyeo.domain.member.entity.Member;
import com.shinhan.dongibuyeo.global.entity.TransferType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Slf4j
public class DailyScoreService {

    private final DailyScoreRepository dailyScoreRepository;
    private final MemberChallengeService memberChallengeService;
    private final ConsumeService consumeService;

    public DailyScoreService(DailyScoreRepository dailyScoreRepository, MemberChallengeService memberChallengeService, ConsumeService consumeService) {
        this.dailyScoreRepository = dailyScoreRepository;
        this.memberChallengeService = memberChallengeService;
        this.consumeService = consumeService;
    }

    @Transactional
    public DailyScore getOrCreateDailyScore(MemberChallenge memberChallenge, LocalDate date) {
        return dailyScoreRepository.findByMemberChallengeIdAndDate(memberChallenge.getId(), date)
                .orElseGet(() -> {
                    log.info("[getOrCreateDailyScore] Create DailyScore");
                    DailyScore newDailyScore = new DailyScore(date);
                    newDailyScore.updateMemberChallenge(memberChallenge);
                    newDailyScore.addScoreDetail(new ScoreDetail("DAILY_SCORE", 10, 10));
                    memberChallenge.addDailyScore(newDailyScore);
                    return newDailyScore;
                });
    }

    @Transactional
    public void rewardNonConsumptionDuringFeverTime(ChallengeType challengeType, TransferType transferType) {
        List<MemberChallenge> activeChallenges = memberChallengeService.findAllByChallengeTypeAndStatus(challengeType, ChallengeStatus.IN_PROGRESS);
        List<FeverTimeInfo> feverTimes = getFeverTimes(challengeType);

        for (FeverTimeInfo feverTime : feverTimes) {
            for (MemberChallenge challenge : activeChallenges) {
                if (!hasConsumptionDuring(challenge, feverTime.getStart(), feverTime.getEnd(), transferType)) {
                    DailyScore dailyScore = getOrCreateDailyScore(challenge, LocalDate.now());
                    String description = feverTime.getDescription();
                    int score = feverTime.getScore();
                    dailyScore.addScoreDetail(new ScoreDetail(description, score, dailyScore.getTotalScore() + score));
                    challenge.addDailyScore(dailyScore);
                }
            }
        }
    }

    private List<FeverTimeInfo> getFeverTimes(ChallengeType challengeType) {
        LocalDateTime now = LocalDateTime.now();
        return switch (challengeType) {
            case CONSUMPTION_COFFEE -> List.of(
                    new FeverTimeInfo(now.withHour(7).withMinute(0).withSecond(0),
                            now.withHour(10).withMinute(0).withSecond(0),
                            "[FEVER] 7AM-10AM", 2),
                    new FeverTimeInfo(now.withHour(11).withMinute(0).withSecond(0),
                            now.withHour(14).withMinute(0).withSecond(0),
                            "[FEVER] 11AM-2PM", 3)
            );
            case CONSUMPTION_DRINK -> {
                DayOfWeek today = now.getDayOfWeek();
                if (today == DayOfWeek.SATURDAY) {
                    yield List.of(
                            new FeverTimeInfo(now.minusDays(1).withHour(0).withMinute(0).withSecond(0),
                                    now.minusDays(1).withHour(23).withMinute(59).withSecond(59),
                                    "[FEVER] Friday", 5)
                    );
                } else if (today == DayOfWeek.SUNDAY) {
                    yield List.of(
                            new FeverTimeInfo(now.minusDays(1).withHour(0).withMinute(0).withSecond(0),
                                    now.minusDays(1).withHour(23).withMinute(59).withSecond(59),
                                    "[FEVER] Saturday", 5)
                    );
                } else {
                    yield List.of(); // 다른 요일에는 피버타임 없음
                }
            }
            case CONSUMPTION_DELIVERY -> List.of(
                    new FeverTimeInfo(now.minusDays(1).withHour(21).withMinute(0).withSecond(0),
                            now.withHour(2).withMinute(0).withSecond(0),
                            "[FEVER] 9PM-2AM", 5)
            );
            default -> throw new IllegalArgumentException("Invalid challenge type for fever time");
        };
    }

    private boolean hasConsumptionDuring(MemberChallenge challenge, LocalDateTime start, LocalDateTime end, TransferType transferType) {
        Member member = challenge.getMember();
        ConsumptionRequest request = new ConsumptionRequest(
                transferType,
                TransactionHistoryRequest.builder()
                        .memberId(member.getId())
                        .accountNo(member.getChallengeAccount().getAccountNo())
                        .startDate(start.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")))
                        .endDate(end.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")))
                        .build()
        );

        List<TransactionHistory> transactions = consumeService.getTypeHistory(request);

        return transactions.stream()
                .anyMatch(transaction -> {
                    LocalDateTime transactionDateTime = LocalDateTime.parse(
                            transaction.getTransactionDate() + transaction.getTransactionTime(),
                            DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                    );
                    return !transactionDateTime.isBefore(start) && !transactionDateTime.isAfter(end);
                });
    }

    @Transactional
    public void updateDailyScore(MemberChallenge memberChallenge, LocalDate date, String description, int score) {
        DailyScore dailyScore = getOrCreateDailyScore(memberChallenge, date);

        // 중복항목 추가 방지
        boolean descriptionExists = dailyScore.getScoreDetails().stream()
                .anyMatch(detail -> detail.getDescription().equals(description));

        if (descriptionExists) {
            log.info("Score detail for description '{}' already exists for date {}. Skipping.", description, date);
            return;
        }

        int currentScore = dailyScore.getTotalScore();
        ScoreDetail scoreDetail = new ScoreDetail(description, score, currentScore + score);
        dailyScore.addScoreDetail(scoreDetail);
        memberChallenge.addDailyScore(dailyScore);
    }
}
