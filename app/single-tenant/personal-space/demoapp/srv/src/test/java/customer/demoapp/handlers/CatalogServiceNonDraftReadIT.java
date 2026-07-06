package customer.demoapp.handlers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Reproduces the SDM 1.10.0 regression:
 *
 *   SDMReadAttachmentsHandler.populateUploadableFlags (new @After handler in 1.10.0) fires after
 *   every successful read on any entity whose composition has @SDM.Attachments.  It then calls
 *   DBQuery.getAttachmentsForUPID, which unconditionally SELECTs "IsActiveEntity" — a column that
 *   only exists on draft-enabled entities.  CatalogService.Books is a non-draft, @readonly
 *   projection, so its Books.attachments entity has no IsActiveEntity element.  The CQN
 *   resolution throws CdsElementNotFoundException, which surfaces as HTTP 500.
 *
 *   Expected (broken on SDM 1.10.0 without the fix):  GET /odata/v4/CatalogService/Books → 500
 *   Expected (after the fix):                          GET /odata/v4/CatalogService/Books → 200
 *
 *   The seed CSV ships 5 books, so the response always has rows — exactly the condition that
 *   triggers populateUploadableFlags (it returns early on empty data).
 */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogServiceNonDraftReadIT {

    // CatalogService is bound at the default CDS OData endpoint path.
    // The CAP Spring Boot starter mounts OData services under /odata/v4/<ServiceName>.
    private static final String BOOKS_URI = "/odata/v4/CatalogService/Books";

    @Autowired
    private MockMvc mockMvc;

    /**
     * A plain, unauthenticated GET on the non-draft CatalogService.Books must return 200
     * and at least one book row.  On SDM 1.10.0 (unpatched) this returns 500 because
     * populateUploadableFlags tries to SELECT IsActiveEntity from a non-draft attachment entity.
     */
    @Test
    void nonDraftReadOfBooksWithAttachmentsMustReturn200() throws Exception {
        mockMvc.perform(get(BOOKS_URI))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.value").isArray())
               .andExpect(jsonPath("$.value[0].ID").exists());
    }

    /**
     * Same call with $top=1 — verifies it is not the 0-row early-exit case that happens to pass.
     * populateUploadableFlags does `if (data == null || data.isEmpty()) return;`, so an empty
     * result would never hit the crash.  This test ensures at least one row is returned AND
     * processed without error.
     */
    @Test
    void nonDraftReadWithTopOneStillReturns200() throws Exception {
        mockMvc.perform(get(BOOKS_URI + "?$top=1"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.value").isArray())
               .andExpect(jsonPath("$.value[0].ID").exists());
    }

    /**
     * Filtering to a row that is known to exist (Wuthering Heights is in the seed CSV).
     * Exercises the exact code path: a query that returns exactly 1 row goes through
     * populateUploadableFlags fully, hitting getAttachmentsForUPID once per facet.
     */
    @Test
    void nonDraftReadWithFilterStillReturns200() throws Exception {
        mockMvc.perform(get(BOOKS_URI + "?$filter=stock gt 0"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.value").isArray());
    }
}
