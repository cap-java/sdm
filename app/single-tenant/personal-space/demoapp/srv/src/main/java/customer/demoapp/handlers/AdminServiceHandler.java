package customer.demoapp.handlers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.sap.cds.ql.Insert;
import com.sap.cds.ql.cqn.CqnAnalyzer;
import com.sap.cds.ql.cqn.CqnComparisonPredicate;
import com.sap.cds.ql.cqn.CqnConnectivePredicate;
import com.sap.cds.ql.cqn.CqnElementRef;
import com.sap.cds.ql.cqn.CqnLiteral;
import com.sap.cds.ql.cqn.CqnPredicate;
import com.sap.cds.ql.cqn.CqnReference;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.ql.cqn.CqnStructuredTypeRef;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.cds.ApplicationService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;

import java.util.List;

import cds.gen.adminservice.AdminService_;
import cds.gen.adminservice.BooksAttachments_;

/**
 * Handler for AdminService operations including creating attachments in active entity state.
 */
@Component
@ServiceName(AdminService_.CDS_NAME)
public class AdminServiceHandler implements EventHandler {

    private static final Logger logger = LoggerFactory.getLogger(AdminServiceHandler.class);

    @Autowired
    @Qualifier("AdminService")
    private ApplicationService adminService;

    /**
     * Handler for createAttachmentInActive action.
     */
    @On(event = "createAttachmentInActive")
    public void createAttachmentInActive(EventContext context) {
        String targetEntity = context.getTarget().getQualifiedName();
        logger.info("=== createAttachmentInActive triggered for entity: {} ===", targetEntity);
        
        // Guard: When CAP invokes this during draft activation (edit+save),
        // the "in" parameter contains existing attachment rows.
        // A user-initiated button click sends "in" as null/empty (RequiresSelection: false).
        // Skip execution if "in" has items — that means it's a framework save, not a user click.
        Object inParam = context.get("in");
        if (inParam instanceof List && !((List<?>) inParam).isEmpty()) {
            logger.info("Skipping createAttachmentInActive — triggered by framework save (in has {} items), not user click", ((List<?>) inParam).size());
            context.setCompleted();
            return;
        }
        
        try {
            // Extract the parent entity ID from CQN
            CqnSelect cqn = (CqnSelect) context.get("cqn");
            CqnAnalyzer analyzer = CqnAnalyzer.create(context.getModel());
            Map<String, Object> rootKeys = analyzer.analyze(cqn).rootKeys();
            logger.info("Root keys: {}", rootKeys);
            
            // Extract the immediate parent ID.
            // For non-nested entities (e.g., Books.attachments), rootKeys has {up__ID: bookID}.
            // For nested entities (e.g., Chapters.attachments via composition path
            //   Books(bookID)/chapters(chapterID)/attachments), rootKeys only has {ID: bookID}
            //   and the Chapter ID is in the penultimate CQN path segment's filter.
            String parentId = extractParentId(cqn, rootKeys);
            
            if (parentId == null || parentId.isEmpty()) {
                logger.error("Could not extract parent ID from CQN. Root keys: {}", rootKeys);
                context.setCompleted();
                throw new RuntimeException("Parent entity ID is required to create attachment.");
            }
            
            logger.info("Creating attachment for parent ID: {} in facet: {}", parentId, targetEntity);
            
            // Create attachment with unique filename (timestamp prevents any duplicate issues)
            String attachmentId = createAttachmentWithContent(parentId, targetEntity);
            logger.info("Attachment created successfully with ID: {}", attachmentId);
            
            context.setCompleted();
            
        } catch (Exception e) {
            logger.error("Failed to create attachment: {}", e.getMessage(), e);
            context.setCompleted();
            throw new RuntimeException("Failed to create attachment: " + e.getMessage(), e);
        }
    }

    /**
     * Creates an attachment record with content in the active entity state.
     * Uses a timestamp-based unique filename to prevent duplicate issues.
     */
    private String createAttachmentWithContent(String parentId, String targetEntity) throws IOException {
        String attachmentId = UUID.randomUUID().toString();
        
        // Use timestamp in filename to guarantee uniqueness
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
            .withZone(ZoneId.systemDefault())
            .format(Instant.now());
        String fileName = "attachment-" + timestamp + ".txt";
        
        String sampleContent = "Sample Attachment\n" +
                               "Created: " + Instant.now() + "\n" +
                               "Parent ID: " + parentId + "\n" +
                               "Facet: " + targetEntity + "\n" +
                               "Attachment ID: " + attachmentId;
        
        InputStream contentStream = new ByteArrayInputStream(sampleContent.getBytes(StandardCharsets.UTF_8));
        
        Map<String, Object> attachmentData = new HashMap<>();
        attachmentData.put("ID", attachmentId);
        attachmentData.put("up__ID", parentId);
        attachmentData.put("fileName", fileName);
        attachmentData.put("mimeType", "text/plain");
        attachmentData.put("note", "Created programmatically in active entity");
        attachmentData.put("content", contentStream);
        
        // Determine which entity to insert into based on the target
        // The target will be like "AdminService.Books.attachments" or "AdminService.Chapters.attachments" etc.
        String insertTarget = targetEntity;
        logger.info("Inserting attachment into: {}", insertTarget);
        
        Insert insert = Insert.into(insertTarget).entry(attachmentData);
        adminService.run(insert);
        
        return attachmentId;
    }

    /**
     * Extracts the immediate parent entity's ID from the CQN.
     * 
     * For non-nested entities (e.g., Books.attachments):
     *   CQN: SELECT from AdminService.Books.attachments WHERE up__ID = 'bookID'
     *   rootKeys = {up__ID: bookID} → up__ID found directly.
     * 
     * For nested entities (e.g., Chapters.attachments via composition path):
     *   CQN: SELECT from AdminService.Books[ID='bookID'].chapters[ID='chapterID'].attachments
     *   rootKeys = {ID: bookID} → up__ID NOT found.
     *   Fix: traverse CQN path segments and extract ID from the penultimate segment
     *   (which represents the immediate parent entity, e.g., chapters[ID='chapterID']).
     */
    private String extractParentId(CqnSelect cqn, Map<String, Object> rootKeys) {
        // Case 1: Direct entity set — rootKeys has up__ID
        Object upId = rootKeys.get("up__ID");
        if (upId != null) {
            logger.info("Found up__ID in rootKeys: {}", upId);
            return upId.toString();
        }

        // Case 2: Nested composition path — traverse CQN ref segments
        try {
            if (cqn.from().isRef()) {
                CqnStructuredTypeRef ref = cqn.from().asRef();
                List<? extends CqnReference.Segment> segments = ref.segments();
                logger.info("CQN path has {} segments", segments.size());

                if (segments.size() >= 2) {
                    // Penultimate segment is the immediate parent (e.g., "chapters")
                    CqnReference.Segment parentSegment = segments.get(segments.size() - 2);
                    logger.info("Parent segment: {}, has filter: {}",
                            parentSegment.id(), parentSegment.filter().isPresent());

                    if (parentSegment.filter().isPresent()) {
                        String parentId = extractIdFromPredicate(parentSegment.filter().get());
                        if (parentId != null) {
                            logger.info("Extracted parent ID from CQN segment '{}': {}",
                                    parentSegment.id(), parentId);
                            return parentId;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Could not extract parent ID from CQN path segments: {}", e.getMessage());
        }

        // Fallback: use ID from rootKeys (correct for non-nested, wrong for nested)
        Object id = rootKeys.get("ID");
        if (id != null) {
            logger.warn("Using fallback ID from rootKeys (may be wrong for nested entities): {}", id);
            return id.toString();
        }

        return null;
    }

    /**
     * Recursively extracts the "ID" value from a CQN predicate.
     * Handles simple comparisons (ID = 'value') and conjunctions (ID = 'value' AND IsActiveEntity = true).
     */
    private String extractIdFromPredicate(CqnPredicate predicate) {
        if (predicate instanceof CqnComparisonPredicate) {
            CqnComparisonPredicate comp = (CqnComparisonPredicate) predicate;
            if (comp.left() instanceof CqnElementRef && comp.right() instanceof CqnLiteral) {
                String fieldName = ((CqnElementRef) comp.left()).lastSegment();
                if ("ID".equals(fieldName)) {
                    return ((CqnLiteral<?>) comp.right()).value().toString();
                }
            }
        } else if (predicate instanceof CqnConnectivePredicate) {
            // Conjunction (AND) or disjunction (OR) — check each child
            for (CqnPredicate child : ((CqnConnectivePredicate) predicate).predicates()) {
                String id = extractIdFromPredicate(child);
                if (id != null) {
                    return id;
                }
            }
        }
        return null;
    }
}
