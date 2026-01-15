package com.teamA.async.admin.service;

import com.teamA.async.admin.ddb.RequestItemRepository;
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

        if (winners <= 0) throw new IllegalArgumentException("winners must be > 0");

        // ✅ 1) 이벤트 전체 RequestItem 조회 (이미 SUCCESS 찍혀도 덮어쓰기 가능)
        List<RequestItem> candidates = requestItemRepository.findByEvent(eventId);

        if (candidates.isEmpty()) {
            throw new IllegalStateException("no candidates");
        }

        // ✅ 2) 랜덤 셔플
        Collections.shuffle(candidates);

        // ✅ 3) 당첨/탈락
        int winCount = Math.min(winners, candidates.size());
        List<RequestItem> win = candidates.stream().limit(winCount).toList();
        List<RequestItem> lose = candidates.stream().skip(winCount).toList();

        // ✅ 4) 결과 반영 (DDB 덮어쓰기)
        resultWriter.markWinners(win);
        resultWriter.markLosers(lose);
    }
}
