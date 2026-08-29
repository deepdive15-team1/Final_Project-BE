package com.highpass.runspot.course.service;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.auth.domain.dao.UserRepository;
import com.highpass.runspot.community.exception.*;
import com.highpass.runspot.course.domain.*;
import com.highpass.runspot.course.dto.RunningRecordResponse;
import com.highpass.runspot.course.repository.*;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseService {
    private final RunningRecordRepository records;
    private final CourseScrapRepository scraps;
    private final UserRepository users;

    public List<RunningRecordResponse> myRecords(Long userId) {
        return records.findByUserIdOrderByRecordedAtDescIdDesc(userId).stream()
                .map(RunningRecordResponse::from)
                .toList();
    }

    @Transactional
    public void scrap(Long userId, Long recordId) {
        if (scraps.existsByRunningRecordIdAndUserId(recordId, userId))
            throw e(CommunityErrorCode.ALREADY_COURSE_SCRAPPED);
        RunningRecord record =
                records.findById(recordId)
                        .orElseThrow(() -> e(CommunityErrorCode.RUNNING_RECORD_NOT_FOUND));
        User user = users.findById(userId).orElseThrow(() -> e(CommunityErrorCode.USER_NOT_FOUND));
        try {
            scraps.saveAndFlush(CourseScrap.create(record, user));
        } catch (DataIntegrityViolationException ex) {
            throw e(CommunityErrorCode.ALREADY_COURSE_SCRAPPED);
        }
    }

    public List<RunningRecordResponse> myCourses(Long userId) {
        return scraps.findByUserIdOrderByIdDesc(userId).stream()
                .map(CourseScrap::getRunningRecord)
                .map(RunningRecordResponse::from)
                .toList();
    }

    private CommunityException e(CommunityErrorCode code) {
        return new CommunityException(code);
    }
}
