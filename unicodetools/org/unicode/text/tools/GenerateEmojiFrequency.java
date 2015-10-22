package org.unicode.text.tools;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.unicode.cldr.draft.FileUtilities;
import org.unicode.cldr.util.Counter;
import org.unicode.cldr.util.Counter2;
import org.unicode.draft.CharacterFrequency;
import org.unicode.text.tools.GenerateEmoji.Style;
import org.unicode.text.utility.Utility;

import com.google.common.base.Splitter;
import com.ibm.icu.dev.util.BagFormatter;
import com.ibm.icu.dev.util.CollectionUtilities;
import com.ibm.icu.impl.Row.R2;
import com.ibm.icu.text.NumberFormat;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.util.ULocale;

public class GenerateEmojiFrequency {

//    static final EmojiAnnotations ANNOTATIONS_TO_CHARS = new EmojiAnnotations(
//            GenerateEmoji.CODEPOINT_COMPARE, 
//            "emojiAnnotations.txt",
//            "emojiAnnotationsGroups.txt",
//            "emojiAnnotationsFlags.txt"
//            );

    static final Set<String> KEEP_ANNOTATIONS = new LinkedHashSet<>(Arrays.asList(
            "animal",
            "sport",
            "plant",
            "emotion",
            "face",
            "body",
            "time",
            "vehicle",
            "travel",
            "clothing",
            "food",
            "drink",
            "entertainment",
            "office",
            "weather",

            "person",
            "place",
            "object",
            "symbol",
            "flag",
            "other"));

    static final Set<String> DROP_ANNOTATIONS = new HashSet<>(Arrays.asList(
            "default-text-style",
            "fitz-primary",
            "fitz-secondary",
            "nature-android",
            "nature-apple",
            "objects-android",
            "objects-apple",
            "people-android",
            "people-apple",
            "places-android",
            "places-apple",
            "symbols-android",
            "symbols-apple",
            "other-android"));


    public static void main(String[] args) throws IOException {
        Counter2<String> frequency = getData();

        Counter2<String> mainCount = new Counter2<>();
        Map<String,Buckets> mainList = new HashMap<>();

        NumberFormat pf = NumberFormat.getPercentInstance(ULocale.ENGLISH);
        pf.setMaximumFractionDigits(2);
        pf.setMinimumFractionDigits(2);
        NumberFormat intf = NumberFormat.getIntegerInstance(ULocale.ENGLISH);

        try (PrintWriter out = BagFormatter.openUTF8Writer(Emoji.TR51_DRAFT_DIR
                + Emoji.TR51_PREFIX, "emoji-frequency-all.html")) {
            GenerateEmoji.writeHeader(out, "Emoji Frequency", null, "Approximate frequency of emoji characters, gathered from emojiTracker and web stats.", "border='1'");

            String title = "%\tCP\tMain Category\tAnnotations";
            System.out.println(title);
            toRow(out, title, 999, "");
            for (String code : frequency.getKeysetSortedByCount(false)) {
                Double count = frequency.getCount(code);
                Set<String> annotations = EmojiAnnotations.ANNOTATIONS_TO_CHARS.getKeys(code);
                Set<String> keep = new LinkedHashSet<>(annotations);
                if (keep.contains("clock")) {
                    keep.remove("face");
                }
                String main = "?";
                for (String s : KEEP_ANNOTATIONS) {
                    if (keep.contains(s)) {
                        main = s;
                        break;
                    }
                }
                keep.removeAll(DROP_ANNOTATIONS);
                double percent = count/(double)frequency.getTotal();
                if (count != 0) {
                    String annotationList = CollectionUtilities.join(keep, ", ");
                    System.out.println(percent + "\t" + code + "\t" + main + "\t" + annotationList);
                    out.append("<tr>");
                    out.append("<td>").append(pf.format(percent)).append("</td>");
                    showItems(out, Collections.singleton(code));
                    out.append("<td>").append(main).append("</td>");
                    out.append("<td>").append(annotationList).append("</td>");
                    out.append("</tr>\n");
                }

                mainCount.add(main, count);
                Buckets list = mainList.get(main);
                if (list == null) {
                    mainList.put(main, list = new Buckets());
                }
                list.add(code, percent);
            }
            GenerateEmoji.writeFooter(out);
        }
        try (PrintWriter out = BagFormatter.openUTF8Writer(Emoji.TR51_DRAFT_DIR
                + Emoji.TR51_PREFIX, "emoji-frequency.html")) {
            GenerateEmoji.writeHeader(out, "Emoji Frequency", null, "Approximate frequency of emoji characters, gathered from emojiTracker and web stats.", "border='1'");

            System.out.println(Buckets.BUCKET_TITLE);
            toRow(out, Buckets.BUCKET_TITLE, 3, " width='12%'");

            for (String main : mainCount.getKeysetSortedByCount(false)) {
                Double count = mainCount.getCount(main);
                double percent = count/(double)mainCount.getTotal();
                Buckets list = mainList.get(main);

                System.out.println(main + "\t" + list.getCount() + "\t" + percent + list.toString(main));
                out.append("<tr>");
                out.append("<td>").append(main).append("</td>");
                out.append("<td>").append(intf.format(list.getCount())).append("</td>");
                out.append("<td>").append(pf.format(percent)).append("</td>");
                list.toHtml(out, main);
                out.append("</tr>\n");
            }
            GenerateEmoji.writeFooter(out);

        }
    }

    private static void showItems(PrintWriter out, Collection<String> items) {
        GenerateEmoji.displayUnicodeSet(out, items, Style.bestImage, 9999, 1, 1, "../../emoji/charts/full-emoji-list.html", " s18");
    }

    final static Splitter ONTAB = Splitter.on('\t').trimResults();

    private static void toRow(Appendable out, String tabRow, int startingAt, String attributes) {
        try {
            out.append("<tr>");
            for (String s : ONTAB.split(tabRow)) {
                out
                .append("<th")
                .append(--startingAt >= 0 ? "" : attributes)
                .append(">")
                .append(s)
                .append("</th>");
            }
            out.append("</tr>\n");
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static Counter2<String> getData() {
        Splitter semi = Splitter.on(';').trimResults();


        Counter2<String> emojiFrequency = new Counter2<>();
        for (String line : FileUtilities.in(GenerateEmojiFrequency.class, "emojiTracker.txt")) {
            List<String> parts = semi.splitToList(line);
            String code = Utility.fromHex(parts.get(0));
            long count = Long.parseLong(parts.get(1));
            emojiFrequency.add(code, (double)count);
        }

        Counter2<String> frequency = new Counter2<>();

        // make sure everything is listed, and normalize web vs twitter frequency
        Counter<Integer> webFrequency = CharacterFrequency.getCodePointCounter("mul", true);
        double webEmojiTotal = 0;
        for (String s : Emoji.EMOJI_CHARS) {
            int cp = s.codePointAt(0);
            if (s.length() == Character.charCount(cp)) {
                webEmojiTotal += webScale(cp) * webFrequency.get(cp);
            }
        }

        for (String s : Emoji.EMOJI_CHARS) {
            double value = emojiFrequency.getCount(s) / emojiFrequency.getTotal().doubleValue();
            int cp = s.codePointAt(0);
            if (s.length() == Character.charCount(cp)) {
                double scale = webScale(cp);
                value += scale * webFrequency.get(cp) / webEmojiTotal;
                value /= (1 + scale);
            }
            frequency.add(s, value);
        }
        return frequency;
    }

    static final UnicodeSet PRIMARILY_TEXT = new UnicodeSet("[© ® ™]").freeze();
    static final UnicodeSet SYMBOLS = new UnicodeSet().addAll(EmojiAnnotations.ANNOTATIONS_TO_CHARS.getValues("symbol")).freeze();

    private static double webScale(int cp) {
        return PRIMARILY_TEXT.contains(cp) || GenerateEmoji.STANDARDIZED_VARIANT.get(cp) != null ? 0
                : SYMBOLS.contains(cp) ? 0.01
                        : 1;
    }

    static class Buckets {
        static final String BUCKET_TITLE = "Main Category\tCount\tTotal%\tEach≥1%\tEach≥0.1%\tEach≥0.05%\tEach≥0.01%\tEach≥0.005%\tEach>0%\tNo Data Yet";

        List<String> L1 = new ArrayList<>();
        List<String> L01 = new ArrayList<>();
        List<String> L005 = new ArrayList<>();
        List<String> L001 = new ArrayList<>();
        List<String> L0005 = new ArrayList<>();
        List<String> Nonzero = new ArrayList<>();
        List<String> Zero = new ArrayList<>();

        private List<List<String>> bucketList = Arrays.asList(L1, L01, L005, L001, L0005, Nonzero, Zero);

        public void add(String code, double percent) {
            if (percent >= 0.01) {
                L1.add(code);
            } else if (percent >= 0.001) {
                L01.add(code);
            } else if (percent >= 0.0005) {
                L005.add(code);
            } else if (percent >= 0.0001) {
                L001.add(code);
            } else if (percent >= 0.00005) {
                L0005.add(code);
            } else if (percent >0) {
                Nonzero.add(code);
            } else {
                Zero.add(code);
            }
        }
        public void toHtml(PrintWriter out, String title) {
            for (List<String> cell : bucketList) {
                if (cell == Zero && title.equals("flag")) {
                    out.append("<td><i>others</i></td>");
                    continue;
                }
                showItems(out, cell);
            }
        }
        
        public int getCount() {
            int count = 0;
            for (List<String> cell : bucketList) {
                count += cell.size();
            }
            return count;
        }
        
        public String toString(String title) {
            StringBuilder out = new StringBuilder();
            for (List<String> cell : bucketList) {
                if (cell == Zero && title.equals("flag")) {
                    out.append("\tothers");
                    continue;
                }
                out.append("\t").append(CollectionUtilities.join(cell, " "));
            }
            return out.toString();
        }
    }
}