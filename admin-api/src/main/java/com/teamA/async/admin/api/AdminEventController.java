package com.teamA.async.admin.api;

import com.teamA.async.admin.api.dto.AdminEventDtos;
import com.teamA.async.admin.domain.EventItem;
import com.teamA.async.admin.service.AdminEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import static com.teamA.async.admin.api.dto.AdminEventDtos.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/events")
public class AdminEventController {

    private final AdminEventService adminEventService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateEventResponse create(@RequestBody CreateEventRequest req) {
        String eventId = adminEventService.createDraft(
                req.getTitle(),
                req.getType(),
                req.getCapacityTotal(),
                req.getOpenAt(),
                req.getCloseAt()
        );
        return new CreateEventResponse(eventId, com.teamA.async.admin.domain.EventStatus.DRAFT);
    }

    @GetMapping("/{id}")
    public GetEventResponse get(@PathVariable("id") String id) {
        EventItem item = adminEventService.getOrThrow(id);
        return new GetEventResponse(
                item.getEventId(),
                item.getTitle(),
                item.getType(),
                item.getStatus(),
                item.getCapacityTotal(),
                item.getOpenAt(),
                item.getCloseAt(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }

    @PatchMapping("/{id}/open")
    public EventStatusResponse open(@PathVariable("id") String id) {
        return new EventStatusResponse(id, adminEventService.open(id));
    }

    @PatchMapping("/{id}/close")
    public EventStatusResponse close(@PathVariable("id") String id) {
        return new EventStatusResponse(id, adminEventService.close(id));
    }
}
