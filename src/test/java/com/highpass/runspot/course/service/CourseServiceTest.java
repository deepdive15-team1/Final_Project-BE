package com.highpass.runspot.course.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.auth.domain.dao.UserRepository;
import com.highpass.runspot.community.exception.CommunityErrorCode;
import com.highpass.runspot.community.exception.CommunityException;
import com.highpass.runspot.course.domain.CourseScrap;
import com.highpass.runspot.course.domain.RunningRecord;
import com.highpass.runspot.course.repository.CourseScrapRepository;
import com.highpass.runspot.course.repository.RunningRecordRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {
    @Mock RunningRecordRepository records;
    @Mock CourseScrapRepository scraps;
    @Mock UserRepository users;
    @Mock RunningRecord record;
    @InjectMocks CourseService service;

    @Test
    void 이미_저장한_코스는_중복_저장할_수_없다() {
        when(scraps.existsByRunningRecordIdAndUserId(10L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.scrap(1L, 10L))
                .isInstanceOf(CommunityException.class)
                .hasFieldOrPropertyWithValue("exceptionType", CommunityErrorCode.ALREADY_COURSE_SCRAPPED);
    }

    @Test
    void 존재하지_않는_러닝_기록은_저장할_수_없다() {
        when(records.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.scrap(1L, 10L))
                .isInstanceOf(CommunityException.class)
                .hasFieldOrPropertyWithValue("exceptionType", CommunityErrorCode.RUNNING_RECORD_NOT_FOUND);
    }

    @Test
    void 러닝_코스를_저장한다() {
        User user = User.builder().id(1L).build();
        when(records.findById(10L)).thenReturn(Optional.of(record));
        when(users.findById(1L)).thenReturn(Optional.of(user));

        service.scrap(1L, 10L);

        verify(scraps).saveAndFlush(org.mockito.ArgumentMatchers.any(CourseScrap.class));
    }
}
