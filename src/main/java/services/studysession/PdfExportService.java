package services.studysession;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.itextpdf.text.pdf.draw.LineSeparator;
import javafx.scene.Node;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for generating PDF exports of the Admin Analytics Dashboard.
 *
 * <p>Captures each visible chart node as a {@code WritableImage} via
 * {@code node.snapshot(null, null)}, converts the image to an iText
 * {@code Image}, and assembles a structured PDF containing:
 * <ul>
 *   <li>Platform branding header</li>
 *   <li>Generation timestamp</li>
 *   <li>Summary stat card values</li>
 *   <li>Chart snapshots with titles</li>
 *   <li>Tutor Performance table</li>
 * </ul>
 *
 * <p><strong>Threading note:</strong> {@code node.snapshot(null, null)} must be
 * called on the JavaFX Application Thread. The caller is responsible for
 * ensuring this before invoking {@link #exportReport}.
 *
 * Requirements: 7.1, 7.2, 7.3, 7.4
 */
public class PdfExportService {

    // ── Fonts ────────────────────────────────────────────────────────────────

    private static final Font FONT_TITLE = new Font(Font.FontFamily.HELVETICA, 20, Font.BOLD,
            new BaseColor(30, 41, 59));          // slate-800
    private static final Font FONT_SUBTITLE = new Font(Font.FontFamily.HELVETICA, 11, Font.NORMAL,
            new BaseColor(100, 116, 139));        // slate-500
    private static final Font FONT_SECTION = new Font(Font.FontFamily.HELVETICA, 13, Font.BOLD,
            new BaseColor(30, 41, 59));
    private static final Font FONT_CARD_LABEL = new Font(Font.FontFamily.HELVETICA, 9, Font.NORMAL,
            new BaseColor(100, 116, 139));
    private static final Font FONT_CARD_VALUE = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD,
            new BaseColor(30, 41, 59));
    private static final Font FONT_CHART_TITLE = new Font(Font.FontFamily.HELVETICA, 11, Font.BOLD,
            new BaseColor(51, 65, 85));           // slate-700
    private static final Font FONT_TABLE_HEADER = new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD,
            BaseColor.WHITE);
    private static final Font FONT_TABLE_CELL = new Font(Font.FontFamily.HELVETICA, 9, Font.NORMAL,
            new BaseColor(30, 41, 59));

    // ── Colors ───────────────────────────────────────────────────────────────

    private static final BaseColor COLOR_HEADER_BG = new BaseColor(30, 41, 59);   // slate-800
    private static final BaseColor COLOR_TABLE_HEADER_BG = new BaseColor(59, 130, 246); // blue-500
    private static final BaseColor COLOR_ROW_ALT = new BaseColor(248, 250, 252);  // slate-50
    private static final BaseColor COLOR_DIVIDER = new BaseColor(226, 232, 240);  // slate-200

    // ── Date formatter ───────────────────────────────────────────────────────

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy  HH:mm:ss");

    // ─────────────────────────────────────────────────────────────────────────
    //  Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates a PDF analytics report and writes it to {@code outputFile}.
     *
     * <p>The method must be called after all chart nodes have been rendered on
     * the JavaFX Application Thread (snapshots are taken inside this method).
     *
     * @param chartNodes  list of JavaFX chart {@link Node}s to snapshot; each
     *                    node's {@code getId()} is used as the chart title
     * @param statCards   ordered map of stat card title → display value
     *                    (e.g. "Total Study Sessions" → "1,234")
     * @param tutorRows   list of {@link TutorPerformanceRow} for the tutor table
     * @param outputFile  destination file to write the PDF to
     * @throws IOException if PDF creation or file I/O fails
     *
     * Requirements: 7.1, 7.2, 7.3, 7.4
     */
    public void exportReport(List<Node> chartNodes,
                             Map<String, String> statCards,
                             List<TutorPerformanceRow> tutorRows,
                             File outputFile) throws IOException {

        Document document = new Document(PageSize.A4, 36, 36, 36, 36);

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            PdfWriter.getInstance(document, fos);
            document.open();

            // 1. Branding header
            addBrandingHeader(document);

            // 2. Generation timestamp
            addTimestamp(document);

            // 3. Stat cards section
            addStatCards(document, statCards);

            // 4. Chart snapshots
            addChartSnapshots(document, chartNodes);

            // 5. Tutor performance table
            addTutorTable(document, tutorRows);

            document.close();

        } catch (DocumentException e) {
            throw new IOException("Failed to create PDF document: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Private helpers — document sections
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Adds the platform branding header with a dark background banner.
     * Requirements: 7.3
     */
    private void addBrandingHeader(Document document) throws DocumentException {
        // Dark banner cell
        PdfPTable banner = new PdfPTable(1);
        banner.setWidthPercentage(100);

        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(COLOR_HEADER_BG);
        cell.setPadding(18);
        cell.setBorder(Rectangle.NO_BORDER);

        Paragraph title = new Paragraph("NOVA Learning Platform — Analytics Report", FONT_TITLE);
        title.setAlignment(Element.ALIGN_CENTER);
        Font titleFont = new Font(Font.FontFamily.HELVETICA, 20, Font.BOLD, BaseColor.WHITE);
        Paragraph titleWhite = new Paragraph("NOVA Learning Platform — Analytics Report", titleFont);
        titleWhite.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(titleWhite);

        banner.addCell(cell);
        document.add(banner);
        document.add(Chunk.NEWLINE);
    }

    /**
     * Adds the generation date/time line.
     * Requirements: 7.3
     */
    private void addTimestamp(Document document) throws DocumentException {
        String ts = "Generated: " + LocalDateTime.now().format(TIMESTAMP_FMT);
        Paragraph p = new Paragraph(ts, FONT_SUBTITLE);
        p.setAlignment(Element.ALIGN_CENTER);
        document.add(p);
        document.add(new Paragraph(" "));
        addDivider(document);
    }

    /**
     * Adds the four summary stat cards as a 2×N grid table.
     * Requirements: 7.3
     */
    private void addStatCards(Document document, Map<String, String> statCards) throws DocumentException {
        if (statCards == null || statCards.isEmpty()) {
            return;
        }

        Paragraph sectionTitle = new Paragraph("Summary Statistics", FONT_SECTION);
        sectionTitle.setSpacingBefore(10);
        sectionTitle.setSpacingAfter(8);
        document.add(sectionTitle);

        // Lay out cards in a 2-column table
        int cols = Math.min(2, statCards.size());
        PdfPTable table = new PdfPTable(cols);
        table.setWidthPercentage(100);
        table.setSpacingAfter(10);

        for (Map.Entry<String, String> entry : statCards.entrySet()) {
            PdfPCell card = buildStatCard(entry.getKey(), entry.getValue());
            table.addCell(card);
        }

        // Pad to complete the last row if needed
        int remainder = statCards.size() % cols;
        if (remainder != 0) {
            for (int i = 0; i < cols - remainder; i++) {
                PdfPCell empty = new PdfPCell();
                empty.setBorder(Rectangle.NO_BORDER);
                table.addCell(empty);
            }
        }

        document.add(table);
        addDivider(document);
    }

    /**
     * Builds a single stat card cell.
     */
    private PdfPCell buildStatCard(String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setBorderColor(COLOR_DIVIDER);
        cell.setBorderWidth(1f);
        cell.setPadding(12);
        cell.setBackgroundColor(BaseColor.WHITE);

        Paragraph labelPara = new Paragraph(label, FONT_CARD_LABEL);
        labelPara.setSpacingAfter(4);
        cell.addElement(labelPara);

        Paragraph valuePara = new Paragraph(value != null ? value : "—", FONT_CARD_VALUE);
        cell.addElement(valuePara);

        return cell;
    }

    /**
     * Captures each chart node as a {@code WritableImage}, converts it to an
     * iText {@code Image}, and adds it to the document with the node's id as title.
     *
     * <p>Must be called on the JavaFX Application Thread (caller's responsibility).
     * Requirements: 7.2, 7.3
     */
    private void addChartSnapshots(Document document, List<Node> chartNodes) throws DocumentException, IOException {
        if (chartNodes == null || chartNodes.isEmpty()) {
            return;
        }

        Paragraph sectionTitle = new Paragraph("Analytics Charts", FONT_SECTION);
        sectionTitle.setSpacingBefore(10);
        sectionTitle.setSpacingAfter(8);
        document.add(sectionTitle);

        for (Node node : chartNodes) {
            // Derive a human-readable title from the node id
            String chartTitle = buildChartTitle(node);

            // Snapshot the node
            javafx.scene.image.WritableImage writableImage = node.snapshot(null, null);

            // Convert WritableImage → byte[] via SwingFXUtils + ImageIO
            byte[] imageBytes = writableImageToBytes(writableImage);
            if (imageBytes == null || imageBytes.length == 0) {
                continue;
            }

            // Add chart title
            Paragraph titlePara = new Paragraph(chartTitle, FONT_CHART_TITLE);
            titlePara.setSpacingBefore(8);
            titlePara.setSpacingAfter(4);
            document.add(titlePara);

            // Add chart image, scaled to page width
            try {
                Image pdfImage = Image.getInstance(imageBytes);
                pdfImage.scaleToFit(
                        document.getPageSize().getWidth() - document.leftMargin() - document.rightMargin(),
                        300f);
                pdfImage.setAlignment(Image.ALIGN_CENTER);
                document.add(pdfImage);
            } catch (BadElementException e) {
                throw new IOException("Failed to embed chart image for '" + chartTitle + "': " + e.getMessage(), e);
            }

            document.add(new Paragraph(" "));
        }

        addDivider(document);
    }

    /**
     * Adds the Tutor Performance table as a formatted iText table.
     * Requirements: 7.3
     */
    private void addTutorTable(Document document, List<TutorPerformanceRow> tutorRows) throws DocumentException {
        Paragraph sectionTitle = new Paragraph("Tutor Performance", FONT_SECTION);
        sectionTitle.setSpacingBefore(10);
        sectionTitle.setSpacingAfter(8);
        document.add(sectionTitle);

        if (tutorRows == null || tutorRows.isEmpty()) {
            document.add(new Paragraph("No tutor data available.", FONT_SUBTITLE));
            return;
        }

        // 5 columns: Tutor Name | Enrolled Students | Avg. Completion Rate (%) | Active Courses | Avg. Session Duration (min)
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setSpacingAfter(10);

        try {
            table.setWidths(new float[]{3f, 2f, 2.5f, 2f, 2.5f});
        } catch (DocumentException ignored) {
            // fallback to equal widths
        }

        // Header row
        String[] headers = {
            "Tutor Name",
            "Enrolled Students",
            "Avg. Completion Rate (%)",
            "Active Courses",
            "Avg. Session Duration (min)"
        };
        for (String header : headers) {
            PdfPCell hCell = new PdfPCell(new Phrase(header, FONT_TABLE_HEADER));
            hCell.setBackgroundColor(COLOR_TABLE_HEADER_BG);
            hCell.setPadding(7);
            hCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            hCell.setBorderColor(BaseColor.WHITE);
            table.addCell(hCell);
        }

        // Data rows
        boolean alternate = false;
        for (TutorPerformanceRow row : tutorRows) {
            BaseColor rowBg = alternate ? COLOR_ROW_ALT : BaseColor.WHITE;
            alternate = !alternate;

            table.addCell(buildDataCell(row.getTutorName(), rowBg, Element.ALIGN_LEFT));
            table.addCell(buildDataCell(String.valueOf(row.getEnrolledStudents()), rowBg, Element.ALIGN_CENTER));
            table.addCell(buildDataCell(String.format("%.1f", row.getAverageCompletionRate()), rowBg, Element.ALIGN_CENTER));
            table.addCell(buildDataCell(String.valueOf(row.getActiveCourseCount()), rowBg, Element.ALIGN_CENTER));
            table.addCell(buildDataCell(String.format("%.1f", row.getAverageSessionDuration()), rowBg, Element.ALIGN_CENTER));
        }

        document.add(table);
    }

    /**
     * Builds a data cell for the tutor table.
     */
    private PdfPCell buildDataCell(String text, BaseColor background, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "—", FONT_TABLE_CELL));
        cell.setBackgroundColor(background);
        cell.setPadding(6);
        cell.setHorizontalAlignment(alignment);
        cell.setBorderColor(COLOR_DIVIDER);
        cell.setBorderWidth(0.5f);
        return cell;
    }

    /**
     * Adds a thin horizontal divider line.
     */
    private void addDivider(Document document) throws DocumentException {
        LineSeparator line = new LineSeparator(0.5f, 100f, COLOR_DIVIDER, Element.ALIGN_CENTER, -2f);
        document.add(new Chunk(line));
        document.add(new Paragraph(" "));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Private helpers — image conversion
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Converts a JavaFX {@code WritableImage} to a PNG byte array using
     * {@code PixelReader} and {@code ImageIO} — no Swing dependency required.
     *
     * @param writableImage the source image
     * @return PNG bytes, or {@code null} if conversion fails
     * @throws IOException if ImageIO write fails
     */
    private byte[] writableImageToBytes(javafx.scene.image.WritableImage writableImage) throws IOException {
        if (writableImage == null) {
            return null;
        }
        int width  = (int) writableImage.getWidth();
        int height = (int) writableImage.getHeight();
        if (width <= 0 || height <= 0) {
            return null;
        }

        // Read pixels via JavaFX PixelReader → BufferedImage (TYPE_INT_ARGB)
        PixelReader pixelReader = writableImage.getPixelReader();
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                javafx.scene.paint.Color fxColor = pixelReader.getColor(x, y);
                int a = (int) Math.round(fxColor.getOpacity() * 255);
                int r = (int) Math.round(fxColor.getRed()     * 255);
                int g = (int) Math.round(fxColor.getGreen()   * 255);
                int b = (int) Math.round(fxColor.getBlue()    * 255);
                bufferedImage.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(bufferedImage, "PNG", baos);
        return baos.toByteArray();
    }

    /**
     * Derives a human-readable chart title from the node's {@code id}.
     * Converts kebab-case / camelCase ids to title-cased words.
     * Falls back to "Chart" if the id is null or blank.
     */
    private String buildChartTitle(Node node) {
        String id = node.getId();
        if (id == null || id.isBlank()) {
            return "Chart";
        }
        // Replace hyphens/underscores with spaces, then title-case each word
        String spaced = id.replaceAll("[-_]", " ")
                          .replaceAll("([A-Z])", " $1")
                          .trim();
        String[] words = spaced.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    sb.append(word.substring(1));
                }
                sb.append(' ');
            }
        }
        return sb.toString().trim();
    }
}
