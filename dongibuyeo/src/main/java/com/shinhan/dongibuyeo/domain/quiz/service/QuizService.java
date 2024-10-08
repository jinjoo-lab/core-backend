package com.shinhan.dongibuyeo.domain.quiz.service;

import com.shinhan.dongibuyeo.domain.member.dto.response.MemberResponse;
import com.shinhan.dongibuyeo.domain.member.entity.Member;
import com.shinhan.dongibuyeo.domain.member.service.MemberService;
import com.shinhan.dongibuyeo.domain.quiz.dto.request.QuizMakeRequest;
import com.shinhan.dongibuyeo.domain.quiz.dto.request.QuizSolveRequest;
import com.shinhan.dongibuyeo.domain.quiz.dto.response.QuizResponse;
import com.shinhan.dongibuyeo.domain.quiz.dto.response.QuizSolveResponse;
import com.shinhan.dongibuyeo.domain.quiz.dto.response.QuizTotalResponse;
import com.shinhan.dongibuyeo.domain.quiz.entity.Quiz;
import com.shinhan.dongibuyeo.domain.quiz.entity.QuizMember;
import com.shinhan.dongibuyeo.domain.quiz.mapper.QuizMapper;
import com.shinhan.dongibuyeo.domain.quiz.repository.QuizMemberRepository;
import com.shinhan.dongibuyeo.domain.quiz.repository.QuizRepository;
import com.shinhan.dongibuyeo.global.slack.SlackComponent;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class QuizService {
    private static final Logger log = LoggerFactory.getLogger(QuizService.class);
    private final QuizMemberRepository quizMemberRepository;
    private QuizRepository quizRepository;
    private MemberService memberService;
    private QuizMapper quizMapper;
    private SlackComponent slackComponent;
    private HashMap<String,String> slackWinners = new HashMap<>();

    public QuizService(QuizRepository quizRepository, MemberService memberService, QuizMapper quizMapper, QuizMemberRepository quizMemberRepository, SlackComponent slackComponent) {
        this.quizRepository = quizRepository;
        this.memberService = memberService;
        this.quizMapper = quizMapper;
        this.quizMemberRepository = quizMemberRepository;
        this.slackComponent = slackComponent;
    }

    @Transactional
    public QuizResponse makeQuiz(QuizMakeRequest request) {
        Quiz quiz = quizRepository.save(quizMapper.toQuizEntity(request));
        return quizMapper.toQuizResponse(quiz);
    }

    @Transactional
    public QuizResponse getRandomQuiz(UUID memberId) {
        Member member = memberService.getMemberById(memberId);
        Quiz quiz = quizRepository.findAll(getRandomIndex()).stream().findFirst().orElseThrow(EntityNotFoundException::new);
        return quizMapper.toQuizResponse(quiz);
    }

    @Transactional
    public Pageable getRandomIndex() {
        long count = quizRepository.count();
        int idx =  new Random().nextInt((int) count);
        return PageRequest.of(idx,1);
    }

    @Transactional
    public QuizSolveResponse solveQuiz(QuizSolveRequest request) {
        Quiz quiz = getQuizById(request.getQuizId());
        log.info(quiz.getDescription());
        Member member = memberService.getMemberById(request.getMemberId());

        QuizMember quizMember = new QuizMember(member, quiz);
        member.getQuizMembers().add(quizMember);

        return quizMapper.toQuizSolveResponse(quizMember);
    }

    private Quiz getQuizById(UUID quizId) {
        return quizRepository.findById(quizId).orElseThrow(EntityNotFoundException::new);
    }

    @Transactional
    public Boolean alreadyToday(UUID memberId) {
        LocalDateTime now = LocalDateTime.now();
        return quizMemberRepository.existsByMemberAndDate(memberId,now.getYear(),now.getMonthValue(),now.getDayOfMonth());
    }


    @Transactional
    public List<QuizSolveResponse> getMemberDateSolvedList(UUID memberId, Integer year, Integer month) {
        List<QuizMember> solvedList = quizMemberRepository.findAllByMemberAndDate(year,month,memberId);
        return solvedList.stream().map(x -> quizMapper.toQuizSolveResponse(x)).toList();
    }

    @Transactional
    public List<MemberResponse> getWinnerOfMonth(int year, int month) {
        List<QuizMember> solvedList = quizMemberRepository.findWinnerByYearAndMonth(year,month);
        Collections.shuffle(solvedList);

        HashMap<UUID, Member> winners = new HashMap<>();
        slackWinners.put("EMAIL","NAME");

        for(QuizMember quizMember : solvedList) {
            winners.put(quizMember.getMember().getId(), quizMember.getMember());
            slackWinners.put(quizMember.getMember().getEmail(), quizMember.getMember().getName());

            if(winners.size() >= 42)
                break;
        }

        slackComponent.sendSlackMessage("QUIZ CHALLENGE : "+year+"년 "+month+"월 ",slackWinners);

        return quizMapper.toWinnerResponse(winners);
    }

    @Transactional
    public QuizTotalResponse getQuizTotal(UUID memberId, Integer year, Integer month) {
        List<QuizMember> solvedList = quizMemberRepository.findWinnerByYearAndMonth(year,month);
        List<QuizMember> myList = solvedList.stream().filter(x -> x.getMember().getId().equals(memberId)).toList();
        return new QuizTotalResponse(solvedList.size(), myList.size());
    }



}
