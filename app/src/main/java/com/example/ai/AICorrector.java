package com.example.ai;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import com.example.engine.Scene;
import com.example.tools.ToolExecutor;
import com.example.tools.ToolOperation;
import com.example.utils.VynaraLogger;
import com.example.validation.ValidationResult;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class AICorrector {
    private final ToolExecutor toolExecutor;
    private final AIOrchestrator aiOrchestrator;
    private final Scene activeScene;

    private static final int SCRIPT_REPAIR_TIMEOUT_SECONDS = 35;

    public AICorrector(ToolExecutor toolExecutor, AIOrchestrator aiOrchestrator) {
        this(toolExecutor, aiOrchestrator, null);
    }

    public AICorrector(ToolExecutor toolExecutor, AIOrchestrator aiOrchestrator, Scene activeScene) {
        this.toolExecutor = toolExecutor;
        this.aiOrchestrator = aiOrchestrator;
        this.activeScene = activeScene;
    }

    /**
     * Evaluates validation inspection results and executes the internal AI correction loop:
     * Generate -> Validate -> Inspect -> Problem Detection -> Repair Selection -> Correction.
     */
    public boolean applyCorrections(List<ValidationResult> inspectionResults) {
        if (inspectionResults == null || inspectionResults.isEmpty()) {
            return true;
        }

        boolean allCorrectionsSuccessful = true;

        for (ValidationResult vr : inspectionResults) {
            if (vr.getSeverity() == ValidationResult.Severity.ERROR ||
                vr.getSeverity() == ValidationResult.Severity.CRITICAL) {

                boolean repairExecuted = executeIntelligenceDrivenRepair(vr);
                if (!repairExecuted) {
                    allCorrectionsSuccessful = false;
                }
            }
        }

        return allCorrectionsSuccessful;
    }

    /**
     * SOLUTION B: AI Script Corrector (Async)
     * Analyzes the original user prompt, the faulty Blender script, and the exact terminal traceback
     * from error.txt, asking Gemini to return an executable, zero-error replacement.
     */
    public void correctBlenderScript(String userPrompt,
                                     String failedScript,
                                     String errorTraceback,
                                     final GeminiApiClient.ApiCallback<String> callback) {
        if (aiOrchestrator == null || aiOrchestrator.getApiKeyManager() == null || !aiOrchestrator.getApiKeyManager().hasApiKey()) {
            if (callback != null) {
                callback.onError("Cannot repair script: Gemini API key is missing or unconfigured.");
            }
            return;
        }

        String repairInstruction = buildBlenderRepairSystemInstruction();
        String repairPrompt = buildBlenderRepairUserPrompt(userPrompt, failedScript, errorTraceback);

        VynaraLogger.system("AICorrector: Dispatching single-turn script repair request to Gemini...");

        aiOrchestrator.getApiClient().generateContent(
                aiOrchestrator.getApiKeyManager().getApiKey(),
                aiOrchestrator.getApiKeyManager().getSelectedModel(),
                repairInstruction,
                repairPrompt,
                new GeminiApiClient.ApiCallback<String>() {
                    @Override
                    public void onSuccess(String result) {
                        String cleanedScript = cleanAndValidateScript(result);
                        if (cleanedScript.isEmpty()) {
                            VynaraLogger.e("AICorrector: Gemini returned an empty or invalid repair script.");
                            if (callback != null) {
                                callback.onError("Gemini returned empty or invalid repair script.");
                            }
                        } else {
                            VynaraLogger.system("AICorrector: Successfully repaired Python script (" + cleanedScript.length() + " chars).");
                            if (callback != null) {
                                callback.onSuccess(cleanedScript);
                            }
                        }
                    }

                    @Override
                    public void onError(String error) {
                        VynaraLogger.e("AICorrector: Script repair failed from Gemini API: " + error);
                        if (callback != null) {
                            callback.onError(error);
                        }
                    }
                }
        );
    }

    /**
     * SOLUTION B: AI Script Corrector (Sync / Blocking)
     * Convenience method for background execution threads that need to synchronously wait
     * for the repaired script.
     */
    public String correctBlenderScriptSync(String userPrompt, String failedScript, String errorTraceback) {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> repairedScriptRef = new AtomicReference<>(null);

        correctBlenderScript(userPrompt, failedScript, errorTraceback, new GeminiApiClient.ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                repairedScriptRef.set(result);
                latch.countDown();
            }

            @Override
            public void onError(String error) {
                VynaraLogger.e("AICorrector (Sync): Repair error: " + error);
                latch.countDown();
            }
        });

        try {
            boolean completed = latch.await(SCRIPT_REPAIR_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                VynaraLogger.e("AICorrector (Sync): Script repair timed out after " + SCRIPT_REPAIR_TIMEOUT_SECONDS + "s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            VynaraLogger.e("AICorrector (Sync): Repair interrupted: " + e.getMessage());
        }

        return repairedScriptRef.get();
    }

    /**
     * VISUAL CRITIQUE & REFINEMENT (Async)
     * Compares the Cycles preview render against the reference photo using Gemini Vision to spot and fix
     * aesthetic defects (boxiness, wheel alignment, bad lighting).
     */
    public void critiqueAndRefineBlenderScript(String userPrompt,
                                              String currentScript,
                                              File referenceImageFile,
                                              File renderPreviewFile,
                                              final GeminiApiClient.ApiCallback<String> callback) {
        if (aiOrchestrator == null || aiOrchestrator.getApiKeyManager() == null || !aiOrchestrator.getApiKeyManager().hasApiKey()) {
            if (callback != null) callback.onError("Cannot critique scene: Gemini API key missing.");
            return;
        }

        String b64Ref = encodeImageFileToBase64(referenceImageFile);
        String b64Render = encodeImageFileToBase64(renderPreviewFile);

        VynaraLogger.system("AICorrector: Dispatching multimodal visual critique to Gemini Vision...");

        aiOrchestrator.getApiClient().critiqueAndRefineRender(
                aiOrchestrator.getApiKeyManager().getApiKey(),
                aiOrchestrator.getApiKeyManager().getSelectedModel(),
                userPrompt,
                currentScript,
                b64Ref,
                b64Render,
                new GeminiApiClient.ApiCallback<String>() {
                    @Override
                    public void onSuccess(String result) {
                        String cleaned = cleanAndValidateScript(result);
                        if (cleaned.isEmpty()) {
                            if (callback != null) callback.onError("Gemini returned empty refined script.");
                        } else {
                            VynaraLogger.system("AICorrector: Visual critique successfully refined Python script (" + cleaned.length() + " chars).");
                            if (callback != null) callback.onSuccess(cleaned);
                        }
                    }

                    @Override
                    public void onError(String errorMessage) {
                        VynaraLogger.e("AICorrector: Visual refinement failed: " + errorMessage);
                        if (callback != null) callback.onError(errorMessage);
                    }
                }
        );
    }

    /**
     * VISUAL CRITIQUE & REFINEMENT (Sync / Blocking)
     */
    public String critiqueAndRefineBlenderScriptSync(String userPrompt,
                                                    String currentScript,
                                                    File referenceImageFile,
                                                    File renderPreviewFile) {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> refinedScriptRef = new AtomicReference<>(null);

        critiqueAndRefineBlenderScript(userPrompt, currentScript, referenceImageFile, renderPreviewFile, new GeminiApiClient.ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                refinedScriptRef.set(result);
                latch.countDown();
            }

            @Override
            public void onError(String error) {
                VynaraLogger.e("AICorrector (Sync): Visual critique error: " + error);
                latch.countDown();
            }
        });

        try {
            boolean completed = latch.await(SCRIPT_REPAIR_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                VynaraLogger.e("AICorrector (Sync): Visual critique timed out after " + SCRIPT_REPAIR_TIMEOUT_SECONDS + "s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            VynaraLogger.e("AICorrector (Sync): Interrupted: " + e.getMessage());
        }

        return refinedScriptRef.get();
    }

    private String buildBlenderRepairSystemInstruction() {
        return "You are an elite Blender Python (`bpy`) core engineer and debugger specializing in automated 3D asset generation.\n" +
                "A cloud worker running headless Blender failed with a runtime exception/traceback while executing a generated script.\n" +
                "Your objective is to fix the exact error identified in the traceback, preserve all 3D assets/materials from the prompt, and output the entire corrected script.\n\n" +
                "CRITICAL REQUIREMENTS:\n" +
                "1. Output ONLY the fully corrected, executable Python script inside a single ```python ... ``` block. No conversational filler, greetings, or explanations.\n" +
                "2. Read the error traceback carefully and fix the specific failing line, parameter, enum, or syntax.\n" +
                "3. API GUARDS:\n" +
                "   - Mesh primitives must use `bpy.ops.mesh.primitive_..._add` (never create or raw call without add).\n" +
                "   - Lights must use `bpy.ops.object.light_add(type=...)` (never `bpy.ops.light.add`).\n" +
                "   - Texture types in `bpy.data.textures.new(...)` MUST be one of: ('NONE', 'BLEND', 'CLOUDS', 'DISTORTED_NOISE', 'IMAGE', 'MAGIC', 'MARBLE', 'MUSGRAVE', 'NOISE', 'STUCCI', 'VORONOI', 'WOOD'). Never invent custom enum names.\n" +
                "   - Modifiers must use valid Blender types: 'SUBSURF', 'BEVEL', 'BOOLEAN', 'SOLIDIFY', 'ARRAY', 'MIRROR', etc.\n" +
                "   - Ensure `bpy.ops.export_scene.gltf` or `bpy.ops.wm.save_as_mainfile` runs at the very end as designed.\n" +
                "4. COMPLETE SCENE: Do not return partial snippets, comments like `# ... rest of code`, or placeholders. Return the full complete scene script.";
    }

    private String buildBlenderRepairUserPrompt(String userPrompt, String failedScript, String errorTraceback) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== WHAT WAS BEING BUILT (USER PROMPT) ===\n")
          .append(userPrompt != null ? userPrompt : "Generate 3D Scene")
          .append("\n\n")
          .append("=== EXACT BLENDER TERMINAL ERROR / TRACEBACK (FROM error.txt) ===\n")
          .append(errorTraceback != null ? errorTraceback : "Unknown execution failure")
          .append("\n\n")
          .append("=== THE FAULTY SCRIPT THAT FAILED ===\n")
          .append(failedScript != null ? failedScript : "# No script content");
        return sb.toString();
    }

    private String cleanAndValidateScript(String rawResponse) {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            return "";
        }

        String cleaned = rawResponse.trim();

        if (cleaned.startsWith("```python")) {
            cleaned = cleaned.substring("```python".length());
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }

        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        cleaned = cleaned.trim();

        if (!cleaned.contains("import bpy") && !cleaned.contains("bpy.")) {
            int idx = cleaned.indexOf("import bpy");
            if (idx >= 0) {
                cleaned = cleaned.substring(idx).trim();
            }
        }

        return cleaned;
    }

    private String encodeImageFileToBase64(File file) {
        if (file == null || !file.exists() || file.length() == 0) return null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);

            int maxDim = Math.max(options.outWidth, options.outHeight);
            int inSampleSize = 1;
            while (maxDim / inSampleSize > 1024) {
                inSampleSize *= 2;
            }

            options.inJustDecodeBounds = false;
            options.inSampleSize = inSampleSize;
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (bitmap == null) return null;

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos);
            byte[] bytes = baos.toByteArray();
            bitmap.recycle();

            return Base64.encodeToString(bytes, Base64.NO_WRAP);
        } catch (Exception e) {
            VynaraLogger.e("AICorrector: Failed to encode image to base64: " + e.getMessage());
            return null;
        }
    }

    /**
     * Consults Gemini AI for a local repair plan, falling back to local deterministic repairs if offline.
     */
    private boolean executeIntelligenceDrivenRepair(ValidationResult vr) {
        if (vr == null || vr.getMessage() == null || toolExecutor == null) {
            return false;
        }

        if (aiOrchestrator == null || aiOrchestrator.getApiKeyManager() == null || !aiOrchestrator.getApiKeyManager().hasApiKey()) {
            return executeLocalDeterministicRepair(vr);
        }

        String sceneContextJson = activeScene != null ? AIContext.buildSceneContextJson(activeScene) : "{}";
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean repairSuccess = new AtomicBoolean(false);

        aiOrchestrator.requestCorrectionPlan(vr.getMessage(), vr.getCategory().name(), sceneContextJson, new GeminiApiClient.ApiCallback<String>() {
            @Override
            public void onSuccess(String jsonResult) {
                try {
                    JSONObject opObj = new JSONObject(jsonResult);
                    String toolId = opObj.optString("toolId", null);

                    if (toolId != null && !toolId.trim().isEmpty()) {
                        ToolOperation repairOp = new ToolOperation(toolId);
                        JSONObject paramsObj = opObj.optJSONObject("parameters");
                        if (paramsObj != null) {
                            java.util.Iterator<String> keys = paramsObj.keys();
                            while (keys.hasNext()) {
                                String key = keys.next();
                                Object val = paramsObj.opt(key);
                                if (val != null) {
                                    repairOp.setParam(key, val);
                                }
                            }
                        }
                        boolean executed = toolExecutor.executeOperation(repairOp);
                        repairSuccess.set(executed);
                    } else {
                        repairSuccess.set(executeLocalDeterministicRepair(vr));
                    }
                } catch (Exception e) {
                    repairSuccess.set(executeLocalDeterministicRepair(vr));
                }
                latch.countDown();
            }

            @Override
            public void onError(String errorMessage) {
                repairSuccess.set(executeLocalDeterministicRepair(vr));
                latch.countDown();
            }
        });

        try {
            boolean ok = latch.await(5, TimeUnit.SECONDS);
            if (!ok) {
                return executeLocalDeterministicRepair(vr);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return executeLocalDeterministicRepair(vr);
        }

        return repairSuccess.get();
    }

    private boolean executeLocalDeterministicRepair(ValidationResult vr) {
        String msg = vr.getMessage().toLowerCase();

        // 1. Missing or Degenerate Mesh Repair
        if (msg.contains("mesh") || msg.contains("vertex") || msg.contains("vertices")) {
            ToolOperation repairMeshOp = new ToolOperation("geometry.create_primitive")
                    .setParam("type", "cube")
                    .setParam("width", 1.5f)
                    .setParam("height", 1.5f)
                    .setParam("depth", 1.5f);
            return toolExecutor.executeOperation(repairMeshOp);
        }

        // 2. Missing Material Shading Repair
        if (msg.contains("material") || msg.contains("color") || msg.contains("shader")) {
            ToolOperation repairMatOp = new ToolOperation("material.set_properties")
                    .setParam("colorHex", "#A0A5BD")
                    .setParam("metallic", 0.1f)
                    .setParam("roughness", 0.5f);
            return toolExecutor.executeOperation(repairMatOp);
        }

        // 3. Unbound Skin or Weight Normalization Repair
        if (msg.contains("skin") || msg.contains("weight") || msg.contains("skeleton")) {
            ToolOperation bindOp = new ToolOperation("skeleton.bind");
            return toolExecutor.executeOperation(bindOp);
        }

        // 4. Default Fallback Re-validation Tool
        ToolOperation checkOp = new ToolOperation("validation.check_mesh");
        return toolExecutor.executeOperation(checkOp);
    }

    public ToolExecutor getToolExecutor() {
        return toolExecutor;
    }
}