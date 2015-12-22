package org.unicode.text.tools;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.unicode.cldr.draft.FileUtilities;
import org.unicode.cldr.tool.CountryCodeConverter;

import com.google.common.base.Splitter;
import com.ibm.icu.dev.util.UnicodeMap;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.util.Output;

public class EmojiAnnotations extends Birelation<String,String> {
    static final Splitter TAB = Splitter.on("\t").trimResults();

    private static final boolean SHOW = false;

    static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList("the", "of", "for", "a", "and", "state", 
            "c�te", "verde▪cape", "dhekelia", "akrotiri", "comros", "pdr", "jamahiriya", "part",
            "yugoslav", "tfyr", "autonomous", "rawanda", "da", "rb", "yugoslavia",
            "states", "sar", "people's", "minor",
            "sts."));

    static final EmojiAnnotations          ANNOTATIONS_TO_CHARS        = new EmojiAnnotations(
            GenerateEmoji.EMOJI_COMPARATOR,
            "emojiAnnotations.txt",
            "emojiAnnotationsFlags.txt",
            "emojiAnnotationsGroups.txt",
            "emojiAnnotationsZwj.txt"
            );

    public EmojiAnnotations(Comparator codepointCompare, String... filenames) {
        super(new TreeMap(codepointCompare), 
                new TreeMap(codepointCompare), 
                TreeSet.class, 
                TreeSet.class, 
                codepointCompare, 
                codepointCompare);

        //Output<Set<String>> lastLabel = new Output<Set<String>>(new TreeSet<String>(codepointCompare));
        for (String filename : filenames) {
            int lineCount = 0;
            int lineNumber = 0;
            EmojiIterator ei = new EmojiIterator(EmojiData.of(Emoji.VERSION_LAST_RELEASED), true);

            for (String line : FileUtilities.in(EmojiAnnotations.class, filename)) {
                line = line.trim();
                lineNumber++;
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.contains("closed")) {
                    int debug = 0;
                }
                lineCount++;
                for (String string : ei.set(line)) {
                    if (Emoji.ASCII_LETTERS.containsSome(string)) {
                        UnicodeSet overlap = new UnicodeSet().addAll(string).retainAll(Emoji.ASCII_LETTERS);
                        String withPosition = line.replaceAll("("+overlap+")", "###$1");
                        throw new IllegalArgumentException(lineNumber + "\tStrange line with ASCII emoji: " + overlap + "; "+ withPosition);
                    }
                    if (Emoji.skipEmojiSequence(string)) {
                        continue;
                    }
                    for (String item : ei.newLabel) {
                        add(fixAnnotation(item), string);
                    }
                }
            }
            if (SHOW) System.out.println(lineCount + "\tannotation lines from " + filename);
        }
        addOther("-apple", Emoji.EMOJI_CHARS);
        addOther("-android", Emoji.EMOJI_CHARS);
        addOther("", Emoji.EMOJI_CHARS);

        //        final Set<String> personAndroid = getValues("person-android");
        //        if (personAndroid != null) {
        //            UnicodeSet temp = new UnicodeSet(Emoji.APPLE)
        //            .removeAll(personAndroid)
        //            .removeAll(getValues("nature-android"))
        //            .removeAll(getValues("object-android"))
        //            .removeAll(getValues("place-android"))
        //            .removeAll(getValues("symbol-android"));
        //            System.out.println(temp.size() + " other-android: " + temp.toPattern(false));
        //            for (String s : temp) {
        //                add("other-android", s);
        //            }
        //        }
        //
        //        UnicodeSet temp = new UnicodeSet(Emoji.EMOJI_CHARS)
        //        .removeAll(getValues("person"))
        //        .removeAll(getValues("nature"))
        //        .removeAll(getValues("object"))
        //        .removeAll(getValues("place"))
        //        .removeAll(getValues("symbol"))
        //        .removeAll(getValues("flag"));
        //        System.out.println(temp.size() + " other: " + temp.toPattern(false));
        //        for (String s : temp) {
        //            add("other", s);
        //        }


        //        for (String s : FITZ_MINIMAL) {
        //            ANNOTATIONS_TO_CHARS.add("fitz-minimal", s);
        //        }
        //        for (String s : FITZ_OPTIONAL) {
        //            ANNOTATIONS_TO_CHARS.add("fitz-optional", s);
        //        }
        // for programmatic additions, take this and modify
        //        for (String s : Emoji.EMOJI_CHARS) {
        //            String charName = UCharacter.getName(s.codePointAt(0));
        //            if (charName.contains("MARK")) {
        //                ANNOTATIONS_TO_CHARS.add("mark", s);
        //            }
        //        }
        for (int cp1 = Emoji.FIRST_REGIONAL; cp1 <= Emoji.LAST_REGIONAL; ++cp1) {
            for (int cp2 = Emoji.FIRST_REGIONAL; cp2 <= Emoji.LAST_REGIONAL; ++cp2) {
                String emoji = new StringBuilder().appendCodePoint(cp1).appendCodePoint(cp2).toString();
                if (Emoji.EMOJI_CHARS.contains(emoji)) {
                    add("flag", emoji);
                }
                //String regionCode = GenerateEmoji.getFlagCode(emoji);
            }
        }
        // get extra names
        //        for (String name : CountryCodeConverter.names()) {
        //            String regionCode = CountryCodeConverter.getCodeFromName(name);
        //            if (regionCode == null || regionCode.length() != 2) {
        //                continue;
        //            }
        //            if (regionCode.equals("RS") 
        //                    && name.contains("montenegro")) {
        //                continue;
        //            }
        //            String emoji = Emoji.getEmojiFromRegionCode(regionCode);
        //            //System.out.println(regionCode + "=>" + name);
        //            addParts(emoji, name);
        //        }
        freeze();
        Map<String,UnicodeSet> _TO_UNICODE_SET = new HashMap<>();
        for (Entry<String, Set<String>> entry : this.keyToValues.keyValuesSet()) {
            _TO_UNICODE_SET.put(entry.getKey(), new UnicodeSet().addAll(entry.getValue()).freeze());
        }
        TO_UNICODE_SET = Collections.unmodifiableMap(_TO_UNICODE_SET);
        UnicodeSet annotationCharacters = new UnicodeSet().addAll(valuesSet());
        if (!annotationCharacters.containsAll(Emoji.EMOJI_CHARS)) {
            UnicodeSet missing = new UnicodeSet().addAll(Emoji.EMOJI_CHARS).removeAll(annotationCharacters);
            throw new IllegalArgumentException("Missing annotations: " + missing.toPattern(false));
        }

    }

    static final UnicodeMap<String> TTS = new UnicodeMap<>();
    static {
        for (String line : FileUtilities.in(GenerateOtherAnnotations.class, "en-tts.tsv")) {
            if (line.startsWith("#") || line.isEmpty()) continue;
            List<String> list = TAB.splitToList(line);
            String source = org.unicode.text.utility.Utility.fromHex(list.get(0));
            TTS.put(source, list.get(1));
        }
        TTS.freeze();
    }
    
    final Map<String,UnicodeSet> TO_UNICODE_SET;

    static final Set<String> GROUP_ANNOTATIONS = new HashSet<>(Arrays.asList(
            "default-text-style",
            "fitz-primary",
            "fitz-secondary",
            "nature",
            "nature-android",
            "nature-apple",
            "object",
            "object-android",
            "object-apple",
            "person",
            "person-android",
            "person-apple",
            "place",
            "place-android",
            "place-apple",
            "symbol",
            "symbol-android",
            "symbol-apple",
            "other-android",
            "flag",
            "other",
            "travel",
            "office",
            "animal",
            "sign",
            "word",
            "time",
            "food",
            "entertainment",
            "activity",
            "restaurant",
            "sound",
            "sport",
            "emotion",
            "communication",
            "education"
            ));

    public UnicodeSet getUnicodeSet(String annotation) {
        return TO_UNICODE_SET.get(annotation);
    }

    public Set<Entry<String, UnicodeSet>> getStringUnicodeSetEntries() {
        return TO_UNICODE_SET.entrySet();
    }

    private void addOther(String suffix, UnicodeSet core) {
        final Set<String> personApple = getValues("person" + suffix);
        if (personApple != null) {
            UnicodeSet temp = new UnicodeSet(core)
            .removeAll(personApple)
            .removeAll(getValues("nature" + suffix))
            .removeAll(getValues("object" + suffix))
            .removeAll(getValues("place" + suffix))
            .removeAll(getValues("symbol" + suffix));
            if (SHOW) System.out.println(temp.size() + " other" + suffix
                    + ": " + temp.toPattern(false));
            for (String s : temp) {
                add("other" + suffix, s);
            }
        }
    }

    private String fixAnnotation(String item) {
        String result = item.toLowerCase(Locale.ENGLISH);
        return result
                .replace("minimal", "primary")
                .replace("optional", "secondary");
    }

    //    public void addParts(String emoji, String name) {
    //        name = name.toLowerCase(Locale.ENGLISH);
    //        for (String namePart : name.split("[- ,&\\(\\)]+")) {
    //            if (STOP_WORDS.contains(namePart)) {
    //                continue;
    //            }
    //            if (namePart.startsWith("d’") || namePart.startsWith("d'")) {
    //                namePart = namePart.substring(2);
    //            }
    //            add(namePart, emoji);
    //        }
    //    }
}
