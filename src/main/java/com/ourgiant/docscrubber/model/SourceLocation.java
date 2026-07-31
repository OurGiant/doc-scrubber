package com.ourgiant.docscrubber.model;

/**
 * Where a fragment came from within its document, in terms generic enough to cover PDF pages,
 * docx paragraphs/runs, plain-text line numbers, and named metadata fields. Fields that don't
 * apply to a given format are left null.
 */
public final class SourceLocation {

    private final String kind;
    private final Integer index;
    private final Integer runIndex;
    private final String fieldName;

    private SourceLocation(String kind, Integer index, Integer runIndex, String fieldName) {
        this.kind = kind;
        this.index = index;
        this.runIndex = runIndex;
        this.fieldName = fieldName;
    }

    public static SourceLocation page(int pageIndex) {
        return new SourceLocation("Page", pageIndex, null, null);
    }

    public static SourceLocation paragraphRun(int paragraphIndex, int runIndex) {
        return new SourceLocation("Paragraph", paragraphIndex, runIndex, null);
    }

    public static SourceLocation line(int lineNumber) {
        return new SourceLocation("Line", lineNumber, null, null);
    }

    public static SourceLocation field(String fieldName) {
        return new SourceLocation(null, null, null, fieldName);
    }

    public Integer getPageOrParagraph() {
        return index;
    }

    public Integer getRunIndex() {
        return runIndex;
    }

    public String getFieldName() {
        return fieldName;
    }

    /** Human-readable description for the findings table / report, e.g. "Page 3" or "Paragraph 12, run 2". */
    public String describe() {
        if (fieldName != null) {
            return fieldName;
        }
        if (index == null) {
            return "Unknown location";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(kind).append(' ').append(index + 1);
        if (runIndex != null) {
            sb.append(", run ").append(runIndex + 1);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return describe();
    }
}
