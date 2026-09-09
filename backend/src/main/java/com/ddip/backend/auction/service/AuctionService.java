package com.ddip.backend.auction.service;

import com.ddip.backend.auction.repository.AuctionImageRepository;
import com.ddip.backend.auction.repository.AuctionRepository;
import com.ddip.backend.auction.repository.BidsRepository;
import com.ddip.backend.auction.repository.MyBidsRepository;
import com.ddip.backend.billing.service.PointService;
import com.ddip.backend.user.repository.UserRepository;
import com.ddip.backend.common.aop.DistributedLock;
import com.ddip.backend.auction.domain.Auction;
import com.ddip.backend.auction.domain.AuctionImage;
import com.ddip.backend.auction.domain.Bids;
import com.ddip.backend.auction.domain.MyBids;
import com.ddip.backend.admin.dto.auction.AdminAuctionSearchCondition;
import com.ddip.backend.auction.dto.auction.AuctionDetailResponseDto;
import com.ddip.backend.auction.dto.auction.AuctionRequestDto;
import com.ddip.backend.auction.dto.auction.AuctionResponseDto;
import com.ddip.backend.auction.dto.enums.AuctionStatus;
import com.ddip.backend.billing.dto.PointLedgerSource;
import com.ddip.backend.billing.dto.PointLedgerType;
import com.ddip.backend.common.es.document.AuctionDocument;
import com.ddip.backend.common.es.repository.AuctionElasticsearchRepository;
import com.ddip.backend.auction.event.AuctionEndEvent;
import com.ddip.backend.auction.event.AuctionEsEvent;
import com.ddip.backend.auction.validation.auction.AuctionDeniedException;
import com.ddip.backend.auction.validation.auction.AuctionNotFoundException;
import com.ddip.backend.auction.validation.auction.InvalidBidStepException;
import com.ddip.backend.user.validation.user.UserNotFoundException;
import com.ddip.backend.user.domain.User;
import com.ddip.backend.common.utils.AwsS3Util;
import com.ddip.backend.common.utils.S3UrlPrefixFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class AuctionService {

    private final AwsS3Util awsS3Util;
    private final PointService pointService;
    private final ApplicationEventPublisher publisher;
    private final AuctionImageService auctionImageService;

    private final UserRepository userRepository;
    private final BidsRepository bidsRepository;
    private final MyBidsRepository myBidsRepository;
    private final AuctionRepository auctionRepository;
    private final AuctionImageRepository auctionImageRepository;
    private final AuctionElasticsearchRepository auctionEsRepository;


    /**
     * 경매 생성
     */
    public AuctionResponseDto createAuction(List<MultipartFile> auctionFiles,
                                            Long userId, AuctionRequestDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        validateBidStep(dto.getStartPrice(), dto.getBidStep());

        Auction auction = Auction.from(user, dto);
        auctionRepository.save(auction);

        auctionImageService.uploadAuctionImagesAndSaveEntities(auction, auctionFiles, dto.getMainIndex());
        auctionImageService.syncAuctionThumbnailFromMainOrThrow(auction);

        // Es 인덱스 생성
        AuctionDocument auctionDocument = AuctionDocument.from(auction, auction.getMainImagKey());
        auctionEsRepository.save(auctionDocument);

        return AuctionResponseDto.from(auction);
    }

    /**
     * 경매 상세 조회
     */
    @Transactional(readOnly = true)
    public AuctionDetailResponseDto getAuction(Long auctionId) {
        Auction auction = auctionRepository.findDetailById(auctionId)
                .orElseThrow(() -> new AuctionNotFoundException(auctionId));

        List<Bids> bids = bidsRepository.findByAuctionId(auction.getId());

        return AuctionDetailResponseDto.from(auction, bids);
    }

    /**
     * 모든 경매 조회
     */
    @Transactional(readOnly = true)
    public List<AuctionResponseDto> getAllAuctions() {
        return auctionRepository.findAllByOrderByIdDesc().stream()
                .map(AuctionResponseDto::from)
                .collect(Collectors.toList());
    }

    /**
     * 경매 삭제
     */
    public void deleteAuction(Long auctionId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionNotFoundException(auctionId));

        if(!auction.getSeller().equals(user)) {
            throw new AuctionDeniedException(auctionId, userId);
        }

        List<AuctionImage> auctionImages = auctionImageService.findImagesByAuctionId(auction.getId());

        auctionImageService.deleteProjectImages(auctionImages);

        User currentWinner = auction.getCurrentWinner();

        if (currentWinner != null) {
            Long currentWinnerId = currentWinner.getId();
            pointService.changePoint(currentWinnerId, +auction.getCurrentPrice(),
                    PointLedgerType.REFUND, PointLedgerSource.AUCTION,
                    auction.getId(), "경매 삭제 입찰자 환불");
        }

        auctionRepository.delete(auction);
        auctionEsRepository.deleteById(auctionId);
    }


    /**
     * 경메 종료
     */
    @Scheduled(cron = "0 * * * * *")
    public void endExpiredAuction() {
        LocalDateTime now = LocalDateTime.now();

        List<Auction> auctions = auctionRepository.findEndAuctions(now, 100);

        for (Auction auction : auctions) {

            if (auction.getAuctionStatus() != AuctionStatus.RUNNING) {
                continue;
            }

            // 낙찰자는 MyBids 의 LEADING 1건으로 결정
            Optional<MyBids> Users = myBidsRepository.findLeadingByAuctionId(auction.getId());

            if (Users.isPresent()) {
                MyBids leading = Users.get();
                User winner = leading.getUser();

                auction.updateWinner(winner);

                // 낙찰자 유저 제외 모든 유저 LOST 로 변경
                myBidsRepository.markWon(auction.getId(), winner.getId());
                myBidsRepository.markLostExceptWinner(auction.getId(), winner.getId());
            }

            pointService.changePoint(auction.getSeller().getId(), +auction.getCurrentPrice(),
                    PointLedgerType.CHARGE, PointLedgerSource.AUCTION,
                    auction.getId(), "경매 종료 판매자 입금");

            auction.updateAuctionStatus(AuctionStatus.ENDED);

            // after commit
            publisher.publishEvent(new AuctionEsEvent(auction.getId()));
            publisher.publishEvent(new AuctionEndEvent(auction.getId()));
        }
    }

    /**
     * admin 강제 낙찰
     */
    @DistributedLock(key = "'auction:' + #auctionId")
    public void forceEndAuction(Long auctionId) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionNotFoundException(auctionId));

        if (auction.getAuctionStatus() != AuctionStatus.RUNNING) {
            return;
        }

        Optional<MyBids> users = myBidsRepository.findLeadingByAuctionId(auction.getId());

        if (users.isPresent()) {
            MyBids leading = users.get();
            User winner = leading.getUser();

            auction.updateWinner(winner);

            myBidsRepository.markWon(auctionId, winner.getId());
            myBidsRepository.markLostExceptWinner(auctionId, winner.getId());

            pointService.changePoint(auction.getSeller().getId(), + auction.getCurrentPrice(),
                    PointLedgerType.CHARGE, PointLedgerSource.AUCTION, auctionId,
                    "운영자 강제 종료 정산.");
        } else {
            auction.updateWinner(null);
        }

        auction.updateAuctionStatus(AuctionStatus.ENDED);

        // after commit
        publisher.publishEvent(new AuctionEsEvent(auction.getId()));
        publisher.publishEvent(new AuctionEndEvent(auction.getId()));
    }

    /**
     * admin 경매 강제 취소
     */
    public void cancelAuctionByAdmin(Long auctionId) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionNotFoundException(auctionId));

        if (auction.getAuctionStatus() != AuctionStatus.RUNNING) {
            return;
        }

        Optional<MyBids> user = myBidsRepository.findLeadingByAuctionId(auction.getId());

        if (user.isPresent()) {
            MyBids myBid = user.get();
            long refund = myBid.getLastBidPrice() == null ? 0 : myBid.getLastBidPrice();

            pointService.changePoint(myBid.getUser().getId(), + refund,
                    PointLedgerType.REFUND, PointLedgerSource.AUCTION, auctionId,
                    "경매 강제 취소 환불.");

            myBid.markCanceledBid();
        }

        auction.updateWinner(null);
        auction.updateCurrentWinner(null);
        auction.updateAuctionStatus(AuctionStatus.CANCELED);

        // after commit
        publisher.publishEvent(new AuctionEsEvent(auction.getId()));
    }

    /**
     * 판매자 조회
     */
    public List<Auction> getAuctionsBySeller(Long sellerId) {
        return auctionRepository.findAuctionsByUserId(sellerId);
    }

    /**
     * Auction 반환
     */
    public Auction getAuctionById(Long auctionId) {
        return auctionRepository.findById(auctionId).orElseThrow(() -> new AuctionNotFoundException(auctionId));
    }

    /**
     * Auction 페이징
     */
    @Transactional(readOnly = true)
    public Page<Auction> searchAuctionsForAdmin(AdminAuctionSearchCondition condition, Pageable pageable) {
        return auctionRepository.searchAuctionsForAdmin(condition, pageable);
    }


    /**
     * 최저 입찰가 검증
     */
    private void validateBidStep(Long startPrice, int bidStep) {
        if (startPrice == null || startPrice <= 0) {
            throw new InvalidBidStepException(bidStep);
        }
        if (bidStep <= 0) {
            throw new InvalidBidStepException(bidStep);
        }

        long required;
        if (startPrice < 1_000_000L) {
            required = percentCeil(startPrice, 5);
        } else {
            required = percentCeil(startPrice, 10);
        }

        if (bidStep < required) {
            throw new InvalidBidStepException(bidStep);
        }
    }

    private long percentCeil(long value, int percent) {
        return (value * percent + 99) / 100;
    }
}