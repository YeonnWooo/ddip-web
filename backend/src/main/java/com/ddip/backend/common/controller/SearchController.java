package com.ddip.backend.common.controller;

import com.ddip.backend.auction.dto.es.AuctionSearchResponse;
import com.ddip.backend.common.dto.es.SearchAutoCompleteResponse;
import com.ddip.backend.common.es.service.AuctionSearchService;
import com.ddip.backend.common.es.service.SearchAddOnService;
import com.ddip.backend.project.dto.es.ProjectSearchResponse;
import com.ddip.backend.common.es.service.ProjectSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/search")
public class SearchController {

    private final ProjectSearchService projectSearchService;
    private final AuctionSearchService auctionSearchService;
    private final SearchAddOnService findAutoCompleteSuggestionService;

    /**
     * 검색 자동완성
     */
    @GetMapping("/suggest")
    public ResponseEntity<List<SearchAutoCompleteResponse>> autoComplete(@RequestParam("keyword") String keyword) {
        return ResponseEntity.ok(findAutoCompleteSuggestionService.searchAutoComplete(keyword));
    }

    /**
     * 경매 검색
     */
    @GetMapping("/auction")
    public ResponseEntity<List<AuctionSearchResponse>> auctionSearch(@RequestParam("title") String title) {
        List<AuctionSearchResponse> auctions = auctionSearchService.searchAuctionsByKeyword(title);

        return ResponseEntity.ok(auctions);
    }

    /**
     * 경매 상세 검색
     */
    @GetMapping("/auction/filter")
    public ResponseEntity<Page<AuctionSearchResponse>> auctionSearchFilter(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {

       Page<AuctionSearchResponse> auctions = auctionSearchService.searchAuctionByFilter(title, endAt, page, size);

        return ResponseEntity.ok(auctions);
    }

    /**
     * 공동구매 검색
     */
    @GetMapping("/project")
    public ResponseEntity<List<ProjectSearchResponse>> projectSearch(@RequestParam("title") String title) {
        List<ProjectSearchResponse> project = projectSearchService.searchProjectByKeyword(title);

        return ResponseEntity.ok(project);
    }

    /**
     * 공동구매 상세 검색
     */
    @GetMapping("/project/filter")
    public ResponseEntity<Page<ProjectSearchResponse>> auctionSearchFilter(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDate endAt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {

        Page<ProjectSearchResponse> projects = projectSearchService.searchProjectByFilter(title, endAt, page, size);

        return ResponseEntity.ok(projects);
    }
}
