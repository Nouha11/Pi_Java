package services.studysession;

import models.studysession.PdfResource;
import utils.MyConnection;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for managing PDF resources attached to courses.
 *
 * Files are stored under: {user.home}/nova_resources/pdfs/{courseId}/
 * Filename deduplication appends _1, _2, etc. before the extension when a
 * file with the same name already exists in the target directory.
 */
public class PdfResourceService {

    private final Connection cnx = MyConnection.getInstance().getCnx();

    // ─────────────────────────────────────────────
    //  6.1 — addResource
    // ─────────────────────────────────────────────

    /**
     * Copies the source file to the course PDF storage directory, deduplicates
     * the filename if necessary, persists a {@code course_pdf_resource} record,
     * and returns the created {@link PdfResource}.
     *
     * @param courseId      the course this resource belongs to
     * @param title         display title for the resource
     * @param topic         optional topic tag (may be null)
     * @param filePath      absolute path of the source file to copy
     * @param uploadedById  userId of the uploader
     * @return the persisted PdfResource with its generated id
     * @throws SQLException if a database error occurs
     * @throws IOException  if the file cannot be copied
     */
    public PdfResource addResource(int courseId, String title, String topic,
                                   String filePath, int uploadedById)
            throws SQLException, IOException {

        // 1. Resolve target directory and ensure it exists
        Path targetDir = resolveTargetDir(courseId);
        Files.createDirectories(targetDir);

        // 2. Determine the destination filename (deduplicate if needed)
        Path sourcePath = Paths.get(filePath);
        String originalFilename = sourcePath.getFileName().toString();
        String destFilename = deduplicateFilename(targetDir, originalFilename);
        Path destPath = targetDir.resolve(destFilename);

        // 3. Copy the file
        Files.copy(sourcePath, destPath, StandardCopyOption.REPLACE_EXISTING);

        // 4. Persist the DB record
        String sql = "INSERT INTO course_pdf_resource " +
                "(course_id, title, topic, file_path, uploaded_at, uploaded_by_id) " +
                "VALUES (?, ?, ?, ?, NOW(), ?)";
        PreparedStatement ps = cnx.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        ps.setInt(1, courseId);
        ps.setString(2, title);
        if (topic != null && !topic.isBlank()) {
            ps.setString(3, topic);
        } else {
            ps.setNull(3, Types.VARCHAR);
        }
        ps.setString(4, destPath.toAbsolutePath().toString());
        ps.setInt(5, uploadedById);
        ps.executeUpdate();

        // 5. Retrieve the generated id and return the full record
        ResultSet keys = ps.getGeneratedKeys();
        if (keys.next()) {
            int generatedId = keys.getInt(1);
            return findById(generatedId);
        }
        throw new SQLException("Failed to retrieve generated key after inserting PDF resource.");
    }

    // ─────────────────────────────────────────────
    //  6.2 — removeResource
    // ─────────────────────────────────────────────

    /**
     * Deletes the {@code course_pdf_resource} record by id and removes the
     * associated file from disk (silently ignores a missing file).
     *
     * @param resourceId the id of the resource to remove
     * @throws SQLException if a database error occurs
     */
    public void removeResource(int resourceId) throws SQLException {
        // Fetch the file path before deleting the record
        String filePath = null;
        String selectSql = "SELECT file_path FROM course_pdf_resource WHERE id = ?";
        PreparedStatement selectPs = cnx.prepareStatement(selectSql);
        selectPs.setInt(1, resourceId);
        ResultSet rs = selectPs.executeQuery();
        if (rs.next()) {
            filePath = rs.getString("file_path");
        }

        // Delete the DB record
        String deleteSql = "DELETE FROM course_pdf_resource WHERE id = ?";
        PreparedStatement deletePs = cnx.prepareStatement(deleteSql);
        deletePs.setInt(1, resourceId);
        deletePs.executeUpdate();

        // Delete the file from disk (no error if missing)
        if (filePath != null) {
            try {
                Files.deleteIfExists(Paths.get(filePath));
            } catch (IOException e) {
                // Silently ignore — file may already be gone
                System.err.println("[PdfResourceService] Could not delete file: " + filePath + " — " + e.getMessage());
            }
        }
    }

    // ─────────────────────────────────────────────
    //  6.3 — findByCourse
    // ─────────────────────────────────────────────

    /**
     * Returns all PDF resources for the given course, ordered by
     * {@code uploaded_at DESC}.
     *
     * @param courseId the course id
     * @return list of PdfResource records (may be empty)
     * @throws SQLException if a database error occurs
     */
    public List<PdfResource> findByCourse(int courseId) throws SQLException {
        String sql = "SELECT * FROM course_pdf_resource " +
                "WHERE course_id = ? " +
                "ORDER BY uploaded_at DESC";
        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, courseId);
        ResultSet rs = ps.executeQuery();
        List<PdfResource> list = new ArrayList<>();
        while (rs.next()) {
            list.add(mapRow(rs));
        }
        return list;
    }

    // ─────────────────────────────────────────────
    //  6.4 — findByTitle
    // ─────────────────────────────────────────────

    /**
     * Returns PDF resources for the given course where {@code title} or
     * {@code topic} contains {@code searchTerm} (case-insensitive, SQL LIKE).
     *
     * @param courseId   the course id
     * @param searchTerm the substring to search for
     * @return matching PdfResource records ordered by uploaded_at DESC
     * @throws SQLException if a database error occurs
     */
    public List<PdfResource> findByTitle(int courseId, String searchTerm) throws SQLException {
        String sql = "SELECT * FROM course_pdf_resource " +
                "WHERE course_id = ? " +
                "AND (title LIKE ? OR topic LIKE ?) " +
                "ORDER BY uploaded_at DESC";
        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, courseId);
        String pattern = "%" + searchTerm + "%";
        ps.setString(2, pattern);
        ps.setString(3, pattern);
        ResultSet rs = ps.executeQuery();
        List<PdfResource> list = new ArrayList<>();
        while (rs.next()) {
            list.add(mapRow(rs));
        }
        return list;
    }

    // ─────────────────────────────────────────────
    //  PRIVATE HELPERS
    // ─────────────────────────────────────────────

    /**
     * Returns the target storage directory for a course's PDF files:
     * {@code {user.home}/nova_resources/pdfs/{courseId}/}
     */
    private Path resolveTargetDir(int courseId) {
        return Paths.get(System.getProperty("user.home"), "nova_resources", "pdfs",
                String.valueOf(courseId));
    }

    /**
     * Returns a filename that does not already exist in {@code targetDir}.
     * If {@code filename} is free, it is returned unchanged.
     * Otherwise, a numeric suffix is appended before the extension:
     * {@code report.pdf} → {@code report_1.pdf} → {@code report_2.pdf} → …
     *
     * @param targetDir the directory to check against
     * @param filename  the original filename
     * @return a filename that is safe to use in {@code targetDir}
     */
    String deduplicateFilename(Path targetDir, String filename) {
        if (!Files.exists(targetDir.resolve(filename))) {
            return filename;
        }

        // Split into base name and extension
        int dotIndex = filename.lastIndexOf('.');
        String base;
        String ext;
        if (dotIndex > 0) {
            base = filename.substring(0, dotIndex);
            ext = filename.substring(dotIndex); // includes the dot, e.g. ".pdf"
        } else {
            base = filename;
            ext = "";
        }

        int counter = 1;
        String candidate;
        do {
            candidate = base + "_" + counter + ext;
            counter++;
        } while (Files.exists(targetDir.resolve(candidate)));

        return candidate;
    }

    /**
     * Fetches a single PdfResource by its primary key.
     */
    private PdfResource findById(int id) throws SQLException {
        String sql = "SELECT * FROM course_pdf_resource WHERE id = ?";
        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, id);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            return mapRow(rs);
        }
        return null;
    }

    /**
     * Maps the current row of a ResultSet to a {@link PdfResource}.
     */
    private PdfResource mapRow(ResultSet rs) throws SQLException {
        PdfResource resource = new PdfResource();
        resource.setId(rs.getInt("id"));
        resource.setCourseId(rs.getInt("course_id"));
        resource.setTitle(rs.getString("title"));
        resource.setTopic(rs.getString("topic"));
        resource.setFilePath(rs.getString("file_path"));

        Timestamp uploadedAt = rs.getTimestamp("uploaded_at");
        resource.setUploadedAt(uploadedAt != null ? uploadedAt.toLocalDateTime() : null);

        int uploadedById = rs.getInt("uploaded_by_id");
        resource.setUploadedById(rs.wasNull() ? 0 : uploadedById);

        return resource;
    }
}
