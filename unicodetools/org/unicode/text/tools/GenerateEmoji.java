package org.unicode.text.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.unicode.cldr.draft.FileUtilities;
import org.unicode.cldr.draft.ScriptMetadata.Trinary;
import org.unicode.cldr.util.Counter;
import org.unicode.cldr.util.MapComparator;
import org.unicode.cldr.util.With;
import org.unicode.draft.GetNames;
import org.unicode.jsp.Subheader;
import org.unicode.props.IndexUnicodeProperties;
import org.unicode.props.UcdProperty;
import org.unicode.props.UcdPropertyValues;
import org.unicode.props.UcdPropertyValues.Binary;
import org.unicode.props.UcdPropertyValues.Block_Values;
import org.unicode.props.UcdPropertyValues.General_Category_Values;
import org.unicode.text.UCA.UCA;
import org.unicode.text.UCA.UCA.CollatorType;
import org.unicode.text.UCA.WriteCollationData;
import org.unicode.text.UCD.Default;
import org.unicode.text.tools.GenerateEmoji.Data;
import org.unicode.text.utility.Utility;

import com.ibm.icu.dev.util.BagFormatter;
import com.ibm.icu.dev.util.CollectionUtilities;
import com.ibm.icu.dev.util.Relation;
import com.ibm.icu.dev.util.UnicodeMap;
import com.ibm.icu.impl.MultiComparator;
import com.ibm.icu.impl.Row;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.text.Collator;
import com.ibm.icu.text.LocaleDisplayNames;
import com.ibm.icu.text.LocaleDisplayNames.DialectHandling;
import com.ibm.icu.text.Transform;
import com.ibm.icu.text.UTF16;
import com.ibm.icu.text.UTF16.StringComparator;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.util.Output;
import com.ibm.icu.util.ULocale;

public class GenerateEmoji {
    private static boolean SHOW = false;
    private static final UnicodeSet ASCII_LETTER_HYPHEN = new UnicodeSet('-', '-', 'A', 'Z', 'a', 'z', '’', '’').freeze();
    private static final UnicodeSet KEYWORD_CHARS = new UnicodeSet(ASCII_LETTER_HYPHEN).add('0','9').add(0x0020).freeze();
    private static final UnicodeSet EXTRAS = new UnicodeSet(
            "[\\u2714\\u2716\\u303D\\u3030 \\u00A9 \\u00AE \\u2795-\\u2797 \\u27B0 \\U0001F519-\\U0001F51C {🇽🇰}]")
    .add("*"+Emoji.ENCLOSING_KEYCAP)
    .freeze();
    static final UnicodeSet EXCLUDE = new UnicodeSet(
            "[🙬 🙭 🙮 🙯🗴🗵🗶🗷🗸🗹★☆⛫⛩\uFFFC⛤-⛧ ⌤⌥⌦⌧⌫⌬⎆⎇⎋⎗⎘⎙⎚⏣⚝⛌⛚⛬⛭⛮⛯⛶⛻✓🆊\\U0001F544-\\U0001F549]").freeze();
    // 🖫🕾🕿🕻🕼🕽🕾🕿🖀🖪🖬🖭
    static final Set<String> SKIP_WORDS = new HashSet(Arrays.asList("with", "a", "in", "without", "and", "white", "symbol", "sign", "for", "of", "black"));

    static final IndexUnicodeProperties LATEST = IndexUnicodeProperties.make(Default.ucdVersion());
    static final UnicodeMap<String> STANDARDIZED_VARIANT = LATEST.load(UcdProperty.Standardized_Variant);
    static final UnicodeMap<String> VERSION = LATEST.load(UcdProperty.Age);
    static final UnicodeMap<String> WHITESPACE = LATEST.load(UcdProperty.White_Space);
    static final UnicodeMap<String> GENERAL_CATEGORY = LATEST.load(UcdProperty.General_Category);
    static final UnicodeMap<String> SCRIPT_EXTENSIONS = LATEST.load(UcdProperty.Script_Extensions);
    private static final UnicodeSet COMMON_SCRIPT = new UnicodeSet()
    .addAll(SCRIPT_EXTENSIONS.getSet(UcdPropertyValues.Script_Values.Common.toString()))
    .freeze();

    static final UnicodeMap<String> NFKCQC = LATEST.load(UcdProperty.NFKD_Quick_Check);
    static final UnicodeMap<String> NAME = LATEST.load(UcdProperty.Name);
    static final UnicodeSet JSOURCES = new UnicodeSet();
    static {
        JSOURCES
        .addAll(LATEST.load(UcdProperty.Emoji_DCM).keySet())
        .addAll(LATEST.load(UcdProperty.Emoji_KDDI).keySet())
        .addAll(LATEST.load(UcdProperty.Emoji_SB).keySet())
        .removeAll(WHITESPACE.getSet(UcdPropertyValues.Binary.Yes.toString()))
        .freeze();
        if (SHOW) System.out.println("Core:\t" + JSOURCES.size() + "\t" + JSOURCES);
    }
    static final LocaleDisplayNames LOCALE_DISPLAY = LocaleDisplayNames.getInstance(ULocale.ENGLISH);

    private static final String OUTPUT_DIR = "/Users/markdavis/workspace/unicode-draft/reports/tr51/";
    private static final String IMAGES_OUTPUT_DIR = OUTPUT_DIR + "images/";
    static final Pattern tab = Pattern.compile("\t");
    static final Pattern space = Pattern.compile(" ");
    static final char EMOJI_VARIANT = '\uFE0F';
    static final String EMOJI_VARIANT_STRING = String.valueOf(EMOJI_VARIANT);
    static final char TEXT_VARIANT = '\uFE0E';
    static final String TEXT_VARIANT_STRING = String.valueOf(TEXT_VARIANT);
    static final String REPLACEMENT_CHARACTER = "\uFFFD";

    static final MapComparator mp = new MapComparator().setErrorOnMissing(false);

    static final Relation<String,String> ORDERING_TO_CHAR = new Relation(new LinkedHashMap(), LinkedHashSet.class);
    static {
        Set<String> sorted = new LinkedHashSet();
        Output<Set<String>> lastLabel = new Output(new TreeSet());
        for (String line : FileUtilities.in(GenerateEmoji.class, "emojiOrdering.txt")) {
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            line = getLabelFromLine(lastLabel, line);
            for (int i = 0; i < line.length();) {
                String string = getEmojiSequence(line, i);
                i += string.length();
                if (skipEmojiSequence(string)) {
                    continue;
                }
                if (!sorted.contains(string)) {
                    sorted.add(string);
                    for (String item : lastLabel.value) {
                        ORDERING_TO_CHAR.put(item, string);
                    }
                }
            }        
        }
        Set<String> missing = Emoji.EMOJI_CHARS.addAllTo(new LinkedHashSet());
        missing.removeAll(sorted);
        if (!missing.isEmpty()) {
            ORDERING_TO_CHAR.putAll("other", missing);
            System.err.println("Missing some orderings");
        }
        sorted.addAll(missing);
        mp.add(sorted);
        mp.freeze();
        ORDERING_TO_CHAR.freeze();
    }

    static final Comparator CODEPOINT_COMPARE = 
            new MultiComparator<String>(
                    mp,
                    UCA.buildCollator(null), // don't need cldr features
                    new UTF16.StringComparator(true,false,0));

    static final Comparator CODEPOINT_COMPARE_SHORTER = 
            new MultiComparator<String>(
                    Emoji.CODEPOINT_LENGTH,
                    mp,
                    UCA.buildCollator(null), // don't need cldr features
                    new UTF16.StringComparator(true,false,0)); 

    static final Subheader subheader = new Subheader("/Users/markdavis/workspace/unicodetools/data/ucd/7.0.0-Update/");
    static final Set<String> SKIP_BLOCKS = new HashSet(Arrays.asList("Miscellaneous Symbols", 
            "Enclosed Alphanumeric Supplement", 
            "Miscellaneous Symbols And Pictographs",
            "Miscellaneous Symbols And Arrows"));

    public static String getEmojiVariant(String browserChars, String variant) {
        int first = browserChars.codePointAt(0);
        String probe = new StringBuilder()
        .appendCodePoint(first)
        .append(variant).toString();
        if (STANDARDIZED_VARIANT.get(probe) != null) {
            browserChars = probe + browserChars.substring(Character.charCount(first));
        }
        return browserChars;
    }

    enum Style {plain, text, emoji, bestImage, refImage}
    static final Relation<Style,String> STYLE_TO_CHARS = Relation.of(new EnumMap(Style.class), TreeSet.class, CODEPOINT_COMPARE);

    static final Birelation<String,String> ANNOTATIONS_TO_CHARS = new Birelation(
            new TreeMap(CODEPOINT_COMPARE), 
            new TreeMap(CODEPOINT_COMPARE), 
            TreeSet.class, 
            TreeSet.class, 
            CODEPOINT_COMPARE, 
            CODEPOINT_COMPARE);
    static {
        Output<Set<String>> lastLabel = new Output(new TreeSet<String>(CODEPOINT_COMPARE));
        for (String line : FileUtilities.in(GenerateEmoji.class, "emojiAnnotations.txt")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            line = getLabelFromLine(lastLabel, line);
            for (int i = 0; i < line.length();) {
                String string = getEmojiSequence(line, i);
                i += string.length();
                if (skipEmojiSequence(string)) {
                    continue;
                }
                for (String item : lastLabel.value) {
                    ANNOTATIONS_TO_CHARS.add(item, string);
                }
            }
        }
        ANNOTATIONS_TO_CHARS.freeze();
    }

    public static String getLabelFromLine(Output<Set<String>> newLabel, String line) {
        line = line.replace(EMOJI_VARIANT_STRING, "").replace(TEXT_VARIANT_STRING, "").trim();
        int tabPos = line.indexOf('\t');
        //        if (tabPos < 0 && Emoji.EMOJI_CHARS.contains(getEmojiSequence(line, 0))) {
        //            tabPos = line.length();
        //            
        //        }
        if (tabPos >= 0) {
            newLabel.value.clear();
            String[] temp = line.substring(0,tabPos).trim().split(",\\s*");
            for (String part : temp) {
                if (KEYWORD_CHARS.containsAll(part)) {
                    newLabel.value.add(part);
                } else {
                    throw new IllegalArgumentException("Bad line format: " + line);
                }
            }
            line = line.substring(tabPos + 1);
        }
        return line;
    }
    static final Transform<String,String> WINDOWS_URL = new Transform<String,String>() {
        public String transform(String s) {
            String base = "images/windows/windows_";
            String separator = "_";
            return base + Emoji.buildFileName(s, separator) + ".png";
        }

    };

    enum Label {
        person, body, face, nature, animal, plant, clothing, emotion, 
        food, travel, vehicle, place, office,
        time, weather, game, sport, activity, object,
        sound, 
        flag,    
        arrow,
        word,
        sign, 
        //unknown,
        ;

        static Label get(String string) {
            return Label.valueOf(string);
        }
        static final Comparator<Label> LABEL_COMPARE = new Comparator<Label>() {
            public int compare(Label o1, Label o2) {
                return o1.compareTo(o2);
            }
        };

        static final Birelation<String, Label> CHARS_TO_LABELS 
        = Birelation.of(
                new TreeMap(CODEPOINT_COMPARE), 
                new EnumMap(Label.class), 
                TreeSet.class, 
                TreeSet.class, 
                LABEL_COMPARE,
                CODEPOINT_COMPARE
                );

        static {
            Output<Set<String>> lastLabel = new Output(new TreeSet<String>(CODEPOINT_COMPARE));
            String sublabel = null;
            for (String line : FileUtilities.in(GenerateEmoji.class, "emojiLabels.txt")) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                line = getLabelFromLine(lastLabel, line);
                for (int i = 0; i < line.length();) {
                    String string = getEmojiSequence(line, i);
                    i += string.length();
                    if (skipEmojiSequence(string)) {
                        continue;
                    }
                    for (String item : lastLabel.value){ 
                        addLabel(string, Label.valueOf(item));
                    }
                }
            }
            for (String isoCountries : ULocale.getISOCountries()) {
                if (!ASCII_LETTER_HYPHEN.containsAll(isoCountries)) {
                    continue;
                }
                String cc = Emoji.getHexFromFlagCode(isoCountries);
                addLabel(cc, Label.flag);
            }
            // remove misc
            for (Entry<String, Set<Label>> entry : CHARS_TO_LABELS.keyValuesSet()) {
                String chars = entry.getKey();
                Set<Label> set = entry.getValue();
                if (set.contains(Label.sign) && set.size() > 1) {
                    CHARS_TO_LABELS.remove(chars, Label.sign);
                }
            }
            CHARS_TO_LABELS.freeze();
            int i = 0;
            if (SHOW) for (Entry<Label, Set<String>> entry : CHARS_TO_LABELS.valueKeysSet()) {
                System.out.println(i++ + "\t" + entry.getKey() + "\t" + entry.getValue());
            }
        }

        public static void addLabel(String string, Label lastLabel) {
            if (string.contains("Ⓕ")) {
                int x = 0;
            }
            if (!Emoji.EMOJI_CHARS.containsAll(string)) {
                return;
            }
            CHARS_TO_LABELS.add(string, lastLabel);
        }
    }

    public static boolean skipEmojiSequence(String string) {
        if (string.equals(" ") 
                || string.equals(EMOJI_VARIANT_STRING) 
                || string.equals(TEXT_VARIANT_STRING)
                || EXCLUDE.contains(string)) {
            return true;
        }
        return false;
    }

    private static String getEmojiSequence(String line, int i) {
        // it is base + variant? + keycap
        // or
        // RI + RI + variant?
        int firstCodepoint = line.codePointAt(i);
        int firstLen = Character.charCount(firstCodepoint);
        if (i + firstLen == line.length()) {
            return line.substring(i, i+firstLen);
        }
        int secondCodepoint = line.codePointAt(i+firstLen);
        int secondLen = Character.charCount(secondCodepoint);
        if (secondCodepoint == Emoji.ENCLOSING_KEYCAP
                || (Emoji.isRegionalIndicator(firstCodepoint) && Emoji.isRegionalIndicator(secondCodepoint))) {
            return line.substring(i, i+firstLen+secondLen);
        }
        //        if ((secondCodepoint == EMOJI_VARIANT || secondCodepoint == TEXT_VARIANT) && i + firstLen + secondLen < line.length()) {
        //            int codePoint3 = line.codePointAt(i+firstLen+secondLen);
        //            int len3 = Character.charCount(codePoint3);
        //            if (codePoint3 == ENCLOSING_KEYCAP) {
        //                return line.substring(i, i+firstLen+secondLen+len3);
        //            }
        //        }
        return line.substring(i, i+firstLen);
    }


    static class Data implements Comparable<Data>{
        private static final String MISSING_CELL = "<td class='miss'>missing</td>\n";
        private static final String MISSING7_CELL = "<td class='miss7'>new 7.0</td>\n";
        final String chars;
        final String code;
        final UcdPropertyValues.Age_Values age;
        final Style defaultPresentation;
        final Set<Label> labels;
        final String name;
        //        static final Relation<Label, Data> LABELS_TO_DATA 
        //        = Relation.of(new EnumMap(Label.class), TreeSet.class); // , BY_LABEL

        static final UnicodeSet DATA_CHARACTERS = new UnicodeSet();

        static final UnicodeSet missingJSource = new UnicodeSet(JSOURCES);
        static Map<String, Data> STRING_TO_DATA = new TreeMap<>();
        @Override
        public boolean equals(Object obj) {
            return chars.equals(((Data)obj).chars);
        }        
        @Override
        public int hashCode() {
            return chars.hashCode();
        }
        @Override
        public int compareTo(Data o) {
            //            int diff = age.compareTo(o.age);
            //            if (diff != 0) {
            //                return diff;
            //            }
            return CODEPOINT_COMPARE.compare(chars, o.chars);
        }

        public Data(String chars, String code, String age,
                String defaultPresentation, String name) {
            this.chars = chars;
            if (chars.contains(EMOJI_VARIANT_STRING) || chars.equals(TEXT_VARIANT_STRING)) {
                throw new IllegalArgumentException();
            }
            this.code = code;
            this.age = UcdPropertyValues.Age_Values.valueOf(age.replace('.', '_'));
            this.defaultPresentation = Style.valueOf(defaultPresentation);
            this.labels = storeLabels();
            this.name = getName(chars);
            //addWords(chars, name);
            DATA_CHARACTERS.add(chars);
            //            for (Label label : labels) {
            //                LABELS_TO_DATA.put(label, this);
            //            }
            if (!Utility.fromHex(code).equals(chars)) {
                throw new IllegalArgumentException();
            }
        }

        public Data(int codepoint) {
            this(new StringBuilder().appendCodePoint(codepoint).toString());
        }

        public Data(String s) {
            this(s, 
                    "U+" + Utility.hex(s, " U+"), 
                    VERSION.get(s.codePointAt(0)).replace("_", "."), 
                    "text", 
                    Default.ucd().getName(s));
        }

        private Set<Label> storeLabels() {
            Set<Label> labels2 = Label.CHARS_TO_LABELS.getValues(chars); // override
            if (labels2 == null) {
                if (chars.equals("🇽🇰")) {
                    labels2 = Collections.singleton(Label.flag);
                } else {
                    labels2 = Collections.singleton(Label.sign);
                    if (SHOW) System.out.println("*** No specific label for " + Utility.hex(chars) + " " + NAME.get(chars.codePointAt(0)));
                }
            }
            return Collections.unmodifiableSet(labels2);
        }

        static final Data parseLine(String line) {
            String[] items = tab.split(line);
            // U+2194   V1.1    text    arrows  ↔   LEFT RIGHT ARROW
            String code1 = items[0];
            String age1 = items[1];
            String defaultPresentation = items[2];
            String temp = items[3];
            if (temp.isEmpty()) {
                temp = "misc";
            }
            //            EnumSet labelSet = EnumSet.noneOf(Label.class);
            //            for (String label : Arrays.asList(space.split(temp))) {
            //                Label newLabel = Label.get(label);
            //                labelSet.add(newLabel);
            //            }

            String chars1 = items[4];
            if (!Emoji.EMOJI_CHARS.containsAll(chars1)) {
                if (SHOW) System.out.println("Skipping " + getCodeAndName(chars1, " "));
                return null;
            }
            temp = items[5];
            if (temp.startsWith("flag")) {
                temp = "flag for" + temp.substring(4);
            }
            String name1 = temp;
            return new Data(chars1, code1, age1, defaultPresentation, name1);
        }

        static void add(String line) {
            Data data = parseLine(line);
            addNewItem(data, STRING_TO_DATA);
        }

        @Override
        public String toString() {
            return code 
                    + "\t" + getVersion() 
                    + "\t" + defaultPresentation
                    + "\t" + chars 
                    + "\t" + name;
        }
        private String getVersion() {
            return age.toString().replace('_', '.') + (JSOURCES.contains(chars) ? "*" : "");
        }


        public String toHtmlString(Form form, int item, Stats stats) {
            String missingCell = VERSION70.containsSome(chars) ? MISSING7_CELL : MISSING_CELL;
            String core = Emoji.buildFileName(chars,"_");
            String symbolaCell = getCell(Source.ref, core, missingCell);
            //                    String symbolaCell = Emoji.isRegionalIndicator(chars.codePointAt(0))
            //                            ? getCell("ref", core, missingCell)
            //                            : "<td class='symb'>" + chars + "</td>\n";
            //                    getFlag(chars)
            //            if (symbolaChars == null) {
            //                symbolaChars = chars;
            //            }
            //            int firstCodepoint = chars.codePointAt(0);
            //            int firstLen = Character.charCount(firstCodepoint);
            //            int secondCodepoint = firstLen >= chars.length() ? 0 : chars.codePointAt(firstLen);

            //            String header = Default.ucd().getBlock(firstCodepoint).replace('_', ' ');
            //            String subhead = subheader.getSubheader(firstCodepoint);
            //            if (SKIP_BLOCKS.contains(header)) {
            //                header = "<i>" + subhead + "</i>";
            //            } else if (!header.equalsIgnoreCase(subhead)) {
            //                header += ": <i>" + subhead + "</i>";
            //            }

            String appleCell = getCell(Source.apple, core, missingCell);
            String androidCell = getCell(Source.android, core, missingCell);
            String twitterCell = getCell(Source.twitter, core, missingCell);
            String windowsCell = getCell(Source.windows, core, missingCell);
            if (stats != null) {
                stats.add(chars, Source.apple, appleCell.equals(missingCell));
                stats.add(chars, Source.android, androidCell.equals(missingCell));
                stats.add(chars, Source.twitter, twitterCell.equals(missingCell));
                stats.add(chars, Source.windows, windowsCell.equals(missingCell));
            }

            //            String android = androidPng(firstCodepoint, secondCodepoint, true);
            //            String androidCell = missingCell;
            //            if (android != null && new File(IMAGES_OUTPUT_DIR, android).exists()) {
            //                if (secondCodepoint != 0) {
            //                    String secondString = androidPng(firstCodepoint, secondCodepoint, false);
            //                    if (secondString != null) {
            //                        android = secondString;
            //                    }
            //                }
            //                androidCell = "<td class='andr'><img alt='" + chars
            //                        + "' class='imga' src='images/" + android + "'></td>\n";
            //            }


            String browserCell =  "<td class='chars'>" + getEmojiVariant(chars, EMOJI_VARIANT_STRING) + "</td>\n";

            String textChars = getEmojiVariant(chars, TEXT_VARIANT_STRING);
            Set<String> annotations = ifNull(ANNOTATIONS_TO_CHARS.getKeys(chars), Collections.EMPTY_SET);
            //            annotations = new LinkedHashSet(annotations);
            //            for (Label label : labels) {
            //                annotations.remove(label.toString());
            //            }
            StringBuilder annotationString = new StringBuilder();
            if (!annotations.isEmpty()) {
                for (String annotation: annotations) {
                    if (annotationString.length() != 0) {
                        annotationString.append(", ");
                    }
                    annotationString.append(getLink("emoji-annotations.html#" + annotation, annotation, "annotate"));
                }
            }
            //            String twitterCell = ! TWITTER_CHARS.contains(chars) ? missingCell
            //                    : "<td class='andr'>" +
            //                    "<img class='imga' alt='" + chars
            //                    + "' src='" +
            //                    TWITTER_URL.transform(chars) +
            //                    "'>" + "</td>\n";
            //            String appleCell = !GITHUB_APPLE_CHARS.contains(chars) && !APPLE_LOCAL.contains(chars)
            //                    ? missingCell
            //                            : "<td class='andr'>" +
            //                            "<img class='imga' alt='" + chars
            //                            + "' src='" +
            //                            APPLE_URL.transform(chars) +
            //                            "'>" + "</td>\n";
            //            String windowsCell = !WINDOWS_CHARS.contains(chars) ? missingCell : "<td class='andr'>" +
            //                    "<img class='imga' alt='" + chars
            //                    + "' src='" +
            //                    WINDOWS_URL.transform(chars) +
            //                    "'>" + "</td>\n";
            String anchor = getAnchor(code);
            return "<tr>"
            + "<td class='rchars'>" + item + "</td>\n"
            + "<td class='code'>" + getDoubleLink(anchor, code) + "</td>\n"
            + browserCell
            + (form != Form.fullForm && form != Form.extraForm ? ""
                    :  symbolaCell
                    + appleCell
                    + androidCell
                    + twitterCell
                    + windowsCell
                    )
                    //+ browserCell
                    + (form.compareTo(Form.shortForm) <= 0 ? "" : 
                        "<td class='name'>" + name + "</td>\n")
                        + "<td class='age'>" + getVersion() + "</td>\n"
                        + "<td class='default'>" + defaultPresentation + (!textChars.equals(chars) ? "*" : "") + "</td>\n"
                        + (form.compareTo(Form.shortForm) <= 0 ? "" : 
                            "<td class='name'>" 
                            + annotationString
                            //                            + CollectionUtilities.join(labels, ", ")
                            //                            + (annotationString.length() == 0 ? "" : ";<br>" + annotationString)
                            + "</td>\n"
                            //                          +  "<td class='name'>" + header + "</td>"
                                )
                                + "</tr>";
        }

        public String toSemiString(int order) {
            Set<String> annotations = ifNull(ANNOTATIONS_TO_CHARS.getKeys(chars), Collections.EMPTY_SET);
            //            if (annotations != null) {
            //                annotations = new LinkedHashSet(annotations);
            //                for (Label label : labels) {
            //                    annotations.remove(label.toString());
            //                }
            //            }
            String flagRegion = getFlagRegionName(chars);
            if (flagRegion != null) {
                annotations = new LinkedHashSet(annotations);
                annotations.add(flagRegion);
            }
            if (annotations.isEmpty()) {
                System.out.println("No annotations for:\t" + getName(chars) + "\t" + chars);
            }
            return code 
                    + " ;\t" + defaultPresentation
                    + " ;\t" + order
                    + " ;\t" + CollectionUtilities.join(annotations, ", ")
                    + " \t#\t" + getVersion()
                    + "\t" + getName(chars) 
                    ;
        }

        public String getCell(Source type, String core, String missingCell) {
            String filename = getImageFilename(type, core);
            String androidCell = missingCell;
            if (filename != null && new File(IMAGES_OUTPUT_DIR, filename).exists()) {
                androidCell = "<td class='andr'><img alt='" + chars + "' class='imga' src='images/" + filename + "'></td>\n";
            }
            return androidCell;
        }

        private String getAnchor(String code2) {
            return code.replace(" ", "-").replace("+", "").replace("U", "");
        }
        public static String toHtmlHeaderString(Form form) {
            boolean shortForm = form.compareTo(Form.shortForm) <= 0;
            return "<tr>"
            + "<th>Count</th>\n"
            + "<th class='rchars'>Code</th>\n"
            + "<th class='cchars'>Browser</th>\n"
            + (form != Form.fullForm && form != Form.extraForm 
            ? "" : 
                "<th class='cchars'>B&amp;W*</th>\n"
                + "<th class='cchars'>Apple*</th>\n"
                + "<th class='cchars'>Android</th>\n"
                + "<th class='cchars'>Twitter</th>\n"
                + "<th class='cchars'>Windows</th>\n"
                    )
                    //+ "<th class='cchars'>Browser</th>\n"
                    + (shortForm ? "" : 
                        "<th>Name</th>\n"
                        + "<th>Version</th>\n"
                        + "<th>Default</th>\n"
                        + "<th>Annotations</th>\n"
                        //                        + "<th>Block: <i>Subhead</i></th>\n"
                            )
                            + "</tr>";
        }
    }

    public static String getImageFilename(Source type, String core) {
        return type + "/" + type + "_" + core + ".png";
    }

    public static String getBestImage(String s, Source... doFirst) {
        for (Source source : orderedEnum(doFirst)) {
            String cell = getImage(source, s);
            if (cell != null) {
                return cell;
            }
        }
        return null;
    }

    static public String getImage(Source type, String chars) {
        String core = Emoji.buildFileName(chars,"_");
        String filename = getImageFilename(type, core);
        if (filename != null && new File(IMAGES_OUTPUT_DIR, filename).exists()) {
            return "<img alt='" + chars + "'" +
                    " class='imga' src='images/" + filename + "'" +
                    " title='" + getCodeAndName(chars, " ") + "'" +
                    ">";
        }
        return null;
    }

    public static File getBestFile(String s, Source... doFirst) {
        for (Source source : orderedEnum(doFirst)) {
            File file = getImageFile(source, s);
            if (file != null) {
                return file;
            }
        }
        return null;
    }

    public static Iterable<Source> orderedEnum(Source... doFirst) {
        if (doFirst.length == 0) {
            return Arrays.asList(Source.values());
        }
        LinkedHashSet<Source> ordered = new LinkedHashSet(Arrays.asList(doFirst));
        ordered.addAll(Arrays.asList(Source.values()));
        return ordered;
    }

    static public File getImageFile(Source type, String chars) {
        String core = Emoji.buildFileName(chars,"_");
        String filename = getImageFilename(type, core);
        if (filename != null) { 
            File file = new File(IMAGES_OUTPUT_DIR, filename);
            if (file.exists()) {
                return file;
            }
        }
        return null;
    }

    enum Source {apple, android, twitter, windows, ref}

    private static class Stats {
        enum Type {countries, misc, cards, dominos, majong, v70}
        static final UnicodeSet DOMINOS = new UnicodeSet("[🀰-🂓]");
        static final UnicodeSet CARDS = new UnicodeSet("[🂠-🃵]");
        static final UnicodeSet MAHJONG = new UnicodeSet("[🀀-🀫]");
        final EnumMap<Type,EnumMap<Source,UnicodeSet>> data = new EnumMap<>(Type.class);
        public void add(
                String chars,
                Source source,
                boolean isMissing) {
            if (isMissing) {
                Type type = VERSION70.containsAll(chars) ? Type.v70
                        : getFlagCode(chars) != null ? Type.countries
                                : DOMINOS.containsAll(chars) ? Type.dominos
                                        : CARDS.containsAll(chars) ? Type.cards
                                                : MAHJONG.containsAll(chars) ? Type.majong
                                                        : Type.misc;
                EnumMap<Source, UnicodeSet> counter = data.get(type);
                if (counter == null) {
                    data.put(type, counter = new EnumMap<Source, UnicodeSet>(Source.class));
                }
                UnicodeSet us = counter.get(source);
                if (us == null) {
                    counter.put(source, us = new UnicodeSet());
                }
                us.add(chars);
            }
        }
        public void write() throws IOException {
            PrintWriter out = BagFormatter.openUTF8Writer(OUTPUT_DIR, 
                    "missing-emoji-list.html");
            EnumSet<Source> skipRef = EnumSet.allOf(Source.class);
            skipRef.remove(Source.ref);
            writeHeader(out, "Missing", "Missing list of emoji characters");
            String headerRow = "<tr><th>" + "Type" + "</th>";
            for (Source type : skipRef) {
                headerRow +="<th width='" + (80.0/skipRef.size()) + "%'>" + type + "</th>";
            }
            headerRow += "</tr>";

            for (Entry<Type, EnumMap<Source, UnicodeSet>> entry : data.entrySet()) {
                EnumMap<Source, UnicodeSet> values = entry.getValue();

                // find common
                UnicodeSet common = null;
                boolean skipSeparate = true;
                for (Source source : skipRef) {
                    final UnicodeSet us = ifNull(values.get(source), UnicodeSet.EMPTY);
                    if (common == null) {
                        common = new UnicodeSet(us);
                    } else if (!common.equals(us)) {
                        common.retainAll(us);
                        skipSeparate = false;
                    }
                }
                out.println(headerRow);
                // per source
                String sectionLink = getDoubleLink(entry.getKey().toString());
                if (!skipSeparate) {
                    out.print("<tr><th>" + sectionLink + " count</th>");
                    sectionLink = entry.getKey().toString();
                    for (Source source : skipRef) {
                        final UnicodeSet us = ifNull(values.get(source), UnicodeSet.EMPTY);
                        out.print("<td class='cchars'>" + (us.size() - common.size()) + "</td>");
                    }
                    out.print("</tr>");
                    out.print("<tr><th>" + entry.getKey() + " chars</th>");
                    for (Source source : skipRef) {
                        final UnicodeSet us = ifNull(values.get(source), UnicodeSet.EMPTY);
                        displayUnicodeSet(out, new UnicodeSet(us).removeAll(common), 
                                Style.bestImage, false, 1, null);
                    }
                    out.print("</tr>");
                }
                // common
                out.println("<tr><th>" + sectionLink + " count (common)</th>"
                        +"<td class='cchars' colSpan='" + skipRef.size() + "'>"
                        + common.size() + "</td></tr>");
                out.println("<tr><th>" + entry.getKey() + " (common)</th>");
                displayUnicodeSet(out, common, Style.bestImage, false, skipRef.size(), null);
                out.println("</td></tr>");
            }
            writeFooter(out);
            out.close();
        }
    }

    public static int getResponseCode(String urlString) {
        try {
            URL u = new URL(urlString); 
            HttpURLConnection huc =  (HttpURLConnection)  u.openConnection(); 
            huc.setRequestMethod("GET"); 
            huc.connect(); 
            return huc.getResponseCode();
        } catch (Exception e) {
            return -1;
        }
    }
    static final UnicodeSet VERSION70 = VERSION.getSet(UcdPropertyValues.Age_Values.V7_0.toString());

    static final Birelation<String,String> OLD_ANNOTATIONS_TO_CHARS = new Birelation<>(
            new TreeMap(CODEPOINT_COMPARE), 
            new TreeMap(CODEPOINT_COMPARE), 
            TreeSet.class, 
            TreeSet.class, 
            CODEPOINT_COMPARE, 
            CODEPOINT_COMPARE);
    static {
        addOldAnnotations();
    }

    private static void compareOtherAnnotations() {
        for (Entry<String, Set<String>> entry : OLD_ANNOTATIONS_TO_CHARS.valueKeysSet()) {
            String chars = entry.getKey();
            Set<String> oldAnnotations = entry.getValue();

            Set<String> newAnnotations = new TreeSet(ifNull(ANNOTATIONS_TO_CHARS.getKeys(chars), Collections.EMPTY_SET));
            Set<Label> labels = ifNull(Label.CHARS_TO_LABELS.getValues(chars), Collections.EMPTY_SET);
            for (Label label : labels) {
                newAnnotations.add(label.toString());
            }

            if (!Objects.equals(newAnnotations, oldAnnotations)) {
                TreeSet oldNotNew = new TreeSet(oldAnnotations);
                oldNotNew.removeAll(newAnnotations);
                TreeSet newNotOld = new TreeSet(newAnnotations);
                newNotOld.removeAll(oldAnnotations);
                TreeSet both = new TreeSet(newAnnotations);
                both.retainAll(oldAnnotations);
                System.out.println(getCodeAndName(chars, "\t")
                        + "\t" + CollectionUtilities.join(oldNotNew, ", ")
                        + "\t" + CollectionUtilities.join(newNotOld, ", ")
                        + "\t" + CollectionUtilities.join(both, ", ")
                        );
            }
        }
    }

    static String getFlagCode(String chars) {
        int firstCodepoint = chars.codePointAt(0);
        if (!Emoji.isRegionalIndicator(firstCodepoint)) {
            return null;
        }
        int firstLen = Character.charCount(firstCodepoint);
        int secondCodepoint = firstLen >= chars.length() ? 0 : chars.codePointAt(firstLen);
        if (!Emoji.isRegionalIndicator(secondCodepoint)) {
            return null;
        }
        secondCodepoint = chars.codePointAt(2);
        String cc = (char)(firstCodepoint - Emoji.FIRST_REGIONAL + 'A') 
                + ""
                + (char)(secondCodepoint - Emoji.FIRST_REGIONAL + 'A');
        //        String remapped = REMAP_FLAGS.get(cc);
        //        if (remapped != null) {
        //            cc = remapped;
        //        }
        //        if (REPLACEMENT_CHARACTER.equals(cc)) {
        //            return null;
        //        }
        return cc;
    }

    private static void addOldAnnotations() {
        for (String line : FileUtilities.in(GenerateEmoji.class, "oldEmojiAnnotations.txt")) {
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            // U+00AE   Registered symbol, Registered
            String[] fields = line.split("\t");
            String[] codes = fields[0].split("U+");
            StringBuilder realChars = new StringBuilder();
            for (String code : codes) {
                if (code.isEmpty()) {
                    continue;
                }
                int codePoint = Integer.parseInt(code, 16);
                realChars.appendCodePoint(codePoint);
            }
            //            String realChars = ANDROID_REMAP_VALUES.get(codepoint);
            //            if (realChars == null) {
            //                realChars = new StringBuilder().appendCodePoint(codepoint).toString();
            //            }
            if (NAME.get(realChars.codePointAt(0)) == null ) {
                if (SHOW) System.out.println("skipping private use: " + Integer.toHexString(realChars.codePointAt(0)));
                continue;
            }
            addWords(realChars.toString(), fields[1]);
        }
    }


    public static void addWords(String chars, String name) {
        if (OLD_ANNOTATIONS_TO_CHARS.getKeys(chars) != null) {
            throw new IllegalArgumentException("duplicate");
        }
        String nameString = name.replaceAll("[^-A-Za-z:]+", " ").toLowerCase(Locale.ENGLISH);
        for (String word : nameString.split(" ")) {
            if (!SKIP_WORDS.contains(word) && word.length() > 1 && getFlagCode(chars) == null) {
                OLD_ANNOTATIONS_TO_CHARS.add(word, chars);
            }
        }
    }

    static String getFlag(String chars) {
        String core = Emoji.buildFileName(chars,"_");
        String filename = getImageFilename(Source.ref, core);
        String cc = getFlagRegionName(chars);
        return cc == null ? null : "<img alt='" + chars
                + "' class='imgf' src='images/" + filename + "'>";
    }

    public static void main(String[] args) throws IOException {
        for (String line : FileUtilities.in(GenerateEmoji.class, "emojiData.txt")) {
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            Data.add(line);
        }
        for (String s : Label.CHARS_TO_LABELS.keySet()) {
            if (!Data.STRING_TO_DATA.containsKey(s)) {
                addNewItem(s, Data.STRING_TO_DATA);
            }
        }
        for (String s : EXTRAS) {
            if (!Data.STRING_TO_DATA.containsKey(s)) {
                addNewItem(s, Data.STRING_TO_DATA);
                if (SHOW) System.out.println(s);
            }
        }
        LinkedHashMap missingMap = new LinkedHashMap();
        addFileCodepoints(new File(IMAGES_OUTPUT_DIR), missingMap);
        UnicodeSet fileChars = new UnicodeSet().addAll(missingMap.keySet()).removeAll(Emoji.EMOJI_CHARS);
        System.out.println("MISSING: " + fileChars.toPattern(false));

        // show data
        UnicodeSet newItems = new UnicodeSet();
        newItems.addAll(Data.STRING_TO_DATA.keySet());
        newItems.removeAll(JSOURCES);
        UnicodeSet newItems70 = new UnicodeSet(newItems).retainAll(VERSION70);
        UnicodeSet newItems63 = new UnicodeSet(newItems).removeAll(newItems70);
        UnicodeSet newItems63flags = getStrings(newItems63);
        newItems63.removeAll(newItems63flags);
        System.out.println("Other 6.3 Flags:\t" + newItems63flags.size() + "\t" + newItems63flags);
        System.out.println("Other 6.3:\t" + newItems63.size() + "\t" + newItems63);
        System.out.println("Other 7.0:\t" + newItems70.size() + "\t" + newItems70);
        //Data.missingJSource.removeAll(new UnicodeSet("[\\u2002\\u2003\\u2005]"));
        if (Data.missingJSource.size() > 0) {
            throw new IllegalArgumentException("Missing: " + Data.missingJSource);
        }
        //print(Form.imagesOnly, columns, Data.STRING_TO_DATA);
        //print(Form.shortForm, Data.STRING_TO_DATA);
        //        System.out.println(Data.LABELS_TO_DATA.keySet());

        Stats stats = new Stats();
        print(Form.fullForm, Data.STRING_TO_DATA, stats);
        stats.write();
        print(Form.noImages, Data.STRING_TO_DATA, null);
        print(Form.extraForm, missingMap, null);

        for (String e : Emoji.EMOJI_CHARS) {
            Data data = Data.STRING_TO_DATA.get(e);
            if (data == null) {
                STYLE_TO_CHARS.put(Style.text, e);
            } else {
                STYLE_TO_CHARS.put(data.defaultPresentation, e);
            }
        }
        STYLE_TO_CHARS.freeze();
        showOrdering(Style.bestImage);
        showOrdering(Style.refImage);
        showLabels();
        showDefaultStyle();
        showSubhead();
        showAnnotations();
        compareOtherAnnotations();
        showOtherUnicode();
        test();
        // check twitter glyphs

        if (SHOW) {
            System.out.println("static final UnicodeSet EMOJI_CHARS = new UnicodeSet(\n\"" + Data.DATA_CHARACTERS.toPattern(false) + "\");");
            // getUrlCharacters("TWITTER", TWITTER_URL);
            //getUrlCharacters("APPLE", APPLE_URL);
            System.out.println(new UnicodeSet(Emoji.GITHUB_APPLE_CHARS).removeAll(APPLE_CHARS).toPattern(false));
            System.out.println(list(new UnicodeSet(APPLE_CHARS).removeAll(Emoji.GITHUB_APPLE_CHARS)));
        }
    }

    private static <U> U ifNull(U keys, U defaultValue) {
        return keys == null ? defaultValue : keys;
    }

    public static void addFileCodepoints(File imagesOutputDir, Map<String, Data> results) {
        for (File file : imagesOutputDir.listFiles()) {
            if (file.isDirectory()) {
                addFileCodepoints(file, results);
                continue;
            }
            String s = file.getName();
            String original = s;
            if (s.startsWith(".") || !s.endsWith(".png")) {
                continue;
            }
            String chars = Emoji.parseFileName(s, "_");
            addNewItem(chars, results);
        }
    }

    static final UnicodeSet WINDOWS_CHARS = new UnicodeSet();

    private static String extractCodes(String s, String prefix, UnicodeSet chars) {
        if (!s.startsWith(prefix)) {
            return null;
        }
        String[] parts = s.substring(prefix.length()).split("[-_]");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            int codePoint = Integer.parseInt(part,16);
            if (codePoint >= 0xF0000) {
                return "";
            }
            result.appendCodePoint(codePoint);
        }
        String stringResult = result.toString();
        if (chars != null) {
            chars.add(stringResult);
        }
        return stringResult;
    }

    private static String list(UnicodeSet uset) {
        return CollectionUtilities.join(With.in(uset).toList(), " ");
    }

    public static void getUrlCharacters(String title, Transform<String, String> transform) {
        //http://emojistatic.github.io/images/32/1f4ab.png
        UnicodeSet twitterChars = new UnicodeSet();
        int limit = 0;
        for (String s : Data.DATA_CHARACTERS) {
            String twitterUrl = transform.transform(s);
            if (getResponseCode(twitterUrl) == 200) {
                twitterChars.add(s);
            }
            if ((limit++ % 50) == 0) {
                System.out.println(limit + "\t" + s);
            }
        }
        System.out.println("static final UnicodeSet " +
                title +
                "_CHARS = new UnicodeSet(\n\""
                + twitterChars.toPattern(false) + "\");");
    }

    static final UnicodeSet APPLE_CHARS = new UnicodeSet(
            "[©®‼⁉™ℹ↔-↙↩↪⌚⌛⏩-⏬⏰⏳Ⓜ▪▫▶◀◻-◾☀☁☎☑☔☕☝☺♈-♓♠♣♥♦♨♻♿⚓⚠⚡⚪⚫⚽⚾⛄⛅⛎⛔⛪⛲⛳⛵⛺⛽✂✅✈-✌✏✒✔✖✨✳✴❄❇❌❎❓-❕❗❤➕-➗➡➰➿⤴⤵⬅-⬇⬛⬜⭐⭕〰〽㊗㊙🀀-🀫🀰-🂓🂠-🂮🂱-🂿🃁-🃏🃑-🃵🅰🅱🅾🅿🆊🆎🆏🆑-🆚🇦-🇿🈁🈂🈚🈯🈲-🈺🉐🉑🌀-🌬🌰-🍽🎀-🏎🏔-🏷🐀-📾🔀-🔿🕊🕐-🕹🕻-🖣🖥-🙂🙅-🙏🙬-🙯🚀-🛏🛠-🛬🛰-🛳{#⃣}{0⃣}{1⃣}{2⃣}{3⃣}{4⃣}{5⃣}{6⃣}{7⃣}{8⃣}{9⃣}{🇨🇳}{🇩🇪}{🇪🇸}{🇫🇷}{🇬🇧}{🇮🇹}{🇯🇵}{🇰🇷}{🇷🇺}{🇺🇸}]");

    static final UnicodeSet TWITTER_CHARS = new UnicodeSet(
            "[©®‼⁉™ℹ↔-↙↩↪⌚⌛⏩-⏬⏰⏳Ⓜ▪▫▶◀◻-◾☀☁☎☑☔☕☝☺♈-♓♠♣♥♦♨♻♿⚓⚠⚡⚪⚫⚽⚾⛄⛅⛎⛔⛪⛲⛳⛵⛺⛽✂✅✈-✌✏✒✔✖✨✳✴❄❇❌❎❓-❕❗❤➕-➗➡➰➿⤴⤵⬅-⬇⬛⬜⭐⭕〰〽㊗㊙🀄🃏🅰🅱🅾🅿🆎🆑-🆚🇦-🇿🈁🈂🈚🈯🈲-🈺🉐🉑🌀-🌠🌰-🌵🌷-🍼🎀-🎓🎠-🏄🏆-🏊🏠-🏰🐀-🐾👀👂-📷📹-📼🔀-🔽🕐-🕧🗻-🙀🙅-🙏🚀-🛅{#⃣}{0⃣}{1⃣}{2⃣}{3⃣}{4⃣}{5⃣}{6⃣}{7⃣}{8⃣}{9⃣}{🇨🇳}{🇩🇪}{🇪🇸}{🇫🇷}{🇬🇧}{🇮🇹}{🇯🇵}{🇰🇷}{🇷🇺}{🇺🇸}]");

    private static UnicodeSet getStrings(UnicodeSet us) {
        UnicodeSet result = new UnicodeSet();
        for (String s : us) {
            if (Character.charCount(s.codePointAt(0)) != s.length()) {
                result.add(s);
            }
        }
        return result;
    }

    private static void showLabels() throws IOException {
        PrintWriter out = BagFormatter.openUTF8Writer(OUTPUT_DIR, "emoji-labels.html");
        writeHeader(out, "Emoji Labels", "Main categories for character picking. " +
                "Characters may occur more than once. " +
                "Categories could be grouped in the UI.");
        for (Entry<Label, Set<String>> entry : Label.CHARS_TO_LABELS.valueKeysSet()) {
            Label label = entry.getKey();
            Set<String> set = entry.getValue();
            String word = label.toString();
            Set<String> values = entry.getValue();
            UnicodeSet uset = new UnicodeSet().addAll(values);

            displayUnicodeset(out, word, null, uset, Style.bestImage);
        }
        writeFooter(out);
        out.close();
    }

    private static void showOrdering(Style style) throws IOException {
        PrintWriter out = BagFormatter.openUTF8Writer(OUTPUT_DIR, 
                (style == Style.bestImage ? "" : "ref-") + "emoji-ordering.html");
        writeHeader(out, "Emoji Ordering", "Proposed ordering for better results than default. " +
                "Note: the categories are to assist in developing the ordering, " +
                "and wouldn't be surfaced to users.");
        for (Entry<String, Set<String>> entry : ORDERING_TO_CHAR.keyValuesSet()) {
            displayUnicodeset(out, entry.getKey(), null, new UnicodeSet().addAll(entry.getValue()), style);
        }
        writeFooter(out);
        out.close();
    }

    private static void showDefaultStyle() throws IOException {
        PrintWriter out = BagFormatter.openUTF8Writer(OUTPUT_DIR, "emoji-style.html");
        writeHeader(out, "Emoji Default Style Values", "Default Style Values for display.");
        for (Entry<Style, Set<String>> entry : STYLE_TO_CHARS.keyValuesSet()) {
            Style label = entry.getKey();
            Set<String> set = entry.getValue();
            String word = label.toString();
            Set<String> values = entry.getValue();
            UnicodeSet plain = new UnicodeSet();
            UnicodeSet emoji = new UnicodeSet();
            UnicodeSet text = new UnicodeSet();
            for (String value : values) {
                plain.add(value);
                String emojiStyle = getEmojiVariant(value, EMOJI_VARIANT_STRING);
                if (!value.equals(emojiStyle)) {
                    emoji.add(value);
                }
                String textStyle = getEmojiVariant(value, TEXT_VARIANT_STRING);
                if (!value.equals(textStyle)) {
                    text.add(value);
                }
            }
            displayUnicodeset(out, word, "", plain, Style.plain);
            displayUnicodeset(out, word, "with emoji variant", emoji, Style.emoji);
            displayUnicodeset(out, word, "with text variant", text, Style.text);
        }
        writeFooter(out);
        out.close();
    }



    private static void showSubhead() throws IOException {
        Map<String, UnicodeSet> subheadToChars = new TreeMap();
        for (String s : Data.DATA_CHARACTERS) {
            int firstCodepoint = s.codePointAt(0);
            String header = Default.ucd().getBlock(firstCodepoint).replace('_', ' ');
            String subhead = subheader.getSubheader(firstCodepoint);
            if (subhead == null) {
                subhead = "UNNAMED";
            }
            header = header.contains(subhead) ? header : header + ": " + subhead;
            UnicodeSet uset = subheadToChars.get(header);
            if (uset == null) {
                subheadToChars.put(header, uset = new UnicodeSet());
            }
            uset.add(s);
        }
        PrintWriter out = BagFormatter.openUTF8Writer(OUTPUT_DIR, "emoji-subhead.html");
        writeHeader(out, "Emoji Subhead", "Unicode Subhead mapping.");
        for (Entry<String, UnicodeSet> entry : subheadToChars.entrySet()) {
            String label = entry.getKey();
            UnicodeSet uset = entry.getValue();
            if (label.equalsIgnoreCase("exclude")) {
                continue;
            }
            displayUnicodeset(out, label, null, uset, Style.emoji);
        }
        writeFooter(out);
        out.close();
    }


    private static void showAnnotations() throws IOException {
        PrintWriter out = BagFormatter.openUTF8Writer(OUTPUT_DIR, "emoji-annotations.html");
        writeHeader(out, "Emoji Annotations", "Finer-grained character annotations. " +
                "For brevity, flags are not shown: they would have names of the associated countries.");

        Relation<UnicodeSet, String> seen = Relation.of(new HashMap(), TreeSet.class, CODEPOINT_COMPARE);
        for (Entry<String, Set<String>> entry : ANNOTATIONS_TO_CHARS.keyValuesSet()) {
            String word = entry.getKey();
            Set<String> values = entry.getValue();
            UnicodeSet uset = new UnicodeSet().addAll(values);
            try {
                Label label = Label.valueOf(word);
                continue;
            } catch (Exception e) {
            }
            seen.put(uset, word);
        }
        Set<String> labelSeen = new HashSet<>();
        Relation<Set<String>, String> setOfCharsToKeys = ANNOTATIONS_TO_CHARS.getValuesToKeys();

        for (Entry<String, Set<String>> entry : ANNOTATIONS_TO_CHARS.keyValuesSet()) {
            String word = entry.getKey();
            if (labelSeen.contains(word)) {
                continue;
            }
            Set<String> values = entry.getValue();
            Set<String> words = setOfCharsToKeys.get(values);
            labelSeen.addAll(words);
            UnicodeSet uset = new UnicodeSet().addAll(values);
            //            Set<String> words = seen.getAll(uset);
            //            if (words == null || labelSeen.contains(words)) {
            //                continue;
            //            }
            //            labelSeen.add(words);
            displayUnicodeset(out, words, null, uset, Style.bestImage, "full-emoji-list.html");
        }
        writeFooter(out);
        out.close();
    }


    static final UnicodeSet EXCLUDE_SET = new UnicodeSet()
    .addAll(GENERAL_CATEGORY.getSet(General_Category_Values.Unassigned.toString()))
    .addAll(GENERAL_CATEGORY.getSet(General_Category_Values.Private_Use.toString()))
    .addAll(GENERAL_CATEGORY.getSet(General_Category_Values.Surrogate.toString()))
    .addAll(GENERAL_CATEGORY.getSet(General_Category_Values.Control.toString()))
    .addAll(GENERAL_CATEGORY.getSet(General_Category_Values.Nonspacing_Mark.toString()))
    ;

    private static void showOtherUnicode() throws IOException {
        Map<String, UnicodeSet> labelToUnicodeSet = new TreeMap();

        getLabels("otherLabels.txt", labelToUnicodeSet);
        getLabels("otherLabelsComputed.txt", labelToUnicodeSet);
        UnicodeSet symbolMath = LATEST.load(UcdProperty.Math).getSet(Binary.Yes.toString());
        UnicodeSet symbolMathAlphanum = new UnicodeSet()
        .addAll(LATEST.load(UcdProperty.Alphabetic).getSet(Binary.Yes.toString()))
        .addAll(GENERAL_CATEGORY.getSet(General_Category_Values.Decimal_Number.toString()))
        .addAll(GENERAL_CATEGORY.getSet(General_Category_Values.Letter_Number.toString()))
        .addAll(GENERAL_CATEGORY.getSet(General_Category_Values.Other_Number.toString()))
        .retainAll(symbolMath);
        symbolMath.removeAll(symbolMathAlphanum);
        addSet(labelToUnicodeSet, "Symbol-Math", symbolMath);
        addSet(labelToUnicodeSet, "Symbol-Math-Alphanum", symbolMathAlphanum);
        addSet(labelToUnicodeSet, "Symbol-Braille", 
                LATEST.load(UcdProperty.Block).getSet(Block_Values.Braille_Patterns.toString()));
        addSet(labelToUnicodeSet, "Symbol-APL", new UnicodeSet("[⌶-⍺ ⎕]"));

        UnicodeSet otherSymbols = new UnicodeSet()
        .addAll(GENERAL_CATEGORY.getSet(General_Category_Values.Math_Symbol.toString()))
        .addAll(GENERAL_CATEGORY.getSet(General_Category_Values.Other_Symbol.toString()))
        .removeAll(NFKCQC.getSet(Binary.No.toString()))
        .removeAll(Data.DATA_CHARACTERS)
        .retainAll(COMMON_SCRIPT);
        ;
        UnicodeSet otherPunctuation = new UnicodeSet()
        .addAll(GENERAL_CATEGORY.getSet(General_Category_Values.Close_Punctuation.toString()))
        .addAll(GENERAL_CATEGORY.getSet(General_Category_Values.Connector_Punctuation.toString()))
        .addAll(GENERAL_CATEGORY.getSet(General_Category_Values.Dash_Punctuation.toString()))
        .addAll(GENERAL_CATEGORY.getSet(General_Category_Values.Final_Punctuation.toString()))
        .addAll(GENERAL_CATEGORY.getSet(General_Category_Values.Initial_Punctuation.toString()))
        .addAll(GENERAL_CATEGORY.getSet(General_Category_Values.Math_Symbol.toString()))
        .addAll(GENERAL_CATEGORY.getSet(General_Category_Values.Open_Punctuation.toString()))
        .addAll(GENERAL_CATEGORY.getSet(General_Category_Values.Other_Punctuation.toString()))
        .removeAll(NFKCQC.getSet(Binary.No.toString()))
        .removeAll(Data.DATA_CHARACTERS)
        .retainAll(COMMON_SCRIPT);
        ;

        for (Entry<String, UnicodeSet> entry : labelToUnicodeSet.entrySet()) {
            UnicodeSet uset = entry.getValue();
            uset.removeAll(Emoji.EMOJI_CHARS);
            otherSymbols.removeAll(uset);
            otherPunctuation.removeAll(uset);
        }
        if (!otherPunctuation.isEmpty()) {
            addSet(labelToUnicodeSet, "Punctuation-Other", otherPunctuation);
        }
        if (!otherSymbols.isEmpty()) {
            addSet(labelToUnicodeSet, "Symbol-Other", otherSymbols);
        }

        PrintWriter out = BagFormatter.openUTF8Writer(OUTPUT_DIR, "other-labels.html");
        writeHeader(out, "Other Labels", "Draft categories for other Symbols and Punctuation.");

        for (Entry<String, UnicodeSet> entry : labelToUnicodeSet.entrySet()) {
            String label = entry.getKey();
            UnicodeSet uset = entry.getValue();
            if (label.equalsIgnoreCase("exclude")) {
                continue;
            }
            displayUnicodeset(out, label, null, uset, Style.plain);
        }

        writeFooter(out);
        out.close();
    }
    public static void getLabels(String string, Map<String, UnicodeSet> labelToUnicodeSet) {
        String lastLabel = null;
        for (String line : FileUtilities.in(GenerateEmoji.class, string)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            char first = line.charAt(0);
            if ('a' <= first && first <= 'z' || 'A' <= first && first <= 'Z') {
                if (!ASCII_LETTER_HYPHEN.containsAll(line)) {
                    throw new IllegalArgumentException();
                }
                lastLabel = line;
            } else {
                UnicodeSet set = new UnicodeSet("[" + line
                        .replace("&", "\\&")
                        .replace("\\", "\\\\")
                        .replace("[", "\\[")
                        .replace("]", "\\]")
                        .replace("^", "\\^")
                        .replace("{", "\\{")
                        .replace("-", "\\-")
                        + "]");
                addSet(labelToUnicodeSet, lastLabel, set);
            }
        }
    }

    public static <T> void addSet(Map<T, UnicodeSet> labelToUnicodeSet, T key, UnicodeSet set) {
        UnicodeSet s = labelToUnicodeSet.get(key);
        if (s == null) {
            labelToUnicodeSet.put(key, s = new UnicodeSet());
        }
        s.addAll(set).removeAll(EXCLUDE_SET);
    }

    public static void displayUnicodeset(PrintWriter out, String label,
            String sublabel, UnicodeSet uset, Style showEmoji) {
        displayUnicodeset(out, Collections.singleton(label), sublabel, uset, showEmoji, null);
    }

    public static void displayUnicodeset(PrintWriter out, Set<String> labels,
            String sublabel, UnicodeSet uset, Style showEmoji, String link) {
        out.print("<tr><td>");
        boolean first = true;
        for (String label : labels) {
            if (!first) {
                out.print(", ");
            }
            first = false;
            String anchor = (sublabel == null || sublabel.isEmpty() ? label : label + "_" + sublabel);
            getDoubleLink(anchor, label);
            out.print(getDoubleLink(anchor, label));
        }
        out.println("</td>");
        if (sublabel != null) {
            out.println("<td>" + sublabel + "</td>");
        }
        if (SHOW) System.out.println(labels + "\t" + uset.size());
        displayUnicodeSet(out, uset, showEmoji, true, 1, link);
        out.println("</tr>");
    }

    public static void displayUnicodeSet(PrintWriter out,
            UnicodeSet uset, Style showEmoji, boolean addLineBreaks, int colSpan, String link) {
        out.println("<td class='lchars'"
                + (colSpan <= 1 ? "" : " colSpan='" + colSpan + "'")
                + ">");
        Set<String> sorted = uset.addAllTo(new TreeSet(CODEPOINT_COMPARE));
        int count = 0;
        for (String s : sorted) {
            if (count == 0) {
                out.print("\n");
            } else if (addLineBreaks && (count & 0xF) == 0) {
                out.print("<br>");
            } else {
                out.print(" ");
            }
            ++count;
            if (link != null) {
                out.print("<a href='" + link + "#" + Emoji.buildFileName(s,"_") + "' target='full'>");
            }
            String cell = getFlag(s);
            if (cell == null) {
                switch (showEmoji) {
                case text:
                case emoji: 
                    cell = getEmojiVariant(s, showEmoji == Style.emoji ? EMOJI_VARIANT_STRING : TEXT_VARIANT_STRING);
                    cell = "<span title='" +
                            getHex(s) + " " + getName(s) + "'>" 
                            + cell
                            + "</span>";
                    break;
                case plain: 
                    cell = "<span title='" +
                            getHex(s) + " " + getName(s) + "'>" 
                            + s
                            + "</span>";
                    break;
                case bestImage:
                    cell = getBestImage(s);
                    break;
                case refImage:
                    cell = getImage(Source.ref, s);
                    break;
                }
            }
            out.print(cell);
            if (link != null) {
                out.print("</a>");
            }
        }
        out.println("</td>");
    }


    public static String getName(String s) {
        String flag = getFlagRegionName(s);
        if (flag != null) {
            String result = LOCALE_DISPLAY.regionDisplayName(flag);
            if (result.endsWith(" SAR China")) {
                result = result.substring(0, result.length() - " SAR China".length());
            }
            return "flag for " + result;
        }
        final String name = NAME.get(s.codePointAt(0));
        if (s.indexOf(Emoji.ENCLOSING_KEYCAP) >= 0) {
            return "keycap " + name.toLowerCase(Locale.ENGLISH);
        }
        return name == null ? "UNNAMED" : name.toLowerCase(Locale.ENGLISH);
    }

    public static String getFlagRegionName(String s) {
        String result = getFlagCode(s);
        if (result != null) {
            result = LOCALE_DISPLAY.regionDisplayName(result);
            if (result.endsWith(" SAR China")) {
                result = result.substring(0, result.length() - " SAR China".length());
            }
        }
        return result;
    }
    public static String getHex(String theChars) {
        return "U+" + Utility.hex(theChars, " U+");
    }

    public static String getCodeAndName(String chars1, String separator) {
        return getHex(chars1) + separator + chars1 + separator + getName(chars1);
    }

    public static void addNewItem(String s, Map<String, Data> missingMap) {
        if (!Data.DATA_CHARACTERS.contains(s)) {
            addNewItem(new Data(s), missingMap);
        }
    }

    public static void addNewItem(Data item, Map<String, Data> missingMap) {
        if (item == null || EXCLUDE.contains(item.chars)) {
            return;
        }
        if (missingMap.containsKey(item.chars)) {
            throw new IllegalArgumentException(item.toString());
        }
        missingMap.put(item.chars, item);
        Data.missingJSource.remove(item.chars);
    }

    enum Form {
        shortForm("short", " short form."), 
        noImages("", " without images."), 
        fullForm("full", "with images."), 
        extraForm("extra", " with images; have icons but are not including.");
        final String filePrefix;
        final String title;
        final String description;
        Form(String prefix, String description) {
            this.description = description;
            filePrefix = prefix.isEmpty() ? "" : prefix + "-";
            title = "Emoji Data"
                    + (prefix.isEmpty() ? "" : " (" + UCharacter.toTitleCase(prefix, null) + ")");
        }
    }

    public static <T> void print(Form form, Map<String, Data> set, Stats stats) throws IOException {
        PrintWriter out = BagFormatter.openUTF8Writer(OUTPUT_DIR, 
                form.filePrefix + "emoji-list.html");
        PrintWriter outText = null;
        int order = 0;
        if (stats != null) {
            outText = BagFormatter.openUTF8Writer(OUTPUT_DIR, "emoji-data.txt");
            outText.println("# DRAFT emoji-data.txt");
            outText.println("# For details about the format and other information, see http://unicode.org/draft/reports/tr51/tr51.html.");
            outText.println("#");
            outText.println("# Format");
            outText.println("# Code ;\tDefault Style ;\tOrdering ;\tAnnotations\t#\tVersion\tName");
            outText.println("#");
        }
        writeHeader(out, form.title, "List of emoji characters, " + form.description);
        out.println(Data.toHtmlHeaderString(form));
        int item = 0;
        for (Data data : new TreeSet<Data>(set.values())) {
            out.println(data.toHtmlString(form, ++item, stats));
            if (outText != null) {
                outText.println(data.toSemiString(order++));
            }
        }
        writeFooter(out);
        out.close();
        if (outText != null) {
            outText.close();
        }
    }

    static final String FOOTER = "</table>" + Utility.repeat("<br>", 60) + "</body></html>";
    public static void writeFooter(PrintWriter out) {
        out.println(FOOTER);
    }

    public static void writeHeader(PrintWriter out, String title, String firstLine) {
        out.println("<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\">\n" +
                "<html>\n" +
                "<head>\n" +
                "<meta http-equiv='Content-Type' content='text/html; charset=UTF-8'>\n" +
                "<link rel='stylesheet' type='text/css' href='emoji-list.css'>\n" +
                "<title>Draft " +
                title +
                "</title>\n" +
                "</head>\n" +
                "<body>\n"
                + "<h1>Draft " + title + "</h1>\n"
                + "<p>" + firstLine +
                " For details about the format and other information, see <a target='text' href='http://unicode.org/draft/reports/tr51/tr51.html#Data_Files'>Unicode Emoji</a>.</p>\n"
                + "<table>");
    }

    static boolean CHECKFACE = false;

    static void test() throws IOException {
        PrintWriter out = BagFormatter.openUTF8Writer(OUTPUT_DIR, "emoji-diff.html");
        writeHeader(out, "Diff List", "Differences from other categories.");

        UnicodeSet AnimalPlantFood = new UnicodeSet("[☕ 🌰-🌵 🌷-🍼 🎂 🐀-🐾]");
        testEquals(out, "AnimalPlantFood", AnimalPlantFood, Label.nature, Label.food);

        UnicodeSet Object = new UnicodeSet("[⌚ ⌛ ⏰ ⏳ ☎ ⚓ ✂ ✉ ✏ 🎀 🎁 👑-👣 💄 💉 💊 💌-💎 💐 💠 💡 💣 💮 💰-📷 📹-📼 🔋-🔗 🔦-🔮 🕐-🕧]");
        testEquals(out, "Object", Object, Label.object, Label.office, Label.clothing);

        CHECKFACE=true;
        UnicodeSet PeopleEmotion = new UnicodeSet("[☝ ☺ ✊-✌ ❤ 👀 👂-👐 👤-💃 💅-💇 💋 💏 💑 💓-💟 💢-💭 😀-🙀 🙅-🙏]");
        testEquals(out, "PeopleEmotion", PeopleEmotion, Label.person, Label.body, Label.emotion, Label.face);
        CHECKFACE=false;

        UnicodeSet SportsCelebrationActivity = new UnicodeSet("[⛑ ⛷ ⛹ ♠-♧ ⚽ ⚾ 🀀-🀫 🂠-🂮 🂱-🂾 🃁-🃏 🃑-🃟 🎃-🎓 🎠-🏄 🏆-🏊 💒]");
        testEquals(out, "SportsCelebrationActivity", SportsCelebrationActivity, Label.game, Label.sport, Label.activity);

        UnicodeSet TransportMapSignage = new UnicodeSet("[♨ ♻ ♿ ⚠ ⚡ ⛏-⛡ ⛨-⛿ 🏠-🏰 💈 🗻-🗿 🚀-🛅]");
        testEquals(out, "TransportMapSignage", TransportMapSignage, Label.travel, Label.place);

        UnicodeSet WeatherSceneZodiacal = new UnicodeSet("[☀-☍ ☔ ♈-♓ ⛄-⛈ ⛎ ✨ 🌀-🌠 🔥]");
        testEquals(out, "WeatherSceneZodiacal", WeatherSceneZodiacal, Label.weather, Label.time);

        UnicodeSet Enclosed = new UnicodeSet("[[\u24C2\u3297\u3299][\\U0001F150-\\U0001F19A][\\U0001F200-\\U0001F202][\\U0001F210-\\U0001F23A][\\U0001F240-\\U0001F248][\\U0001F250-\\U0001F251]]");
        testEquals(out, "Enclosed", Enclosed, Label.word);

        UnicodeSet Symbols = new UnicodeSet("[[\\U0001F4AF][\\U0001F500-\\U0001F525][\\U0001F52F-\\U0001F53D][\\U0001F540-\\U0001F543[\u00A9\u00AE\u2002\u2003\u2005\u203C\u2049\u2122\u2139\u2194\u2195\u2196\u2197\u2198\u2199\u21A9\u21AA\u231B\u23E9\u23EA\u23EB\u23EC\u25AA\u25AB\u25B6\u25C0\u25FB\u25FC\u25FD\u25FE\u2611\u2660\u2663\u2665\u2666\u267B\u2693\u26AA\u26AB\u2705\u2708\u2712\u2714\u2716\u2728\u2733\u2734\u2744\u2747\u274C\u274E\u2753\u2754\u2755\u2757\u2764\u2795\u2796\u2797\u27A1\u27B0\u2934\u2935\u2B05\u2B06\u2B07\u2B1B\u2B1C\u2B50\u2B55\u3030\u303D]]]");
        testEquals(out, "Symbols", Symbols, Label.sign);

        UnicodeSet other = new UnicodeSet(get70(Label.values()))
        .removeAll(AnimalPlantFood)
        .removeAll(Object)
        .removeAll(PeopleEmotion)
        .removeAll(SportsCelebrationActivity)
        .removeAll(TransportMapSignage)
        .removeAll(WeatherSceneZodiacal)
        .removeAll(Enclosed)
        .removeAll(Symbols)
        ;

        testEquals(out, "Other", other, Label.flag, Label.sign, Label.arrow);

        UnicodeSet ApplePeople = new UnicodeSet("[☝☺✊-✌✨❤🌂🌟🎀🎩🎽🏃👀👂-👺👼👽 👿-💇💋-💏💑💓-💜💞💢💤-💭💼🔥😀-🙀🙅-🙏 🚶]");
        testEquals(out, "ApplePeople", ApplePeople, Label.person, Label.emotion, Label.face, Label.body, Label.clothing);

        UnicodeSet AppleNature = new UnicodeSet("[☀☁☔⚡⛄⛅❄⭐🌀🌁🌈🌊-🌕🌙-🌞🌠🌰-🌵 🌷-🌼🌾-🍄🐀-🐾💐💩]");
        testEquals(out, "AppleNature", AppleNature, Label.nature, Label.food, Label.weather);

        UnicodeSet ApplePlaces = new UnicodeSet("[♨⚓⚠⛪⛲⛵⛺⛽✈🇧-🇬🇮-🇰🇳🇵🇷-🇺 🌃-🌇🌉🎠-🎢🎪🎫🎭🎰🏠-🏦🏨-🏰💈💒💺📍 🔰🗻-🗿🚀-🚝🚟-🚩🚲]");
        testEquals(out, "ApplePlaces", ApplePlaces, Label.place, Label.travel);

        UnicodeSet AppleSymbols = new UnicodeSet("[©®‼⁉⃣™ℹ↔-↙↩↪⏩-⏬ Ⓜ▪▫▶◀◻-◾☑♈-♓♠♣♥♦♻♿⚪⚫⛎ ⛔✅✔✖✳✴❇❌❎❓-❕❗➕-➗➡➰➿⤴⤵ ⬅-⬇⬛⬜⭕〰〽㊗㊙🅰🅱🅾🅿🆎🆑-🆚🈁🈂🈚 🈯🈲-🈺🉐🉑🌟🎦🏧👊👌👎💙💛💟💠💢💮💯💱💲 💹📳-📶🔀-🔄🔗-🔤🔯🔱-🔽🕐-🕧🚫🚭-🚱 🚳🚷-🚼🚾🛂-🛅]");
        testEquals(out, "AppleSymbols", AppleSymbols, Label.sign, Label.game);

        UnicodeSet AppleTextOrEmoji = new UnicodeSet("[‼⁉ℹ↔-↙↩↪Ⓜ▪▫▶◀◻-◾☀☁☎ ☑☔☕☝☺♈-♓♠♣♥♦♨♻♿⚓⚠⚡⚪⚫⚰ ⚾✂✈✉✌✏✒✳✴❄❇❤➡⤴⤵⬅-⬇〽㊗㊙ 🅰🅱🅾🅿🈂🈷🔝{#⃣}{0⃣}{1⃣}{2 ⃣}{3⃣}{4⃣}{5⃣}{6⃣}{7⃣}{8 ⃣}{9⃣}{🇨🇳}{🇩🇪}{🇪🇸}{🇫🇷}{🇬🇧}{ 🇮🇹}{🇯🇵}{🇰🇷}{🇷🇺}{🇺🇸}]");
        UnicodeSet AppleOnlyEmoji = new UnicodeSet("[⌚⌛⏩-⏬⏰⏳⚽⛄⛅⛎⛔⛪⛲⛳⛵⛺⛽✅ ✊✋✨❌❎❓-❕❗➿⬛⬜⭐⭕🀄🃏🆎🆑-🆚🈁 🈚🈯🈲-🈶🈸-🈺🉐🉑🌀-🌠🌰-🌵🌷-🍼🎀-🎓 🎠-🏊🏠-🏰🐀-🐾👀👂-📷📹-📼🔀-🔘🔞-🔽 🕐-🕧🗻-🙀🙅-🙏🚀-🛅]");

        UnicodeSet AppleAll = new UnicodeSet(AppleTextOrEmoji).addAll(AppleOnlyEmoji);
        UnicodeSet AppleObjects = new UnicodeSet(AppleAll)
        .removeAll(ApplePeople)
        .removeAll(AppleNature)
        .removeAll(ApplePlaces)
        .removeAll(AppleSymbols);

        testEquals(out, "AppleObjects", AppleObjects, Label.flag, Label.sign, Label.arrow);

        writeFooter(out);
        out.close();
    }

    public static void testEquals(PrintWriter out, String title1, UnicodeSet AnimalPlantFood, 
            String title2, UnicodeSet labelNatureFood) {
        testContains(out, title1, AnimalPlantFood, title2, labelNatureFood);
        testContains(out, title2, labelNatureFood, title1, AnimalPlantFood);
    }

    public static void testEquals(PrintWriter out, String title1, UnicodeSet AnimalPlantFood, 
            Label... labels) {
        title1 = "<b>" + title1 + "</b>";
        for (Label label : labels) {
            testContains(out, title1, AnimalPlantFood, label.toString(), get70(label));
        }
        String title2 = CollectionUtilities.join(labels, "+");
        UnicodeSet labelNatureFood = get70(labels);
        testContains(out, title2, labelNatureFood, title1, AnimalPlantFood);
    }

    private static void testContains(PrintWriter out, String title, UnicodeSet container, String title2, UnicodeSet containee) {
        if (!container.containsAll(containee)) {
            UnicodeSet missing = new UnicodeSet(containee).removeAll(container);
            out.println("<tr><td>" + title + "</td>\n" +
                    "<td>" + "⊉" + "</td>\n" +
                    "<td>" + title2 + "</td>\n" +
                    "<td>" + missing.size() + "/" + containee.size() + "</td>\n" +
                    "<td class='lchars'>"); 
            boolean first = true;
            Set<String> sorted = new TreeSet<String>(CODEPOINT_COMPARE);
            missing.addAllTo(sorted);
            for (String s : sorted) {
                if (first) {
                    first = false;
                } else {
                    out.print("\n");
                }
                out.print("<span title='" + Default.ucd().getName(s) + "'>" 
                        + getEmojiVariant(s, EMOJI_VARIANT_STRING) 
                        + "</span>");
            }            
            out.println("</td></tr>");
        }
    }

    public static UnicodeSet get70(Label... labels) {
        UnicodeSet containee = new UnicodeSet();
        for (Label label : labels) {
            containee.addAll(Label.CHARS_TO_LABELS.getKeys(label));
        }
        containee.removeAll(VERSION70);
        //containee.retainAll(JSOURCES);
        return containee;
    }

    public static String getDoubleLink(String href, String anchorText) {
        href = href.replace(' ', '_').toLowerCase(Locale.ENGLISH);
        return "<a href='#" + href + "' name='" + href + "'>" + anchorText + "</a>";
    }
    public static String getLink(String href, String anchorText, String target) {
        href = href.replace(' ', '_').toLowerCase(Locale.ENGLISH);
        return "<a" +
        " href='" + href + "'" +
        (target == null ? "" : " target='" + target + "'") +
        ">" + anchorText + "</a>";
    }
    public static String getDoubleLink(String anchor) {
        return getDoubleLink(anchor, anchor);
    }

}