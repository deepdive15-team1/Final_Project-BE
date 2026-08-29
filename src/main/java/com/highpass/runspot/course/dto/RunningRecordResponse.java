package com.highpass.runspot.course.dto;
import com.highpass.runspot.course.domain.RunningRecord;
import java.math.BigDecimal;
import java.time.LocalDate;
public record RunningRecordResponse(Long runningRecordId,String title,LocalDate recordedAt,BigDecimal distanceKm,String avgPace,String locationName,String locationDetail,String mapThumbnailKey,String routePolyline){public static RunningRecordResponse from(RunningRecord r){int sec=r.getAvgPaceSec();return new RunningRecordResponse(r.getId(),r.getTitle(),r.getRecordedAt(),r.getDistanceKm(),String.format("%d:%02d/km",sec/60,sec%60),r.getLocationName(),r.getLocationDetail(),r.getMapThumbnailKey(),r.getRoutePolyline());}}
