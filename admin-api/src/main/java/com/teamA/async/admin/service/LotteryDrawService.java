package com.teamA.async.admin.service;

import com.teamA.async.admin.ddb.RequestItemRepository;
import com.teamA.async.common.domain.enums.RequestStatus;
import com.teamA.async.common.domain.enums.ResultCode;
import com.teamA.async.common.domain.enums.UiResult;
import com.teamA.async.common.domain.model.RequestItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LotteryDrawService {

    private final RequestItemRepository requestItemRepository;
    private final RequestItemResultWriter resultWriter;

    public void draw(String eventId, int winners) {

        // 1️⃣ 응모 대상 조회
        List<RequestItem> all = requestItemRepository.findPendingByEvent(eventId);

        List<RequestItem> candidates = all.stream()
                .filter(r -> r.getStatus() == RequestStatus.QUEUED
                        || r.getStatus() == RequestStatus.PROCESSING)
                .toList();

        // 2️⃣ 랜덤 셔플
        Collections.shuffle(candidates);

        // 3️⃣ 당첨 / 탈락 분리
        List<RequestItem> win = candidates.stream().limit(winners).toList();
        List<RequestItem> lose = candidates.stream().skip(winners).toList();

        // 4️⃣ 결과 반영
        resultWriter.markWinners(win);
        resultWriter.markLosers(lose);
    }
}
