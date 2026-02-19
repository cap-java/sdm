const { GoogleGenerativeAI } = require("@google/generative-ai");

// --- MOCK DATA ---
const MOCK_FAILURE_LOG = `
[INFO] Scanning for projects...
[INFO] 
[INFO] ----------------------< com.sap.cds:sdm-root >----------------------
[INFO] Building CDS Feature for SAP Document Management Service - Root 1.7.1-SNAPSHOT [1/2]
[INFO] --------------------------------[ pom ]---------------------------------
[INFO] 
[INFO] --- maven-enforcer-plugin:3.4.1:enforce (no-duplicate-declared-dependencies) @ sdm-root ---
[INFO] 
[INFO] -------------------------< com.sap.cds:sdm >--------------------------
[INFO] Building CDS Feature for SAP Document Management Service 1.7.1-SNAPSHOT [2/2]
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- maven-resources-plugin:3.3.1:resources (default-resources) @ sdm ---
[INFO] Copying 2 resources from src/main/resources to target/classes
[INFO] 
[INFO] --- maven-compiler-plugin:3.11.0:compile (default-compile) @ sdm ---
[INFO] Changes detected - recompiling the module! :dependency
[INFO] Compiling 45 source files to /home/runner/work/sdm/sdm/sdm/target/classes
[INFO] -------------------------------------------------------------
[ERROR] COMPILATION ERROR : 
[INFO] -------------------------------------------------------------
[ERROR] /home/runner/work/sdm/sdm/sdm/src/main/java/com/sap/cds/sdm/service/DocumentService.java:[24,35] cannot find symbol
  symbol:   method isBlank()
  location: variable name of type java.lang.String
[ERROR] /home/runner/work/sdm/sdm/sdm/src/main/java/com/sap/cds/sdm/service/DocumentService.java:[45,12] ';' expected
[INFO] 2 errors 
[INFO] -------------------------------------------------------------
[INFO] ------------------------------------------------------------------------
[INFO] Reactor Summary:
[INFO] 
[INFO] CDS Feature for SAP Document Management Service - Root 1.7.1-SNAPSHOT SUCCESS [  0.452 s]
[INFO] CDS Feature for SAP Document Management Service 1.7.1-SNAPSHOT ... FAILURE [  2.103 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD FAILURE
[INFO] ------------------------------------------------------------------------
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:3.11.0:compile (default-compile) on project sdm: Compilation failure: Compilation failure: 
[ERROR] /home/runner/work/sdm/sdm/sdm/src/main/java/com/sap/cds/sdm/service/DocumentService.java:[24,35] cannot find symbol
[ERROR]   symbol:   method isBlank()
[ERROR]   location: variable name of type java.lang.String
[ERROR] /home/runner/work/sdm/sdm/sdm/src/main/java/com/sap/cds/sdm/service/DocumentService.java:[45,12] ';' expected
[ERROR] -> [Help 1]
[ERROR] 
[ERROR] To see the full stack trace of the errors, re-run Maven with the -e switch.
[ERROR] Re-run Maven using the -X switch to enable full debug logging.
`;

const LOG_SNIPPET = MOCK_FAILURE_LOG; // Simulating tail extract

async function runLocalTest() {
    console.log("🩺 Starting Local Verification of Build Doctor...");

    // Check API Key
    const apiKey = process.env.GEMINI_API_KEY;
    if (!apiKey) {
        console.error("❌ Error: GEMINI_API_KEY environment variable is NOT set.");
        console.error("Please export it in your terminal: export GEMINI_API_KEY='your_key'");
        return;
    }

    console.log("✅ API Key found.");
    console.log("📄 Using Mock Failure Log (Maven Compilation Error)...");

    try {
        const genAI = new GoogleGenerativeAI(apiKey);
        const model = genAI.getGenerativeModel({ model: "gemini-2.5-flash" });

        const prompt = `
        You are a DevOps Expert and "Build Doctor".
        A GitHub Actions workflow 'Main Build' failed.
        
        Analyze the following log snippet to identify the root cause.
        
        Log Snippet:
        \`\`\`
        ${LOG_SNIPPET}
        \`\`\`
        
        Your response must be a concise Markdown comment suitable for a developer.
        Structure:
        
        ## 🩺 Build Doctor Diagnosis
        
        **1. Root Cause:** 
        (Explain what went wrong in 1-2 senteces. Be specific.)
        
        **2. Relevant Log Lines:**
        (Quote the specific error message from the logs)
        
        **3. Suggested Fix:**
        (Actionable advice. If it's a code fix, show the snippet.)
        
        **Confidence:** (High/Medium/Low)
        `;

        console.log("🤖 Sending Prompt to Gemini...");
        const result = await model.generateContent(prompt);
        const analysis = result.response.text();

        console.log("\n--- 📝 Generated Analysis Start ---\n");
        console.log(analysis);
        console.log("\n--- 📝 Generated Analysis End ---\n");
        console.log("✅ Local Verification Passed (if analysis looks correct).");

    } catch (error) {
        console.error("❌ Verification Failed:", error);
    }
}

runLocalTest();
