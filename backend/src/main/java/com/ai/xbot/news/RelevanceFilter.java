package com.ai.xbot.news;

import java.text.Normalizer;
import java.util.*;

/**
 * Keyword filter for Tamil Nadu politics and governance, in English and Tamil, plus a first-guess
 * topic category. Parties are listed alphabetically and treated identically: the filter decides
 * relevance, never stance.
 */
public final class RelevanceFilter {

    private RelevanceFilter() {}

    public record Result(boolean relevant, List<String> matched, String category) {}

    /** Place / institution terms: at least one is needed for a feed that isn't already TN-specific. */
    static final List<String> PLACE_TERMS = List.of(
            "tamil nadu", "tamilnadu", "chennai", "madurai", "coimbatore", "tiruchi", "trichy", "salem", "tirunelveli",
            "vellore", "erode", "thoothukudi", "puducherry", "tn assembly", "fort st. george", "secretariat",
            "தமிழ்நாடு", "தமிழக", "சென்னை", "மதுரை", "கோவை", "திருச்சி", "சேலம்", "நெல்லை", "வேலூர்", "ஈரோடு", "தூத்துக்குடி");

    static final List<String> PARTY_TERMS = List.of(
            "aiadmk", "ammk", "bjp", "congress", "cpi", "dmdk", "dmk", "mdmk", "ntk", "pmk", "tvk",
            "tamilaga vettri kazhagam", "vck",
            "அதிமுக", "காங்கிரஸ்", "தவெக", "திமுக", "தேமுதிக", "நாம் தமிழர்", "பாஜக", "பாமக", "மதிமுக", "விசிக");

    static final Map<String, List<String>> CATEGORY_TERMS = new LinkedHashMap<>();
    static {
        CATEGORY_TERMS.put("LEGISLATURE", List.of("assembly", "bill", "legislat", "speaker", "mla", "சட்டமன்ற", "சட்டப்பேரவை", "மசோதா"));
        CATEGORY_TERMS.put("ELECTIONS", List.of("election", "poll", "candidate", "constituency", "voter", "தேர்தல்", "வேட்பாளர்", "வாக்கு"));
        CATEGORY_TERMS.put("EDUCATION", List.of("school", "college", "university", "student", "exam", "education", "பள்ளி", "கல்லூரி", "மாணவ", "கல்வி", "தேர்வு"));
        CATEGORY_TERMS.put("HEALTHCARE", List.of("hospital", "health", "doctor", "medical", "patient", "மருத்துவ", "சுகாதார", "நோயாளி"));
        CATEGORY_TERMS.put("TRANSPORT", List.of("metro", "bus", "rail", "road", "highway", "airport", "transport", "flyover", "மெட்ரோ", "பேருந்து", "ரயில்", "சாலை", "விமான"));
        CATEGORY_TERMS.put("INFRASTRUCTURE", List.of("infrastructure", "bridge", "water supply", "drainage", "power", "electricity", "housing", "குடிநீர்", "மின்", "பாலம்", "வீட்டுவசதி"));
        CATEGORY_TERMS.put("EMPLOYMENT", List.of("job", "employment", "investment", "industry", "factory", "wage", "unemployment", "வேலைவாய்ப்பு", "முதலீடு", "தொழில்"));
        CATEGORY_TERMS.put("POLICY", List.of("scheme", "policy", "budget", "government order", "announce", "திட்டம்", "கொள்கை", "பட்ஜெட்", "அறிவிப்பு", "அரசாணை"));
        CATEGORY_TERMS.put("CIVIC_ISSUES", List.of("garbage", "flood", "waterlogging", "pothole", "encroachment", "corporation", "civic", "வெள்ளம்", "குப்பை", "மாநகராட்சி"));
        CATEGORY_TERMS.put("GOVERNANCE", List.of("chief minister", "minister", "collector", "department", "government", "cm ", "முதல்வர்", "முதலமைச்சர்", "அமைச்சர்", "ஆட்சியர்", "அரசு"));
    }

    public static Result evaluate(String title, String excerpt, boolean requireKeywordMatch) {
        String text = " " + Normalizer.normalize((title + " " + (excerpt == null ? "" : excerpt)), Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ") + " ";

        List<String> matched = new ArrayList<>();
        boolean place = false, party = false;
        for (String t : PLACE_TERMS) if (contains(text, t)) { matched.add(t); place = true; }
        for (String t : PARTY_TERMS) if (contains(text, t)) { matched.add(t); party = true; }

        String category = null;
        int best = 0;
        for (Map.Entry<String, List<String>> e : CATEGORY_TERMS.entrySet()) {
            int hits = 0;
            for (String t : e.getValue()) if (contains(text, t)) hits++;
            if (hits > best) { best = hits; category = e.getKey(); }
        }
        if (party && (category == null || category.equals("GOVERNANCE"))) category = "POLITICS";
        if (contains(text, "tvk") || contains(text, "tamilaga vettri kazhagam") || contains(text, "தவெக")) category = "TVK";

        boolean relevant = !requireKeywordMatch || place || party;
        return new Result(relevant, matched, category == null ? "PUBLIC_INTEREST" : category);
    }

    /** Short Latin acronyms must match as whole words (so "dmk" doesn't match inside other words). */
    private static boolean contains(String text, String term) {
        boolean latinShort = term.length() <= 5 && term.chars().allMatch(c -> c < 128);
        if (!latinShort) return text.contains(term);
        int idx = text.indexOf(term);
        while (idx >= 0) {
            char before = text.charAt(idx - 1 < 0 ? 0 : idx - 1);
            char after = idx + term.length() < text.length() ? text.charAt(idx + term.length()) : ' ';
            if (!Character.isLetterOrDigit(before) && !Character.isLetterOrDigit(after)) return true;
            idx = text.indexOf(term, idx + 1);
        }
        return false;
    }
}
