package com.gwonsystem.backend.controller;
import com.gwonsystem.backend.entity.CompanyEvent;
import com.gwonsystem.backend.repository.CompanyEventRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.net.URI;
import java.util.List;
@RestController @RequestMapping("/api/company-events") @RequiredArgsConstructor
public class CompanyEventController {
 private final CompanyEventRepository events;
 private final com.gwonsystem.backend.service.GooglePlacesService places;
 public record Input(@NotBlank @Size(max=80) String title,
  @NotBlank @Pattern(regexp="회의|세미나|교육|행사|기타") String category,
  @NotNull LocalDate startDate,LocalDate endDate,
  @NotNull @Size(max=120) String location,@NotNull @Size(max=1000) String description, @Size(max=120) String locationDetail, @Size(max=200) String placeId) {}
 @GetMapping public List<CompanyEvent> list() { return events.findAllByOrderByStartDateDescIdDesc(); }
 @PostMapping @PreAuthorize("hasRole('SUPERVISOR')")
 public ResponseEntity<CompanyEvent> create(@Valid @RequestBody Input input) {
  if(input.endDate()!=null && input.endDate().isBefore(input.startDate())) throw new IllegalArgumentException("종료일은 시작일 이후여야 합니다.");
  var event=new CompanyEvent(input.title().trim(),input.category(),input.startDate(),input.endDate(),input.location().trim(),input.description().trim());
  event.setPlaceDetails(input.locationDetail()==null?null:input.locationDetail().trim(),input.placeId());
  var saved=events.save(event);
  return ResponseEntity.created(URI.create("/api/company-events/"+saved.getId())).body(saved);
 }
 @GetMapping("/places") @PreAuthorize("hasRole('SUPERVISOR')")
 public List<com.gwonsystem.backend.service.GooglePlacesService.Place> search(@RequestParam String query) { return places.search(query); }
 @ExceptionHandler(ResponseStatusException.class)
 public ResponseEntity<java.util.Map<String,String>> statusError(ResponseStatusException error) { return ResponseEntity.status(error.getStatusCode()).body(java.util.Map.of("message",error.getReason()==null?"요청을 처리하지 못했습니다.":error.getReason())); }
}
