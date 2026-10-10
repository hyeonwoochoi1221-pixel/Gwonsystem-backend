package com.gwonsystem.backend.service;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
@Service
public class GooglePlacesService {
 private final RestClient client; private final String key;
 @Value("${app.places.enabled:true}") private boolean enabled = true;
 public record Place(String id,String name,String address) {}
 public GooglePlacesService(RestClient.Builder builder,@Value("${google.places.api-key:}") String key) {
  this.key=key.trim(); var factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(5000);factory.setReadTimeout(15000);
  client=builder.baseUrl("https://places.googleapis.com/v1").requestFactory(factory).build();
 }
 public List<Place> search(String query) {
  if(!enabled)throw new com.gwonsystem.backend.exception.IntegrationDisabledException("장소 검색 기능이 비활성화되어 있습니다.");
  if(query==null||query.trim().length()<2||query.length()>120)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"검색어는 2~120자로 입력해 주세요.");
  if(key.isBlank())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Google 장소 검색 설정이 필요합니다. 장소를 직접 입력하거나 관리자에게 문의해 주세요.");
  try {
   var result=client.post().uri("/places:searchText").contentType(MediaType.APPLICATION_JSON).header("X-Goog-Api-Key",key).header("X-Goog-FieldMask","places.id,places.displayName,places.formattedAddress")
     .body(Map.of("textQuery",query.trim(),"languageCode","ko","regionCode","KR","pageSize",5)).retrieve().body(JsonNode.class);
   List<Place> items=new ArrayList<>();if(result!=null)for(var place:result.path("places"))items.add(new Place(place.path("id").asText(),place.path("displayName").path("text").asText(),place.path("formattedAddress").asText()));return items;
  }catch(RestClientException e){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Google 장소 검색에 실패했습니다. 잠시 후 다시 시도해 주세요.");}
 }
}
