package com.highpass.runspot.common;

import com.highpass.runspot.auth.domain.AgeGroup;
import com.highpass.runspot.auth.domain.Gender;
import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.auth.domain.UserRunningStats;
import com.highpass.runspot.auth.domain.dao.UserRepository;
import com.highpass.runspot.auth.domain.dao.UserRunningStatsRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.highpass.runspot.session.domain.GenderPolicy;
import com.highpass.runspot.session.domain.RunType;
import com.highpass.runspot.session.domain.Session;
import com.highpass.runspot.session.domain.SessionParticipant;
import com.highpass.runspot.session.domain.ParticipationStatus;
import com.highpass.runspot.session.domain.AttendanceStatus;
import com.highpass.runspot.session.domain.dao.SessionParticipantRepository;
import com.highpass.runspot.session.domain.dao.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final SessionParticipantRepository sessionParticipantRepository;
    private final UserRunningStatsRepository userRunningStatsRepository;
    private final PasswordEncoder passwordEncoder;

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
    private final Random random = new Random();

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (userRepository.existsByUsername("admin")) {
            log.info("Admin 계정이 이미 존재하여 더미 데이터 생성을 건너뜁니다.");
            return;
        }

        log.info("더미 데이터 생성을 시작합니다...");

        // 1. 유저 생성 (다양한 스펙)
        List<User> users = createUsers();

        // 2. 세션 및 참여자 생성 (을지로 위주)
        createSessions(users);

        log.info("더미 데이터 생성이 완료되었습니다.");
    }

    private List<User> createUsers() {
        List<User> users = new ArrayList<>();
        String password = passwordEncoder.encode("12341234");

        // Admin User
        User admin = User.builder()
                .username("admin")
                .password(password)
                .name("관리자")
                .ageGroup(AgeGroup.THIRTIES)
                .gender(Gender.MALE)
                .mannerTemp(BigDecimal.valueOf(99.9))
                .weeklyRunningGoal(7)
                .pacePreferenceSec(300) // 5분 페이스
                .build();
        users.add(userRepository.save(admin));
        userRunningStatsRepository.save(UserRunningStats.builder().user(admin).build());

        // Normal Users (20명)
        for (int i = 1; i <= 20; i++) {
            Gender gender = (i % 2 == 0) ? Gender.FEMALE : Gender.MALE;

            // 나이대 다양화
            AgeGroup ageGroup;
            int ageRand = random.nextInt(100);
            if (ageRand < 30) ageGroup = AgeGroup.TWENTIES;      // 30%
            else if (ageRand < 60) ageGroup = AgeGroup.THIRTIES; // 30%
            else if (ageRand < 85) ageGroup = AgeGroup.FORTIES;  // 25%
            else ageGroup = AgeGroup.FIFTIES;                    // 15%

            // 매너온도 (30.0 ~ 45.0)
            double temp = 30.0 + (random.nextDouble() * 15.0);
            BigDecimal mannerTemp = BigDecimal.valueOf(temp).setScale(1, BigDecimal.ROUND_HALF_UP);

            // 페이스 (4분 ~ 8분)
            int pace = 240 + random.nextInt(241);

            User user = User.builder()
                    .username("user" + i)
                    .password(password)
                    .name("러너" + i)
                    .ageGroup(ageGroup)
                    .gender(gender)
                    .mannerTemp(mannerTemp)
                    .weeklyRunningGoal(random.nextInt(7) + 1)
                    .pacePreferenceSec(pace)
                    .build();

            users.add(userRepository.save(user));
            userRunningStatsRepository.save(UserRunningStats.builder().user(user).build());
        }
        return users;
    }

    private void createSessions(List<User> users) {
        // 을지로 주요 스팟 좌표
        // 1. 청계천 (을지로3가역 북쪽)
        // 2. 을지로 노가리 골목 (을지로3가역 인근)
        // 3. 세운상가 (종로3가~을지로4가 사이)
        // 4. 명동성당 (을지로 입구 쪽)
        double[][] safeZones = {
                {126.9925, 37.5685}, // 청계천 (장교교 인근)
                {126.9905, 37.5665}, // 을지로3가역 (노가리 골목)
                {126.9955, 37.5670}, // 세운상가 앞
                {126.9870, 37.5630}  // 명동성당 인근
        };

        String[] titles = {
                "퇴근 후 힙지로 러닝", "청계천 야간 5km", "을지로 골목 탐방 러닝",
                "세운상가 한바퀴", "명동성당 찍고 오기", "초보자 환영 천천히 뜁니다",
                "도심 속 시티런", "러닝 끝나고 만선호프?", "주말 아침 청계천", "직장인 스트레스 해소"
        };

        for (int i = 0; i < 25; i++) {
            User host = users.get(random.nextInt(users.size()));

            // 안전 구역 중 하나 선택 후 약간의 랜덤 오차
            double[] zone = safeZones[random.nextInt(safeZones.length)];
            double startX = zone[0] + (random.nextDouble() - 0.5) * 0.003;
            double startY = zone[1] + (random.nextDouble() - 0.5) * 0.003;

            Point location = geometryFactory.createPoint(new Coordinate(startX, startY));
            List<Session.RoutePoint> route = createDummyRoute(startX, startY);

            Session session = Session.builder()
                    .hostUser(host)
                    .title(titles[random.nextInt(titles.length)])
                    .runType(RunType.values()[random.nextInt(RunType.values().length)])
                    .locationName("을지로 일대")
                    .location(location)
                    .routePolyline(route)
                    .targetDistanceKm(BigDecimal.valueOf(3 + random.nextInt(7))) // 3~10km
                    .avgPaceSec(300 + random.nextInt(240)) // 5분~9분
                    .startAt(LocalDateTime.now().plusDays(random.nextInt(14)).plusHours(random.nextInt(12))) // 2주 내
                    .capacity(3 + random.nextInt(10)) // 3~12명
                    .genderPolicy(GenderPolicy.MIXED)
                    .build();

            sessionRepository.save(session);

            addParticipants(session, users, host);
        }
    }

    private List<Session.RoutePoint> createDummyRoute(double startX, double startY) {
        List<Session.RoutePoint> route = new ArrayList<>();
        double currentX = startX;
        double currentY = startY;

        route.add(new Session.RoutePoint(BigDecimal.valueOf(currentX), BigDecimal.valueOf(currentY)));

        // 5~10개의 점을 이어 경로 생성
        for (int i = 0; i < 5 + random.nextInt(6); i++) {
            currentX += (random.nextDouble() - 0.5) * 0.002; // 약 100m 이동
            currentY += (random.nextDouble() - 0.5) * 0.002;
            route.add(new Session.RoutePoint(BigDecimal.valueOf(currentX), BigDecimal.valueOf(currentY)));
        }
        return route;
    }

    private void addParticipants(Session session, List<User> users, User host) {
        int participantCount = random.nextInt(session.getCapacity()); // 0 ~ 정원 미만

        String[] messages = {
                "잘 부탁드립니다!", "열심히 뛰겠습니다", "초보인데 괜찮을까요?",
                "시간 맞춰 가겠습니다", "안녕하세요~", "반갑습니다!", "화이팅!"
        };

        for (int i = 0; i < participantCount; i++) {
            User user = users.get(random.nextInt(users.size()));
            if (user.getId().equals(host.getId())) continue;
            if (sessionParticipantRepository.existsBySessionAndUser(session, user)) continue;

            // 상태 랜덤 설정 (승인 70%, 요청중 20%, 거절 10%)
            ParticipationStatus status;
            int statusRand = random.nextInt(100);
            if (statusRand < 70) status = ParticipationStatus.APPROVED;
            else if (statusRand < 90) status = ParticipationStatus.REQUESTED;
            else status = ParticipationStatus.REJECTED;

            SessionParticipant participant = SessionParticipant.builder()
                    .session(session)
                    .user(user)
                    .status(status)
                    .attendanceStatus(AttendanceStatus.ABSENT) // 기본값
                    .messageToHost(messages[random.nextInt(messages.length)])
                    .build();

            sessionParticipantRepository.save(participant);
        }
    }
}
