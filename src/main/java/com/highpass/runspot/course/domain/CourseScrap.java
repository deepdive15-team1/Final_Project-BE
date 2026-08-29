package com.highpass.runspot.course.domain;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.common.domain.BaseTimeEntity;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "course_scraps",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_course_scraps_record_user",
                        columnNames = {"running_record_id", "user_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseScrap extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "running_record_id")
    private RunningRecord runningRecord;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    public static CourseScrap create(RunningRecord record, User user) {
        CourseScrap scrap = new CourseScrap();
        scrap.runningRecord = record;
        scrap.user = user;
        return scrap;
    }
}
