package com.visionmapping.dto.response;

/** One exchange: what the participant sent and the reply, already saved. */
public record AiChatTurnResponse(
        Long conversationId,
        String title,
        AiMessageResponse participantMessage,
        AiMessageResponse reply) {
}
