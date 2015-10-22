package org.unicode.text.tools;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.unicode.cldr.draft.FileUtilities;
import org.unicode.cldr.util.CLDRPaths;
import org.unicode.text.utility.Utility;

import com.google.common.base.Splitter;
import com.ibm.icu.dev.util.BagFormatter;
import com.ibm.icu.dev.util.CollectionUtilities;
import com.ibm.icu.dev.util.TransliteratorUtilities;
import com.ibm.icu.dev.util.UnicodeMap;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.text.LocaleDisplayNames;
import com.ibm.icu.text.LocaleDisplayNames.DialectHandling;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.util.LocaleData;
import com.ibm.icu.util.ULocale;

public class GenerateOtherAnnotations {

    private static final Set<String> SKIP = new HashSet<>(GenerateEmoji.GROUP_ANNOTATIONS);
    static {
        SKIP.add("flag");
    }

    public static void main(String[] args) throws IOException {
        String dir = "/Users/markdavis/Google Drive/workspace/DATA/emoji/";
        Splitter TAB = Splitter.on("\t").trimResults();
        Splitter COMMA = Splitter.on(",").trimResults();
        Splitter SPACE = Splitter.on(" ").trimResults();
        UnicodeMap<String> english = getEnglish();

        UnicodeSet missing = new UnicodeSet();

        for (String file : new File(dir).list()) {
            ULocale locale = new ULocale(file.substring(0,file.indexOf('.')));
            LocaleData ld = LocaleData.getInstance(locale);
            LocaleDisplayNames ldn = LocaleDisplayNames.getInstance(locale, DialectHandling.STANDARD_NAMES);
            UnicodeMap<Set<String>> map = new UnicodeMap<>();
            UnicodeMap<String> tts = new UnicodeMap<>();
            Splitter localeSplitter = locale.equals(ULocale.JAPANESE) ? SPACE : COMMA;

            for (String line : FileUtilities.in(dir, file)) {
                // U+00A9   ©   COPYRIGHT SIGN  Copyright-Symbol, Copyright, C      N
                List<String> parts = TAB.splitToList(line);
                String chars = parts.get(1);
                String annotationString = parts.get(3);
                Set<String> annotations = new LinkedHashSet<>(localeSplitter.splitToList(annotationString));

                // the first item is special. It may be a TTS item
                // use heuristics to remove it from rest
                Iterator<String> it = annotations.iterator();
                String firstItem = it.next();
                tts.put(chars, firstItem);
                if (it.hasNext() && firstItem.contains(" ")) {
                    it.remove();
                }
                map.put(chars, Collections.unmodifiableSet(annotations));
            }
            try (   PrintWriter outText = BagFormatter.openUTF8Writer(CLDRPaths.COMMON_DIRECTORY + "/annotations/", locale + ".xml")) {
                outText.append(GenerateEmoji.ANNOTATION_HEADER
                        + "\t\t<language type='" + locale + "'/>\n"
                        + "\t</identity>\n"
                        + "\t<annotations>\n");
                for (String s : GenerateEmoji.SORTED_EMOJI_CHARS_SET) {
                    Set<String> annotations = map.get(s);
                    if (annotations == null) {
                        annotations = Collections.emptySet();
                    }
                    if (annotations.isEmpty() && Emoji.isRegionalIndicator(s.codePointAt(0))) {
                        String regionCode = Emoji.getRegionCodeFromEmoji(s);
                        String name = ldn.regionDisplayName(regionCode);
                        if (!name.equals(regionCode)) {
                            annotations = Collections.singleton(name);
                        } else {
                            System.out.println(locale + "\tmissing\t" + regionCode);
                        }
                    }
                    if (annotations.isEmpty()) {
                        missing.add(s);
                        continue;
                    }

                    String annotationString = CollectionUtilities.join(annotations, "; ");
                    String englishAnnotationString = english.get(s);
                    outText
                    .append("\n\t\t<!-- " + fix(englishAnnotationString, ld) + " -->\n")
                    .append("\t\t<annotation cp='")
                    .append(new UnicodeSet().add(s).toPattern(false));
                    String ttsString = tts.get(s);
                    if (ttsString != null) {
                        outText.append("' tts='")
                        .append(fix(ttsString, ld));
                    }
                    outText.append("' draft='provisional'>")
                    .append(fix(annotationString, ld))
                    .append("</annotation>\n")
                    ;
                }
                outText.write("\t</annotations>\n"
                        + "</ldml>");
            }
        }

        //        for (String s: GenerateEmoji.SORTED_EMOJI_CHARS_SET) {
        //            if (!missing.contains(s)) {
        //                continue;
        //            }
        //            String englishAnn = english.get(s);
        //            int pos = englishAnn.indexOf(';');
        //            System.out.println("U+" + Utility.hex(s, ",U+") 
        //                    + "\t" + s 
        //                    + "\t" + englishAnn.substring(0,pos) 
        //                    + "\t" + englishAnn.substring(pos+1).trim());
        //        }
    }

    static final int[] delimiters = {LocaleData.QUOTATION_START, LocaleData.QUOTATION_END};

    private static String fix(String source, LocaleData ld) {
        if (source.contains("\"")) {
            // replace every other " with these:
            StringBuilder b = new StringBuilder();
            int current = 0;
            int lastPos = 0;
            while (true) {
                int pos = source.indexOf('"', lastPos);
                if (pos == -1) {
                    b.append(source.substring(lastPos));
                    source = b.toString();
                    break;
                }
                b.append(source.substring(lastPos, pos));
                b.append(ld.getDelimiter(delimiters[current]));
                lastPos = pos+1;
                current = (current + 1) % 2;
            }
        }
        return TransliteratorUtilities.toXML.transform(source);
    }

    static final LocaleDisplayNames ENGLISH_DISPLAY_NAMES = LocaleDisplayNames.getInstance(new ULocale("en"), DialectHandling.STANDARD_NAMES);


    private static UnicodeMap<String> getEnglish() {
        UnicodeMap<String> returnResult = new UnicodeMap<>();
        for (String s : Emoji.EMOJI_CHARS) {
            LinkedHashSet<String> result = new LinkedHashSet<>();
            if (Emoji.isRegionalIndicator(s.codePointAt(0))) {
                String regionCode = Emoji.getRegionCodeFromEmoji(s);
                String name = ENGLISH_DISPLAY_NAMES.regionDisplayName(regionCode);
                result.add(name);
            } else {
                result.add(UCharacter.getName(s, "+"));
            }
            for (String annotation : EmojiAnnotations.ANNOTATIONS_TO_CHARS.getKeys(s)) {
                if (SKIP.contains(annotation)) {
                    continue;
                }
                result.add(annotation);
            }
            returnResult.put(s, CollectionUtilities.join(result, "; "));
        }

        Set<String> missing = new TreeSet<>(GenerateEmoji.CODEPOINT_COMPARE);
        Emoji.EMOJI_CHARS.addAllTo(missing);

        for (String label : Arrays.asList("people",
                "nature",
                "objects",
                "places",                
                "symbols")) {
            Set<String> plain = EmojiAnnotations.ANNOTATIONS_TO_CHARS.getValues(label);
            missing.removeAll(plain);
            Set<String> apple = EmojiAnnotations.ANNOTATIONS_TO_CHARS.getValues(label+"-apple");
            Set<String> android = EmojiAnnotations.ANNOTATIONS_TO_CHARS.getValues(label+"-android");

            showDiff(label, "apple", apple, "android", android);

            Set<String> joint = new HashSet<String>(apple);
            joint.addAll(android);

            showDiff(label, "(apple ∪ android)", joint, "plain", plain);

            System.out.println();
        }
        showAndRemove("flag", missing);

        System.out.println("missing" + "\t\t" + missing.size() + "\t" + CollectionUtilities.join(missing, " "));


        for (String annotation : EmojiAnnotations.ANNOTATIONS_TO_CHARS.keySet()) {
            Set<String> sorted = new TreeSet<String>(GenerateEmoji.CODEPOINT_COMPARE);
            sorted.addAll(EmojiAnnotations.ANNOTATIONS_TO_CHARS.getValues(annotation));
            System.out.println(annotation + "\t" + sorted.size() + "\t" + CollectionUtilities.join(sorted, " "));
        }
        return returnResult.freeze();
    }


    private static void showAndRemove(String label, Set<String> missing) {
        Set<String> plain = EmojiAnnotations.ANNOTATIONS_TO_CHARS.getValues(label);
        missing.removeAll(plain);
        System.out.println(label + "\t\t" + plain.size() + "\t" + CollectionUtilities.join(plain, " "));
    }


    private static void showDiff(String label, String a, Set<String> aSet, String b, Set<String> bSet) {
        showDiff2(label, a, aSet, b, bSet, true);
        showDiff2(label, b, bSet, a, aSet, true);
        showDiff2(label, a, bSet, b, aSet, false);
    }


    private static void showDiff2(String label, String a, Set<String> aSet, String b, Set<String> bSet, boolean diff) {
        Set<String> aMinusB = new TreeSet<String>(GenerateEmoji.CODEPOINT_COMPARE);
        aMinusB.addAll(aSet);
        String rel; 
        if (diff) {
            aMinusB.removeAll(bSet);
            rel = " ⊖ ";
        } else {
            aMinusB.retainAll(bSet);
            rel = " ∩ ";
        }
        System.out.println(label + "\t" + a + rel + b + "\t" + aMinusB.size() + "\t" + CollectionUtilities.join(aMinusB, " "));
    }

}