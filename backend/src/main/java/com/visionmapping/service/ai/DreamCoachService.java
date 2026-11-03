package com.visionmapping.service.ai;

import com.visionmapping.dto.request.AiChatRequest;
import com.visionmapping.dto.request.AiRatingRequest;
import com.visionmapping.dto.response.AiChatTurnResponse;
import com.visionmapping.dto.response.AiConversationResponse;
import com.visionmapping.dto.response.AiMessageResponse;
import com.visionmapping.entity.AiConversation;
import com.visionmapping.entity.AiMessage;
import com.visionmapping.entity.AiSettings;
import com.visionmapping.entity.AiUsageLog;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.enums.AiMessageRole;
import com.visionmapping.entity.enums.AiOutcome;
import com.visionmapping.entity.enums.AiSafetyFlag;
import com.visionmapping.exception.AiUnavailableException;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.repository.AiConversationRepository;
import com.visionmapping.repository.AiMessageRepository;
import com.visionmapping.repository.AiUsageLogRepository;
import com.visionmapping.service.AiSettingsService;
import com.visionmapping.service.support.EntityLookup;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * V9 Phase 3: the Dream Coach chat. Each message goes through the safety layer
 * (consent, budget, daily limit, crisis check), then Claude, then is logged
 * (AI-NFR-14) and saved to the participant's history (AI-FR-25). Nothing is
 * saved when Claude cannot be reached, so a failed send can simply be retried.
 * Phase 4 adds the page's context (AI-FR-01) and streaming (AI-NFR-01).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DreamCoachService {

    static final String FEATURE = "DREAM_COACH_CHAT";
    /** Earlier messages sent with each request; older ones stay in the history only. */
    static final int HISTORY_TURNS = 20;
    private static final BigDecimal ALERT_SHARE = new BigDecimal("0.80");

    private final EntityLookup lookup;
    private final AiAccessService accessService;
    private final AiSettingsService aiSettingsService;
    private final AiSafetyAlertService alertService;
    private final AiConversationRepository conversationRepository;
    private final AiMessageRepository messageRepository;
    private final AiUsageLogRepository usageLogRepository;
    private final ClaudeClient claudeClient;
    private final AiContextBuilder contextBuilder;
    private final TransactionTemplate transactions;

    @Transactional(readOnly = true)
    public List<AiConversationResponse> conversations() {
        return conversationRepository.findByUser_IdOrderByUpdatedAtDesc(lookup.userId()).stream()
                .map(conversation -> new AiConversationResponse(conversation.getId(), conversation.getTitle(),
                        conversation.getUpdatedAt(), null))
                .toList();
    }

    @Transactional(readOnly = true)
    public AiConversationResponse conversation(Long id) {
        AiConversation conversation = owned(id);
        return new AiConversationResponse(conversation.getId(), conversation.getTitle(), conversation.getUpdatedAt(),
                messageRepository.findByConversation_IdOrderByIdAsc(id).stream().map(DreamCoachService::toResponse).toList());
    }

    /** AI-FR-25: a participant may delete a conversation; the usage log keeps no text, so it stays. */
    @Transactional
    public void delete(Long id) {
        conversationRepository.delete(owned(id));
    }

    /** Clears the whole Dream Coach history of the signed-in person; nothing else is touched. */
    @Transactional
    public void deleteAll() {
        conversationRepository.deleteAll(conversationRepository.findByUser_IdOrderByUpdatedAtDesc(lookup.userId()));
    }

    @Transactional
    public AiMessageResponse rate(Long messageId, AiRatingRequest request) {
        AiMessage message = messageRepository.findById(messageId)
                .filter(found -> found.getUser().getId().equals(lookup.userId()))
                .orElseThrow(() -> new ResourceNotFoundException("Message not found: " + messageId));
        AiUsageLog usage = message.getUsageLog();
        if (message.getRole() != AiMessageRole.ASSISTANT || usage == null) {
            throw new BusinessRuleException("Only a Dream Coach reply can be rated.");
        }
        usage.setRating(request.rating() == 0 ? null : request.rating());
        usage.setRatingComment(request.rating() == 0 || request.comment() == null || request.comment().isBlank()
                ? null : request.comment().trim());
        return toResponse(message);
    }

    /**
     * Everything a reply needs, gathered on the request thread: the checks
     * (consent, budget, daily limit), the conversation and the context. A
     * streamed reply then runs on another thread without the security context.
     */
    public record Prepared(AppUser user, String apiKey, String model, BigDecimal budget, String helpLines,
                           Long conversationId, String text, String language, String system,
                           List<ClaudeClient.Turn> turns, List<String> sources, boolean crisis) {
    }

    @Transactional(readOnly = true)
    public Prepared prepare(AiChatRequest request) {
        AppUser user = lookup.currentUser();
        String apiKey = accessService.requireAvailable(user);
        String language = request.language() == null ? "en" : request.language();
        String text = request.message().trim();
        AiSettings settings = aiSettingsService.current();
        Long conversationId = request.conversationId() == null ? null : owned(request.conversationId()).getId();
        if (AiSafety.looksLikeCrisis(text)) {
            return new Prepared(user, apiKey, settings.getModel(), settings.getMonthlyBudgetUsd(), settings.getHelpLines(),
                    conversationId, text, language, null, List.of(), List.of(), true);
        }
        AiContextBuilder.AiContext context = contextBuilder.build(user,
                new AiContextBuilder.Focus(request.page(), request.dreamId(), request.moduleNumber()), language);
        List<ClaudeClient.Turn> turns = new ArrayList<>(conversationId == null ? List.of() : history(conversationId));
        turns.add(new ClaudeClient.Turn(true, text));
        String system = CoachingGuidelines.system(language, firstName(user.getFullName())) + "\n\n" + context.text();
        return new Prepared(user, apiKey, settings.getModel(), settings.getMonthlyBudgetUsd(), settings.getHelpLines(),
                conversationId, text, language, system, List.copyOf(turns), context.sources(), false);
    }

    public AiChatTurnResponse send(AiChatRequest request) {
        Prepared prepared = prepare(request);
        if (prepared.crisis()) {
            return crisisTurn(prepared);
        }
        return complete(prepared, call(prepared, null));
    }

    /** AI-NFR-01: like send, but passes each piece of the reply to onText as it arrives. */
    public AiChatTurnResponse stream(Prepared prepared, Consumer<String> onText) {
        if (prepared.crisis()) {
            return crisisTurn(prepared);
        }
        // Holds back the start of the reply until it is clear it is not the crisis marker.
        MarkerFilter filter = new MarkerFilter(onText);
        ClaudeClient.Reply reply = call(prepared, filter);
        filter.finish();
        return complete(prepared, reply);
    }

    private ClaudeClient.Reply call(Prepared prepared, Consumer<String> onText) {
        try {
            return onText == null
                    ? claudeClient.send(prepared.apiKey(), prepared.model(), prepared.system(), prepared.turns())
                    : claudeClient.stream(prepared.apiKey(), prepared.model(), prepared.system(), prepared.turns(), onText);
        } catch (AiUnavailableException exception) {
            usageLogRepository.save(log(prepared.user(), prepared.model(), 0, 0, AiOutcome.ERROR));
            throw exception;
        }
    }

    private AiChatTurnResponse crisisTurn(Prepared prepared) {
        return transactions.execute(status -> {
            boolean coachAlerted = alertService.raise(prepared.user().getId());
            AiUsageLog usage = usageLogRepository.save(log(prepared.user(), null, 0, 0, AiOutcome.CRISIS_HANDLED));
            return saveTurn(prepared.user(), prepared.conversationId(), prepared.text(),
                    AiSafety.crisisReply(prepared.language(), prepared.helpLines(), coachAlerted), AiSafetyFlag.CRISIS, usage, List.of());
        });
    }

    private AiChatTurnResponse complete(Prepared prepared, ClaudeClient.Reply reply) {
        String language = prepared.language();
        BigDecimal spentBefore = accessService.spendThisMonth();
        AiChatTurnResponse turn = transactions.execute(status -> {
            String content = reply.text();
            AiSafetyFlag flag = null;
            AiOutcome outcome = reply.refused() ? AiOutcome.REFUSED : reply.cutShort() ? AiOutcome.CUT_SHORT : AiOutcome.OK;
            if (reply.refused() || content.isBlank()) {
                content = "km".equals(language)
                        ? "ខ្ញុំមិនអាចជួយរឿងនោះបានទេ។ សូមសាកសួរតាមវិធីផ្សេង ឬនិយាយជាមួយគ្រូណែនាំរបស់អ្នក។"
                        : "I can't help with that one. Try asking another way, or talk to your coach.";
            } else if (AiSafety.markedCrisis(content)) {
                flag = AiSafetyFlag.CRISIS;
                boolean coachAlerted = alertService.raise(prepared.user().getId());
                content = AiSafety.withoutMarker(content) + "\n\n" + AiSafety.crisisReply(language, prepared.helpLines(), coachAlerted);
            }
            AiUsageLog usage = usageLogRepository.save(log(prepared.user(), reply.model(), reply.inputTokens(), reply.outputTokens(), outcome));
            return saveTurn(prepared.user(), prepared.conversationId(), prepared.text(), content, flag, usage, prepared.sources());
        });
        warnNearBudget(spentBefore, prepared.budget());
        return turn;
    }

    /** Passes text through once it cannot be the start of {@link AiSafety#MARKER}; drops the marker itself. */
    private static final class MarkerFilter implements Consumer<String> {

        private final Consumer<String> target;
        private final StringBuilder held = new StringBuilder();
        private boolean decided;

        MarkerFilter(Consumer<String> target) {
            this.target = target;
        }

        @Override
        public void accept(String text) {
            if (decided) {
                target.accept(text);
                return;
            }
            held.append(text);
            String start = held.toString().stripLeading();
            if (start.length() < AiSafety.MARKER.length() && AiSafety.MARKER.startsWith(start)) {
                return;
            }
            decided = true;
            String rest = start.startsWith(AiSafety.MARKER) ? start.substring(AiSafety.MARKER.length()).stripLeading() : held.toString();
            if (!rest.isEmpty()) {
                target.accept(rest);
            }
        }

        void finish() {
            if (!decided && !held.toString().isBlank() && !AiSafety.MARKER.startsWith(held.toString().strip())) {
                target.accept(held.toString());
            }
        }
    }

    private AiChatTurnResponse saveTurn(AppUser user, Long conversationId, String text, String replyText,
                                        AiSafetyFlag flag, AiUsageLog usage, List<String> sources) {
        AiConversation conversation = conversationId == null
                ? conversationRepository.save(AiConversation.builder().user(user).title(title(text)).build())
                : conversationRepository.findById(conversationId).orElseThrow();
        AiMessage asked = messageRepository.save(AiMessage.builder()
                .user(user).conversation(conversation).role(AiMessageRole.USER).content(text).safetyFlag(flag).build());
        AiMessage answered = messageRepository.save(AiMessage.builder()
                .user(user).conversation(conversation).role(AiMessageRole.ASSISTANT).content(replyText)
                .safetyFlag(flag).usageLog(usage)
                .contextSources(sources.isEmpty() ? null : String.join(",", sources)).build());
        // Moves the conversation to the top of the list.
        conversation.setUpdatedAt(Instant.now());
        conversationRepository.saveAndFlush(conversation);
        return new AiChatTurnResponse(conversation.getId(), conversation.getTitle(), toResponse(asked), toResponse(answered));
    }

    private List<ClaudeClient.Turn> history(Long conversationId) {
        List<AiMessage> messages = messageRepository.findByConversation_IdOrderByIdAsc(conversationId);
        List<AiMessage> recent = messages.subList(Math.max(0, messages.size() - HISTORY_TURNS), messages.size());
        // The API expects the conversation to open with the participant.
        int start = !recent.isEmpty() && recent.get(0).getRole() == AiMessageRole.ASSISTANT ? 1 : 0;
        return recent.subList(start, recent.size()).stream()
                .map(message -> new ClaudeClient.Turn(message.getRole() == AiMessageRole.USER, message.getContent()))
                .toList();
    }

    private void warnNearBudget(BigDecimal spentBefore, BigDecimal budget) {
        BigDecimal threshold = budget.multiply(ALERT_SHARE);
        if (spentBefore.compareTo(threshold) < 0 && accessService.spendThisMonth().compareTo(threshold) >= 0) {
            log.warn("AI spend this month has passed 80% of the ${} budget.", budget);
        }
    }

    private static AiUsageLog log(AppUser user, String model, long inputTokens, long outputTokens, AiOutcome outcome) {
        return AiUsageLog.builder()
                .user(user)
                .feature(FEATURE)
                .model(model)
                .inputTokens((int) inputTokens)
                .outputTokens((int) outputTokens)
                .costUsd(model == null ? BigDecimal.ZERO : AiPricing.cost(model, inputTokens, outputTokens))
                .outcome(outcome)
                .build();
    }

    private AiConversation owned(Long id) {
        return conversationRepository.findById(id)
                .filter(found -> found.getUser().getId().equals(lookup.userId()))
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + id));
    }

    static AiMessageResponse toResponse(AiMessage message) {
        return new AiMessageResponse(message.getId(), message.getRole(), message.getContent(), message.getSafetyFlag(),
                message.getUsageLog() == null ? null : message.getUsageLog().getRating(), message.getCreatedAt(),
                message.getContextSources() == null ? List.of() : List.of(message.getContextSources().split(",")));
    }

    private static String title(String text) {
        String line = text.lines().findFirst().orElse(text).trim();
        return line.length() <= 60 ? line : line.substring(0, 57) + "...";
    }

    private static String firstName(String fullName) {
        String trimmed = fullName == null ? "" : fullName.trim();
        int space = trimmed.indexOf(' ');
        return space < 0 ? trimmed : trimmed.substring(0, space);
    }
}
