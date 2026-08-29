package com.highpass.runspot.course.domain;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.common.domain.BaseTimeEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @Entity @Table(name="running_records") @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class RunningRecord extends BaseTimeEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id",nullable=false) private User user;
 @Column(nullable=false,length=100) private String title;
 @Column(name="recorded_at",nullable=false) private LocalDate recordedAt;
 @Column(name="distance_km",nullable=false,precision=7,scale=2) private BigDecimal distanceKm;
 @Column(name="avg_pace_sec",nullable=false) private Integer avgPaceSec;
 @Column(name="location_name",nullable=false,length=100) private String locationName;
 @Column(name="location_detail",length=200) private String locationDetail;
 @Column(name="map_thumbnail_key",length=500) private String mapThumbnailKey;
 @Column(name="route_polyline",nullable=false,columnDefinition="TEXT") private String routePolyline;
}
