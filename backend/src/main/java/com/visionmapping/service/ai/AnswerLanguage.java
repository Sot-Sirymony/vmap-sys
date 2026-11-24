package com.visionmapping.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * V31 v2 (BRD 31 EC-16): whether a structured answer is written in the wrong
 * language. Khmer answers still hold Latin text by design (owners in English,
 * priorities, dates), so a Khmer answer is wrong only when it has a fair amount
 * of Latin letters and no Khmer at all; an English answer is wrong when Khmer
 * letters outnumber Latin ones.
 */
final class AnswerLanguage {

    /** Fewer Latin letters than this is too little text to judge. */
    static final int MIN_LETTERS = 40;

    private AnswerLanguage() {
    }

    static boolean wrong(ObjectMapper objectMapper, Object answer, String language) {
        int[] counts = new int[2]; // [khmer, latin]
        count(objectMapper.valueToTree(answer), counts);
        int khmer = counts[0];
        int latin = counts[1];
        if ("km".equals(language)) {
            return khmer == 0 && latin >= MIN_LETTERS;
        }
        return khmer > latin;
    }

    private static void count(JsonNode node, int[] counts) {
        if (node == null) {
            return;
        }
        if (node.isTextual()) {
            node.asText().codePoints().forEach(point -> {
                if (point >= 0x1780 && point <= 0x17FF) {
                    counts[0]++;
                } else if ((point >= 'a' && point <= 'z') || (point >= 'A' && point <= 'Z')) {
                    counts[1]++;
                }
            });
        } else {
            node.forEach(child -> count(child, counts));
        }
    }

    /** The extra instruction for the one retry. */
    static String retryInstruction(String language) {
        return "km".equals(language)
                ? " Important: write every text field in Khmer (owners stay in English as Me or a role)."
                : " Important: write every text field in English.";
    }
}
