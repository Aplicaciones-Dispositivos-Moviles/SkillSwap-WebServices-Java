package com.innovify.skillswap.learningpathengine.domain.model.entities;

import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** A multiple-choice question: four different answers, exactly one of them correct. Immutable. */
public final class Question {

    public static final int ANSWER_COUNT = 4;
    public static final int MAX_QUESTION_LENGTH = 1000;
    public static final int MAX_ANSWER_LENGTH = 500;

    private static final Pattern DIACRITICS = Pattern.compile("\\p{Mn}+");
    private static final Pattern NOT_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

    private final String questionString;
    private final List<String> answers;
    private final int correctAnswer;

    /**
     * @param correctAnswer index (0 to 3) of the correct answer
     * @throws DomainException when the question does not meet the rules
     */
    public Question(String questionString, List<String> answers, int correctAnswer) {
        if (questionString == null || questionString.isBlank()) {
            throw new DomainException("The question text cannot be empty.");
        }
        String text = questionString.strip();
        if (text.length() > MAX_QUESTION_LENGTH) {
            throw new DomainException(
                    "The question text cannot exceed %d characters.".formatted(MAX_QUESTION_LENGTH));
        }

        if (answers == null || answers.size() != ANSWER_COUNT) {
            throw new DomainException("A question needs exactly %d answers.".formatted(ANSWER_COUNT));
        }
        List<String> cleaned = answers.stream().map(answer -> answer == null ? "" : answer.strip()).toList();
        if (cleaned.stream().anyMatch(String::isEmpty)) {
            throw new DomainException("An answer cannot be empty.");
        }
        if (cleaned.stream().anyMatch(answer -> answer.length() > MAX_ANSWER_LENGTH)) {
            throw new DomainException("An answer cannot exceed %d characters.".formatted(MAX_ANSWER_LENGTH));
        }
        Set<String> distinct = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        distinct.addAll(cleaned);
        if (distinct.size() != ANSWER_COUNT) {
            throw new DomainException("The answers of a question must be different from each other.");
        }

        if (correctAnswer < 0 || correctAnswer >= ANSWER_COUNT) {
            throw new DomainException(
                    "The correct answer must be an index between 0 and %d.".formatted(ANSWER_COUNT - 1));
        }

        this.questionString = text;
        this.answers = cleaned;
        this.correctAnswer = correctAnswer;
    }

    /**
     * The form used to tell whether two questions are the same one: lowercase, without accents, punctuation or
     * extra spaces, so "What is HTTP?" and "what is http" are considered repeated.
     */
    public static String comparableText(String text) {
        if (text == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        String withoutAccents = DIACRITICS.matcher(decomposed).replaceAll("");
        return NOT_ALPHANUMERIC.matcher(withoutAccents).replaceAll(" ").strip();
    }

    /** {@link #comparableText(String)} of this question. */
    public String comparableText() {
        return comparableText(questionString);
    }

    public String getQuestionString() {
        return questionString;
    }

    public List<String> getAnswers() {
        return answers;
    }

    public int getCorrectAnswer() {
        return correctAnswer;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Question question
                && correctAnswer == question.correctAnswer
                && questionString.equals(question.questionString)
                && answers.equals(question.answers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(questionString, answers, correctAnswer);
    }
}
