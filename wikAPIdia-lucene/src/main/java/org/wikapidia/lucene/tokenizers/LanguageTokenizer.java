package org.wikapidia.lucene.tokenizers;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.util.Version;
import org.wikapidia.core.lang.Language;
import org.wikapidia.lucene.TokenizerOptions;
import org.wikapidia.lucene.LuceneOptions;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * This class is based on a class of the same name from Brent Hecht, WikAPIdia.
 * I have updated everything to properly function consistently with Lucene 4.3.
 *
 * This class is used to generate Tokenizers for specific languages. It allows for
 * specifying different types of filters to apply to the child Tokenizers.
 *
 * There are currently 26 language-specific tokenizer subclasses.
 * Note that simple English is treated as standard English
 *
 * @author Ari Weiland
 *
 */
public abstract class LanguageTokenizer {

    private static final String STOP_WORDS = "src/main/resources/stopwords/";
    private static Map<Language, Class> tokenizerClasses;

    protected static LuceneOptions opts;

    protected final Version matchVersion;
    protected final boolean caseInsensitive;
    protected final boolean useStopWords;
    protected final boolean useStem;
    protected final Language language;

    protected LanguageTokenizer(Version version, TokenizerOptions tokenizerOptions, Language language) {
        this.matchVersion = version;
        this.caseInsensitive = tokenizerOptions.isCaseInsensitive();
        this.useStopWords = tokenizerOptions.doesUseStopWords();
        this.useStem = tokenizerOptions.doesUseStem();
        this.language = language;
    }

    public abstract TokenStream getTokenStream(TokenStream input, CharArraySet stemExclusionSet);

    public Tokenizer getTokenizer(Reader r) {
        return new StandardTokenizer(matchVersion, r);
    }

    public TokenizerOptions getTokenizerOptions() {
        TokenizerOptions options = new TokenizerOptions();
        if (caseInsensitive) options.caseInsensitive();
        if (useStopWords) options.useStopWords();
        if (useStem) options.useStem();
        return options;
    }

    public Language getLanguage() {
        return language;
    }

    /**
     * Returns an instance of a LanguageTokenizer for the specified language
     * with the filters specified by opts.
     *
     * @param language the language of the tokenizer to be retrieved
     * @param opts the LuceneOptions object
     * @return a LanguageTokenizer for language configured by opts
     */
    public static LanguageTokenizer getLanguageTokenizer(Language language, LuceneOptions opts) {
        try {
            LanguageTokenizer.opts = opts;
            mapTokenizers();
            if (language.equals(Language.getByLangCode("simple"))) language = Language.getByLangCode("en"); // simple english
            return (LanguageTokenizer) tokenizerClasses.get(language)                                       // is just english
                    .getDeclaredConstructor(
                            Version.class,
                            TokenizerOptions.class,
                            Language.class)
                    .newInstance(
                            opts.matchVersion,
                            opts.options,
                            language);
        } catch (Exception e) {
            throw new RuntimeException(e); // These exceptions are based on hard code and should never get thrown
        }
    }

    private static void mapTokenizers() {
        tokenizerClasses = new HashMap<Language, Class>();
        tokenizerClasses.put(Language.getByLangCode("en"), EnglishTokenizer.class);
        tokenizerClasses.put(Language.getByLangCode("de"), GermanTokenizer.class);
        tokenizerClasses.put(Language.getByLangCode("fr"), FrenchTokenizer.class);
        tokenizerClasses.put(Language.getByLangCode("nl"), DutchTokenizer.class);
        tokenizerClasses.put(Language.getByLangCode("it"), ItalianTokenizer.class);
        tokenizerClasses.put(Language.getByLangCode("pl"), PolishTokenizer.class);
        tokenizerClasses.put(Language.getByLangCode("es"), SpanishTokenizer.class);
        tokenizerClasses.put(Language.getByLangCode("ru"), RussianTokenizer.class);
        tokenizerClasses.put(Language.getByLangCode("ja"), JapaneseTokenizer.class);
        tokenizerClasses.put(Language.getByLangCode("pt"), PortugueseTokenizer.class);
        tokenizerClasses.put(Language.getByLangCode("zh"), ChineseTokenizer.class);
        tokenizerClasses.put(Language.getByLangCode("sv"), SwedishTokenizer.class);
        tokenizerClasses.put(Language.getByLangCode("uk"), UkrainianTokenizer.class);
        tokenizerClasses.put(Language.getByLangCode("ca"), CatalanTokenizer.class);
        tokenizerClasses.put(Language.getByLangCode("no"), NorwegianTokenizer.class);
        tokenizerClasses.put(Language.getByLangCode("fi"), FinnishTokenizer.class);
        tokenizerClasses.put(Language.getByLangCode("cs"), CzechTokenizer.class);
        tokenizerClasses.put(Language.getByLangCode("hu"), HungarianTokenizer.class);
        tokenizerClasses.put(Language.getByLangCode("ko"), KoreanTokenizer.class);
        tokenizerClasses.put(Language.getByLangCode("id"), IndonesianTokenizer.class);
        tokenizerClasses.put(Language.getByLangCode("tr"), TurkishTokenizer.class);
        tokenizerClasses.put(Language.getByLangCode("ro"), RomanianTokenizer.class);
        tokenizerClasses.put(Language.getByLangCode("sk"), SlovakTokenizer.class);
        tokenizerClasses.put(Language.getByLangCode("da"), DanishTokenizer.class);
        tokenizerClasses.put(Language.getByLangCode("he"), HebrewTokenizer.class);
        tokenizerClasses.put(Language.getByLangCode("lad"), LadinoTokenizer.class);
    }

    protected static CharArraySet getStopWordsForNonLuceneLangFromFile(Language language) {
        try{
            String langCode = language.getLangCode();
            String fileName = STOP_WORDS + langCode + ".txt";
            InputStream stream = FileUtils.openInputStream(new File(fileName));
            List<String> stopWords = org.apache.commons.io.IOUtils.readLines(stream);
            CharArraySet charArraySet = new CharArraySet(opts.matchVersion, 0, false);
            for (String stopWord : stopWords) {
                charArraySet.add(stopWord);
            }
            return charArraySet;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}