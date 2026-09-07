package com.example.ai;

import android.content.Context;
import android.net.Uri;

import com.example.ai.agents.DirectorAgent;
import com.example.ai.protocol.AIDirectorSpec;
import com.example.ai.protocol.AIProductionRequest;
import com.example.character.CharacterManager;
import com.example.cloud.CloudProvider;
import com.example.engine.ThreeDEngine;
import com.example.knowledge.KnowledgeManager;
import com.example.runtime.ProjectRuntime;
import com.example.tasks.ExecutionEngine;
import com.example.tasks.ProductionPlan;
import com.example.tools.ToolExecutor;
import com.example.tools.ToolRegistry;
import com.example.utils.VynaraLogger;
import com.example.validation.ValidationManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class AIProductionController {
    private final Context context;
    private final ProjectRuntime runtime;
    private final ApiKeyManager apiKeyManager;
    private final GeminiApiClient apiClient;
    private final GeminiProvider geminiProvider;
    private final KnowledgeManager knowledgeManager;
    private final ToolRegistry toolRegistry;
    private final ThreeDEngine threeDEngine;
    private final CharacterManager characterManager;
    private final ValidationManager validationManager;
    private final ToolExecutor toolExecutor;
    private final ExecutionEngine executionEngine;
    private final AIOrchestrator orchestrator;

    // Director Agent & Self-Correction Subsystems
    private final DirectorAgent directorAgent;
    private final AICorrector aiCorrector;

    private static final int MAX_REPAIR_ATTEMPTS = 2;
    private int currentCorrectionAttempt = 1;

    private ProductionPlan currentPlan;

    public AIProductionController(Context context) {
        this.context = context.getApplicationContext();
        // Connect to the unified ProjectRuntime instance to eliminate split engine instances
        this.runtime = ProjectRuntime.getInstance(this.context);
        this.apiKeyManager = new ApiKeyManager(this.context);
        this.apiClient = new GeminiApiClient();
        this.geminiProvider = new GeminiProvider(apiClient);
        
        // Bind subsystems directly from the shared ProjectRuntime
        this.knowledgeManager = runtime.getKnowledgeManager();
        this.toolRegistry = runtime.getToolRegistry();
        this.threeDEngine = runtime.getEngine();
        this.characterManager = runtime.getCharacterManager();
        this.validationManager = runtime.getValidationManager();
        this.toolExecutor = runtime.getToolExecutor();
        this.executionEngine = runtime.getExecutionEngine();
        this.orchestrator = new AIOrchestrator(apiClient, apiKeyManager, knowledgeManager);

        // Director Agent & AICorrector
        this.directorAgent = new DirectorAgent(this.apiClient, this.apiKeyManager);
        this.aiCorrector = new AICorrector(this.toolExecutor, this.orchestrator, null);
    }

    public ProductionPlan generatePlan(String userPrompt, String style, String engine) {
        return generatePlan(userPrompt, style, engine, new ArrayList<>());
    }

    public ProductionPlan generatePlan(String userPrompt, String style, String engine, List<String> referenceImageUris) {
        if (engine != null && (engine.toLowerCase().contains("blender") || engine.toLowerCase().contains("cloud"))) {
            apiKeyManager.saveComputeProvider(CloudProvider.GITHUB_ACTIONS);
        }

        List<String> resolvedUris = resolveReferenceUris(referenceImageUris);
        currentPlan = orchestrator.planProduction(userPrompt, style, engine, resolvedUris);
        return currentPlan;
    }

    /**
     * CORE PIPELINE: Asynchronously requests an intelligent, structured 3D production plan
     * directly from the selected Gemini model, utilizing active knowledge bases and reference images.
     */
    public void generatePlanWithGemini(String userPrompt, String style, String engine, List<String> referenceImageUris, final GeminiApiClient.ApiCallback<ProductionPlan> callback) {
        if (callback == null) return;

        // Force GitHub Actions compute provider if Blender Native target engine is selected
        if (engine != null && (engine.toLowerCase().contains("blender") || engine.toLowerCase().contains("cloud"))) {
            apiKeyManager.saveComputeProvider(CloudProvider.GITHUB_ACTIONS);
        }

        List<String> resolvedUris = resolveReferenceUris(referenceImageUris);

        AIProductionRequest request = new AIProductionRequest(userPrompt, style, engine);
        if (resolvedUris != null) {
            for (String uri : resolvedUris) {
                request.addReferenceImageUri(uri);
            }
        }

        // Query the live, registered Gemini model
        orchestrator.planProductionWithGemini(request, new GeminiApiClient.ApiCallback<ProductionPlan>() {
            @Override
            public void onSuccess(ProductionPlan plan) {
                currentPlan = plan;
                callback.onSuccess(plan);
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    public void executeCurrentPlan(ExecutionEngine.ExecutionCallback callback) {
        if (currentPlan != null && currentPlan.getTaskGraph() != null) {
            // Begin scene transaction for undo/redo rollback capability
            runtime.getTransactionManager().beginTransaction("Execute AI Plan: " + currentPlan.getProjectName());
            
            executionEngine.executeGraph(currentPlan.getTaskGraph(), new ExecutionEngine.ExecutionCallback() {
                @Override
                public void onTaskUpdated(com.example.tasks.TaskNode node, com.example.tasks.TaskGraph graph) {
                    if (callback != null) callback.onTaskUpdated(node, graph);
                }

                @Override
                public void onGraphCompleted(com.example.tasks.TaskGraph graph) {
                    // Commit transaction upon successful completion
                    runtime.getTransactionManager().commitTransaction();
                    if (callback != null) callback.onGraphCompleted(graph);
                }

                @Override
                public void onError(String errorMessage) {
                    // Rollback scene graph transaction on execution failure
                    runtime.getTransactionManager().rollbackTransaction();
                    if (callback != null) callback.onError(errorMessage);
                }
            });
        } else {
            if (callback != null) callback.onError("No active production plan to execute.");
        }
    }

    /**
     * SOLUTION B: Self-Correction Pipeline Trigger
     * Intercepts execution failures from GitHub Actions runner, requests single-turn Python script repair
     * from Gemini with the terminal traceback, and logs status before re-dispatching Attempt 2.
     */
    public void repairBlenderScript(String userPrompt,
                                    String failedScript,
                                    String errorTraceback,
                                    final GeminiApiClient.ApiCallback<String> callback) {
        if (aiCorrector == null) {
            if (callback != null) callback.onError("AICorrector subsystem is not initialized.");
            return;
        }

        if (currentCorrectionAttempt > MAX_REPAIR_ATTEMPTS) {
            String msg = "AI Self-Correction exceeded maximum attempts (" + MAX_REPAIR_ATTEMPTS + ").";
            VynaraLogger.e("AIProductionController: " + msg);
            if (callback != null) callback.onError(msg);
            return;
        }

        VynaraLogger.system("AIProductionController: Initiating AI Self-Correction (Attempt " + currentCorrectionAttempt + "/" + MAX_REPAIR_ATTEMPTS + ")...");

        aiCorrector.correctBlenderScript(userPrompt, failedScript, errorTraceback, new GeminiApiClient.ApiCallback<String>() {
            @Override
            public void onSuccess(String repairedScript) {
                currentCorrectionAttempt++;
                // Exact mandatory in-app console log for Solution B
                VynaraLogger.system("[SYSTEM] AI Self-Correction: Repaired script. Re-dispatching build...");
                if (callback != null) {
                    callback.onSuccess(repairedScript);
                }
            }

            @Override
            public void onError(String errorMessage) {
                VynaraLogger.e("AIProductionController: AI Self-Correction repair failed: " + errorMessage);
                if (callback != null) {
                    callback.onError(errorMessage);
                }
            }
        });
    }

    /**
     * VISUAL REFINEMENT LOOP: Compares the rendered Cycles preview snapshot (render.png)
     * against the reference goal to visually diagnose and refine the Blender script.
     */
    public void visuallyCritiqueAndRefine(String userPrompt,
                                          String currentScript,
                                          File referenceImageFile,
                                          File renderPreviewFile,
                                          final GeminiApiClient.ApiCallback<String> callback) {
        if (aiCorrector == null) {
            if (callback != null) callback.onError("AICorrector subsystem is not initialized.");
            return;
        }
        VynaraLogger.system("AIProductionController: Triggering multimodal visual critique loop...");
        aiCorrector.critiqueAndRefineBlenderScript(userPrompt, currentScript, referenceImageFile, renderPreviewFile, callback);
    }

    public String visuallyCritiqueAndRefineSync(String userPrompt,
                                                String currentScript,
                                                File referenceImageFile,
                                                File renderPreviewFile) {
        if (aiCorrector == null) return null;
        return aiCorrector.critiqueAndRefineBlenderScriptSync(userPrompt, currentScript, referenceImageFile, renderPreviewFile);
    }

    public File getFirstReferenceImageFile(List<String> resolvedUris) {
        if (resolvedUris != null && !resolvedUris.isEmpty()) {
            String path = resolvedUris.get(0);
            if (path != null && !path.trim().isEmpty()) {
                File f = new File(path);
                if (f.exists() && f.length() > 0) return f;
            }
        }
        return null;
    }

    public int getCurrentCorrectionAttempt() {
        return currentCorrectionAttempt;
    }

    public void resetCorrectionAttempts() {
        this.currentCorrectionAttempt = 1;
    }

    /**
     * Resolves content:// URIs from the Android system photo picker into local cache files,
     * ensuring Gemini Vision can read the actual image bytes.
     */
    public List<String> resolveReferenceUris(List<String> uris) {
        List<String> resolved = new ArrayList<>();
        if (uris == null || uris.isEmpty()) return resolved;

        for (String uriStr : uris) {
            if (uriStr == null || uriStr.trim().isEmpty()) continue;
            
            if (uriStr.startsWith("content://")) {
                try {
                    Uri uri = Uri.parse(uriStr);
                    InputStream inputStream = context.getContentResolver().openInputStream(uri);
                    if (inputStream != null) {
                        File cacheDir = new File(context.getCacheDir(), "ref_images");
                        if (!cacheDir.exists()) cacheDir.mkdirs();
                        
                        File destFile = new File(cacheDir, "ref_" + System.currentTimeMillis() + ".jpg");
                        FileOutputStream outputStream = new FileOutputStream(destFile);
                        
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                        outputStream.flush();
                        outputStream.close();
                        inputStream.close();
                        
                        resolved.add(destFile.getAbsolutePath());
                        continue;
                    }
                } catch (Exception e) {
                    VynaraLogger.e("AIProductionController: Failed resolving content URI: " + e.getMessage());
                }
            }
            resolved.add(uriStr);
        }
        return resolved;
    }

    public Context getContext() { return context; }
    public ProjectRuntime getRuntime() { return runtime; }
    public ApiKeyManager getApiKeyManager() { return apiKeyManager; }
    public GeminiApiClient getApiClient() { return apiClient; }
    public GeminiProvider getGeminiProvider() { return geminiProvider; }
    public KnowledgeManager getKnowledgeManager() { return knowledgeManager; }
    public ToolRegistry getToolRegistry() { return toolRegistry; }
    public ThreeDEngine getThreeDEngine() { return threeDEngine; }
    public CharacterManager getCharacterManager() { return characterManager; }
    public ValidationManager getValidationManager() { return validationManager; }
    public ToolExecutor getToolExecutor() { return toolExecutor; }
    public ExecutionEngine getExecutionEngine() { return executionEngine; }
    public AIOrchestrator getOrchestrator() { return orchestrator; }
    public ProductionPlan getCurrentPlan() { return currentPlan; }
    public AICorrector getAiCorrector() { return aiCorrector; }
    public DirectorAgent getDirectorAgent() { return directorAgent; }
}