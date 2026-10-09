package com.gwonsystem.backend.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
@Entity @Table(name="company_events") @Getter @NoArgsConstructor
public class CompanyEvent {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,length=80) private String title;
 @Column(nullable=false,length=20) private String category;
 @Column(nullable=false) private LocalDate startDate;
 @Column private LocalDate endDate;
 @Column(nullable=false,length=120) private String location;
 @Column(length=120) private String locationDetail;
 @Column(length=200) private String placeId;
 @Column(nullable=false,length=1000) private String description;
 public CompanyEvent(String title,String category,LocalDate startDate,LocalDate endDate,String location,String description) {
  this.title=title;this.category=category;this.startDate=startDate;this.endDate=endDate;this.location=location;this.description=description;
 }
 public void setPlaceDetails(String locationDetail,String placeId) { this.locationDetail=locationDetail;this.placeId=placeId; }
}
