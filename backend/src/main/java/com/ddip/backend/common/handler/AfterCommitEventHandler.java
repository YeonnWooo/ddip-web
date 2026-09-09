package com.ddip.backend.common.handler;

import com.ddip.backend.auction.domain.Bids;
import com.ddip.backend.auction.dto.auction.AuctionEndedEventDto;
import com.ddip.backend.auction.domain.Auction;
import com.ddip.backend.auction.dto.auction.AuctionResponseDto;
import com.ddip.backend.auction.dto.auction.AuctionUpdateEventDto;
import com.ddip.backend.auction.dto.bids.BidsResponseDto;
import com.ddip.backend.auction.event.AuctionUpdateEvent;
import com.ddip.backend.auction.service.AuctionService;
import com.ddip.backend.auction.service.BidsService;
import com.ddip.backend.common.es.document.AuctionDocument;
import com.ddip.backend.common.es.repository.AuctionElasticsearchRepository;
import com.ddip.backend.project.domain.Project;
import com.ddip.backend.common.es.document.ProjectDocument;
import com.ddip.backend.common.es.repository.ProjectElasticsearchRepository;
import com.ddip.backend.auction.event.AuctionEndEvent;
import com.ddip.backend.auction.event.AuctionEsEvent;
import com.ddip.backend.project.event.ProjectEsEvent;
import com.ddip.backend.project.exception.project.ProjectNotFoundException;
import com.ddip.backend.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AfterCommitEventHandler {

    private final BidsService bidsService;
    private final AuctionService auctionService;
    private final ProjectRepository projectRepository;
    private final ProjectElasticsearchRepository projectElasticsearchRepository;
    private final AuctionElasticsearchRepository auctionElasticSearchRepository;

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void auctionDocumentHandler(AuctionEsEvent event) {

        Auction auction = auctionService.getAuctionById(event.auctionId());

        AuctionDocument auctionDocument = AuctionDocument.from(auction, auction.getMainImagKey());

        log.info("auction: {}, Es Document Title: {}", auction.getTitle(), auctionDocument.getTitle());

        auctionElasticSearchRepository.save(auctionDocument);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void projectDocumentHandler(ProjectEsEvent event) {
        Project project = projectRepository.findById(event.projectId())
                .orElseThrow(() -> new ProjectNotFoundException(event.projectId()));

        ProjectDocument projectDocument = ProjectDocument.from(project, project.getThumbnailUrl());

        log.info("project: {}, Es Document Title: {}", project.getTitle(), projectDocument.getTitle());

        projectElasticsearchRepository.save(projectDocument);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void auctionEventHandler(AuctionEndEvent event) {
        Auction auction = auctionService.getAuctionById(event.auctionId());

        AuctionEndedEventDto auctionEndedEventDto = AuctionEndedEventDto.from(auction);

        messagingTemplate.convertAndSend("/topic/auction/" + auction.getId(), auctionEndedEventDto);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void auctionUpdateEventHandler(AuctionUpdateEvent event) {

        Auction auction = auctionService.getAuctionById(event.auctionId());

        Bids bids = bidsService.getBidsById(event.BidId());

        AuctionResponseDto auctionResponseDto = AuctionResponseDto.from(auction);

        AuctionUpdateEventDto auctionUpdateEventDto = AuctionUpdateEventDto.from(auction, bids);

        messagingTemplate.convertAndSend("/topic/auction/" + auction.getId(), auctionUpdateEventDto);
        log.info("Successfully send: {}, {}", auctionResponseDto.getAuctionId(), auctionUpdateEventDto.getBidsResponseDto().getId());

        messagingTemplate.convertAndSend("/topic/auction/list", auctionResponseDto);
        log.info("Successfully send home page: {}", auctionResponseDto.getAuctionId());
    }
}
