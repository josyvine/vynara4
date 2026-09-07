package com.example.ai.agents;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import com.example.ai.ApiKeyManager;
import com.example.ai.GeminiApiClient;
import com.example.ai.protocol.AIDirectorSpec;
import com.example.utils.VynaraLogger;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DirectorAgent {
    private final GeminiApiClient apiClient;
    private final ApiKeyManager apiKeyManager;

    public interface DirectorCallback {
        void onSpecReady(AIDirectorSpec spec);
        void onError(String errorMessage);
    }

    public DirectorAgent(GeminiApiClient apiClient, ApiKeyManager apiKeyManager) {
        this.apiClient = apiClient;
        this.apiKeyManager = apiKeyManager;
    }

    /**
     * Phase 1: Formulates the comprehensive 4-worker scene specification using Gemini Vision.
     * Analyzes reference photos to decompose any prompt into dynamic architectural/automotive layers.
     */
    public void formulateDirectorSpec(final String userPrompt,
                                      final String style,
                                      final List<String> referenceImageUris,
                                      final DirectorCallback callback) {
        if (callback == null) return;

        if (!apiKeyManager.hasApiKey()) {
            String msg = "DirectorAgent: Gemini API Key missing in Settings. Cannot run live AI generation.";
            VynaraLogger.e(msg);
            callback.onError(msg);
            return;
        }

        final String activeModel = apiKeyManager.getSelectedModel();

        // 1. Read and downscale reference images for Gemini Vision
        List<String> base64Images = new ArrayList<>();
        if (referenceImageUris != null && !referenceImageUris.isEmpty()) {
            for (String uriOrPath : referenceImageUris) {
                String b64 = readImageAsBase64(uriOrPath);
                if (b64 != null && !b64.isEmpty()) {
                    base64Images.add(b64);
                } else {
                    VynaraLogger.w("DirectorAgent: Reference image could not be converted to Base64: " + uriOrPath);
                }
            }
        }

        String systemInstruction = buildDirectorSystemInstruction();

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("USER PROMPT: ").append(userPrompt).append("\n");
        promptBuilder.append("REQUESTED STYLE: ").append(style).append("\n");
        if (!base64Images.isEmpty()) {
            promptBuilder.append("VISUAL REFERENCE ATTACHED: Inspect the attached visual reference image(s). ")
                         .append("Deconstruct the actual physical geometry, automotive curves or architectural cantilever slabs, ")
                         .append("wheel designs, materials, and lighting atmosphere. Do not invent generic cubes.\n");
        }

        VynaraLogger.system("DirectorAgent: Formulating dynamic 4-Worker scene spec via Gemini Vision [" + activeModel + "] with " + base64Images.size() + " image(s)...");

        // 2. Dispatch Multimodal Structured Request
        apiClient.generateStructuredJson(
                apiKeyManager.getApiKey(),
                activeModel,
                systemInstruction,
                promptBuilder.toString(),
                base64Images,
                new GeminiApiClient.ApiCallback<String>() {
                    @Override
                    public void onSuccess(String jsonResult) {
                        try {
                            String cleanJson = jsonResult.trim();
                            if (cleanJson.startsWith("```json")) {
                                cleanJson = cleanJson.substring(7);
                            } else if (cleanJson.startsWith("```")) {
                                cleanJson = cleanJson.substring(3);
                            }
                            if (cleanJson.endsWith("```")) {
                                cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
                            }
                            cleanJson = cleanJson.trim();

                            JSONObject root = new JSONObject(cleanJson);
                            AIDirectorSpec spec = AIDirectorSpec.fromJson(root, activeModel);
                            
                            VynaraLogger.system("DirectorAgent: Dynamic multi-agent specification formulated successfully for [" + spec.getSceneType() + "].");
                            callback.onSpecReady(spec);
                        } catch (Exception e) {
                            String err = "DirectorAgent: Failed to parse Gemini specification: " + e.getMessage();
                            VynaraLogger.e(err, e);
                            callback.onError(err);
                        }
                    }

                    @Override
                    public void onError(String errorMessage) {
                        String err = "DirectorAgent: Google Gemini API error: " + errorMessage;
                        VynaraLogger.e(err);
                        callback.onError(err);
                    }
                }
        );
    }

    private String buildDirectorSystemInstruction() {
        return "You are the 3D Master Art Director & Spatial Architect (like Fable 5 / SKILL.md).\n" +
                "YOUR ROLE:\n" +
                "- You NEVER write Python code or Blender operators directly.\n" +
                "- Your job is to analyze the user's prompt and reference images, and decompose the scene into a structured 4-Worker dynamic specification.\n" +
                "- Never settle for generic primitives or cubes. Define aerodynamic curvatures, bevels, architectural cantilevers, and authentic wheel orientations.\n\n" +
                "CINEMATIC DIRECTIVES:\n" +
                "1. Worker 1 (Structure): Define primary volume, chassis or building envelope, bevel radius (e.g. 0.08m), and whether subdivision surface is required.\n" +
                "2. Worker 2 (Details & Hardware): If vehicle, specify 4 vertical wheels with Euler rotation (90 deg on X-axis), rims, and glass. If architecture, specify cantilever balconies, pool basin, and terrain. Props must snap to ground.\n" +
                "3. Worker 3 (PBR Materials): Define Base Color, Metallic (0.0 to 1.0), Roughness (0.05 to 0.9), and Transmission Weight (0.9 for glass/water) conforming to Blender 4.2+ Principled BSDF.\n" +
                "4. Worker 4 (Cinematics): Select focal length (35mm for wide architecture/scenes, 50mm for natural perspective, 85mm for hero products), depth of field f/1.8, and Sun elevation/azimuth with warm/cool lighting contrast.\n\n" +
                "OUTPUT RAW STRICT JSON ONLY (NO MARKDOWN FENCES):\n" +
                "{\n" +
                "  \"sceneType\": \"string\",\n" +
                "  \"mood\": \"string\",\n" +
                "  \"visualStyleNotes\": \"string\",\n" +
                "  \"objectCategory\": \"vehicle | architecture | character | nature | prop\",\n" +
                "  \"workers\": {\n" +
                "    \"w1_structure\": \"Detailed structural guidelines with dimensions and bevel requirements\",\n" +
                "    \"w2_details\": \"Sub-part hardware, wheel rotation (90 deg on X-axis), and prop placement\",\n" +
                "    \"w3_materials\": \"PBR shader properties for hero surface, glass, and details\",\n" +
                "    \"w4_cinematics\": \"Lighting rig and camera optics framing\"\n" +
                "  },\n" +
                "  \"camera\": {\n" +
                "    \"focalLengthMm\": 50.0,\n" +
                "    \"apertureFStop\": 1.8,\n" +
                "    \"focusDistance\": 5.5,\n" +
                "    \"position\": [0.0, -8.0, 3.2],\n" +
                "    \"target\": [0.0, 0.0, 1.0]\n" +
                "  },\n" +
                "  \"lighting\": {\n" +
                "    \"useVolumetrics\": true,\n" +
                "    \"volumetricDensity\": 0.015,\n" +
                "    \"sunElevation\": 25.0,\n" +
                "    \"sunAzimuth\": -35.0,\n" +
                "    \"sunIntensity\": 4.5,\n" +
                "    \"ambientColorHex\": \"#1A2530\"\n" +
                "  },\n" +
                "  \"palette\": {\n" +
                "    \"primaryColorHex\": \"#2C3E50\",\n" +
                "    \"secondaryColorHex\": \"#BDC3C7\",\n" +
                "    \"accentColorHex\": \"#E74C3C\"\n" +
                "  },\n" +
                "  \"seeds\": {\n" +
                "    \"seedTerrain\": 101,\n" +
                "    \"seedHero\": 202,\n" +
                "    \"seedVegetation\": 303,\n" +
                "    \"seedLighting\": 404\n" +
                "  }\n" +
                "}";
    }

    /**
     * Generates a targeted code-synthesis instruction for each specialized worker agent.
     */
    public static String buildWorkerPrompt(AIDirectorSpec spec, int workerIndex, String userPrompt) {
        StringBuilder sb = new StringBuilder();
        sb.append("SCENE GOAL: ").append(userPrompt).append("\n");
        if (spec != null) {
            sb.append("SCENE TYPE: ").append(spec.getSceneType()).append(" | MOOD: ").append(spec.getMood()).append("\n");
            sb.append("PALETTE: Primary=").append(spec.getPrimaryColorHex())
              .append(", Secondary=").append(spec.getSecondaryColorHex()).append("\n");
        }

        switch (workerIndex) {
            case 1: // Worker 1: Core Structure
                sb.append("\nTASK: WORKER 1 (STRUCTURE & HULL)\n")
                  .append("- Generate ONLY the primary structural geometry.\n")
                  .append("- Always add a BEVEL modifier (width=0.04, segments=3) and enable smooth shading (`bpy.ops.object.shade_smooth()`).\n")
                  .append("- Output raw Blender Python code inside ```python.");
                break;
            case 2: // Worker 2: Details & Sub-parts
                sb.append("\nTASK: WORKER 2 (DETAILS & HARDWARE)\n")
                  .append("- Generate detailed sub-assemblies (e.g. wheels, windows, doors, trim, props).\n")
                  .append("- CRITICAL: If wheels or cylinders on an axle, rotate 90 degrees on X/Y axis (`rotation=(0, math.radians(90), 0)`). Never leave wheels standing upright on Z.\n")
                  .append("- Output raw Blender Python code inside ```python.");
                break;
            case 3: // Worker 3: PBR Materials & Shaders
                sb.append("\nTASK: WORKER 3 (PBR MATERIALS)\n")
                  .append("- Configure Principled BSDF materials using Blender 4.2+ socket names (e.g. 'Transmission Weight').\n")
                  .append("- Add metallic car paint, roughness maps, or glass transmission where appropriate.\n")
                  .append("- Output raw Blender Python code inside ```python.");
                break;
            case 4: // Worker 4: Cinematics & Lighting
            default:
                sb.append("\nTASK: WORKER 4 (LIGHTING & CAMERA)\n")
                  .append("- Configure the Camera with focal length and depth of field.\n")
                  .append("- Add Sun light and key/fill lighting, then export GLB and render Cycles preview.\n")
                  .append("- Output raw Blender Python code inside ```python.");
                break;
        }

        return sb.toString();
    }

    /**
     * Resolves local file paths, URIs, or base64 strings, downscaling images to max 1024px dimension.
     */
    private String readImageAsBase64(String pathOrUri) {
        if (pathOrUri == null || pathOrUri.trim().isEmpty()) return null;

        String cleanPath = pathOrUri.trim();

        if (cleanPath.startsWith("file://")) {
            cleanPath = cleanPath.substring(7);
        }

        if (cleanPath.startsWith("data:image") && cleanPath.contains("base64,")) {
            return cleanPath.substring(cleanPath.indexOf("base64,") + 7).trim();
        }

        try {
            File imageFile = new File(cleanPath);
            if (!imageFile.exists() || imageFile.length() == 0) {
                if (cleanPath.length() > 100 && !cleanPath.contains(File.separator)) {
                    return cleanPath;
                }
                return null;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);

            int maxDim = Math.max(options.outWidth, options.outHeight);
            int inSampleSize = 1;
            while (maxDim / inSampleSize > 1024) {
                inSampleSize *= 2;
            }

            options.inJustDecodeBounds = false;
            options.inSampleSize = inSampleSize;
            Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);

            if (bitmap == null) return null;

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream);
            byte[] imageBytes = outputStream.toByteArray();
            bitmap.recycle();

            return Base64.encodeToString(imageBytes, Base64.NO_WRAP);

        } catch (Exception e) {
            VynaraLogger.e("DirectorAgent: Error reading reference image: " + e.getMessage());
            return null;
        }
    }
}