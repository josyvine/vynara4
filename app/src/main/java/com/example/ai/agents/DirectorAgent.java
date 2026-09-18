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
import java.util.Locale;

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

        // 1. Differentiate between 2D reference images and imported 3D models
        List<String> base64Images = new ArrayList<>();
        List<String> attached3DModels = new ArrayList<>();

        if (referenceImageUris != null && !referenceImageUris.isEmpty()) {
            for (String uriOrPath : referenceImageUris) {
                if (is3DModelUri(uriOrPath)) {
                    String modelName = extractModelName(uriOrPath);
                    attached3DModels.add(modelName);
                    VynaraLogger.system("DirectorAgent: Attached 3D model metadata identified: " + modelName);
                } else {
                    String b64 = readImageAsBase64(uriOrPath);
                    if (b64 != null && !b64.isEmpty()) {
                        base64Images.add(b64);
                    } else {
                        VynaraLogger.w("DirectorAgent: Reference image could not be converted to Base64: " + uriOrPath);
                    }
                }
            }
        }

        String systemInstruction = buildDirectorSystemInstruction();

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("USER PROMPT: ").append(userPrompt).append("\n");
        promptBuilder.append("REQUESTED STYLE: ").append(style).append("\n");

        if (!attached3DModels.isEmpty()) {
            promptBuilder.append("ACTIVE 3D MODEL ATTACHED: The scene contains an imported 3D mesh asset: ")
                         .append(String.join(", ", attached3DModels))
                         .append(". Worker 1 must import the normalized asset (inputs/input_model.glb) into the scene rather than generating a replacement placeholder chassis.\n");
        }

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

    /**
     * Autonomous Driving / Cinematics Pipeline:
     * Generates a fully automated production plan for an imported asset (e.g. vehicle, character, prop)
     * without requiring any manual user rigging, tagging, or camera positioning.
     */
    public void formulateAutonomousAssetSpec(final String userPrompt,
                                            final String modelName,
                                            final String modelCategory,
                                            final DirectorCallback callback) {
        if (callback == null) return;

        if (!apiKeyManager.hasApiKey()) {
            String msg = "DirectorAgent: Gemini API Key missing in Settings. Cannot run autonomous generation.";
            VynaraLogger.e(msg);
            callback.onError(msg);
            return;
        }

        final String activeModel = apiKeyManager.getSelectedModel();
        String systemInstruction = buildDirectorSystemInstruction();

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("USER PROMPT: ").append(userPrompt).append("\n");
        promptBuilder.append("IMPORTED ASSET NAME: ").append(modelName).append("\n");
        promptBuilder.append("INFERRED CATEGORY: ").append(modelCategory).append("\n");
        promptBuilder.append("DIRECTIVE: Generate a high-speed, cinematic, photorealistic sequence. ")
                     .append("If category is 'Vehicle', generate procedural road spline, guardrails, automated wheel rotation drivers ")
                     .append("(angular velocity = linear speed / wheel radius), shrinkwrap ground sensors, rear-wheel low-angle camera framing, ")
                     .append("and 180-degree optical shutter motion blur. No static poly placeholders. Import model from inputs/input_model.glb.");

        VynaraLogger.system("DirectorAgent: Formulating autonomous asset animation spec for [" + modelName + "]...");

        apiClient.generateStructuredJson(
                apiKeyManager.getApiKey(),
                activeModel,
                systemInstruction,
                promptBuilder.toString(),
                new ArrayList<>(),
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

                            VynaraLogger.system("DirectorAgent: Autonomous spec ready for [" + spec.getSceneType() + "].");
                            callback.onSpecReady(spec);
                        } catch (Exception e) {
                            String err = "DirectorAgent: Failed to parse autonomous specification: " + e.getMessage();
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
                "- Your job is to analyze the user's prompt, imported asset context, and reference images, and decompose the scene into a structured 4-Worker dynamic specification.\n" +
                "- Never settle for generic primitives or low-poly cubes. Define aerodynamic curvatures, bevels, architectural cantilevers, and authentic wheel orientations.\n\n" +
                "CINEMATIC DIRECTIVES:\n" +
                "1. Worker 1 (Structure & Environment):\n" +
                "   - Define primary volume, chassis, or building envelope.\n" +
                "   - If an imported 3D asset exists, instruct Worker 1 to import it from 'inputs/input_model.glb' (or 'inputs/input_model.fbx').\n" +
                "   - For driving/high-speed shots: define a procedural road ribbon with asphalt, curbs, steel guardrails, and street lamps along a path curve.\n" +
                "   - Always specify bevel radius (e.g. 0.04m - 0.08m) and smooth shading.\n" +
                "2. Worker 2 (Details, Kinematics & Rigging):\n" +
                "   - If vehicle: autonomously identify/rig 4 vertical wheels (90 deg on X/Y axis), brake calipers, and chassis.\n" +
                "   - Bind automated wheel spin drivers tied to forward displacement (rotation = distance / radius).\n" +
                "   - Bind axle ground sensors (Shrinkwrap constraint targeting the road surface).\n" +
                "3. Worker 3 (PBR Materials & Shaders):\n" +
                "   - Conforming to Blender 4.2+ Principled BSDF.\n" +
                "   - High-detail 4K asphalt with normal map pebble bump, roughness variations, and wet/dry bitumen specular.\n" +
                "   - Metallic car paint with clearcoat, darkened glass transmission (0.9), and matte tire rubber.\n" +
                "4. Worker 4 (Cinematics, Camera Optics & Lighting):\n" +
                "   - Low-angle ground clearance camera (15cm off ground, positioned outside rear wheel arch pointing forward along car flank).\n" +
                "   - Wide-angle focal length (18mm - 24mm) to amplify speed parallax.\n" +
                "   - Depth of field locked to rear rim (f/2.8).\n" +
                "   - Enable 180-degree optical motion blur (shutter = 0.5) to streak road lines and spin wheels.\n" +
                "   - Low-horizon Sun lighting with rim-light highlights, lens flare, and strict Blender 4.2 AgX color management ('AgX - High Contrast').\n\n" +
                "OUTPUT RAW STRICT JSON ONLY (NO MARKDOWN FENCES):\n" +
                "{\n" +
                "  \"sceneType\": \"string\",\n" +
                "  \"mood\": \"string\",\n" +
                "  \"visualStyleNotes\": \"string\",\n" +
                "  \"objectCategory\": \"vehicle | architecture | character | nature | prop\",\n" +
                "  \"workers\": {\n" +
                "    \"w1_structure\": \"Structural guidelines, chassis envelope or procedural road curve with guardrails\",\n" +
                "    \"w2_details\": \"Sub-part hardware, wheel spin drivers, shrinkwrap ground sensors, and props\",\n" +
                "    \"w3_materials\": \"PBR shader properties: 4K asphalt, metallic paint, glass transmission, tire rubber\",\n" +
                "    \"w4_cinematics\": \"Low ground clearance camera (18mm-24mm), rear-wheel lock, 180 deg motion blur, AgX sun lighting\"\n" +
                "  },\n" +
                "  \"camera\": {\n" +
                "    \"focalLengthMm\": 20.0,\n" +
                "    \"apertureFStop\": 2.8,\n" +
                "    \"focusDistance\": 1.2,\n" +
                "    \"position\": [0.95, -1.8, 0.25],\n" +
                "    \"target\": [0.85, 1.5, 0.35]\n" +
                "  },\n" +
                "  \"lighting\": {\n" +
                "    \"useVolumetrics\": true,\n" +
                "    \"volumetricDensity\": 0.012,\n" +
                "    \"sunElevation\": 18.0,\n" +
                "    \"sunAzimuth\": -45.0,\n" +
                "    \"sunIntensity\": 5.5,\n" +
                "    \"ambientColorHex\": \"#1A2530\"\n" +
                "  },\n" +
                "  \"palette\": {\n" +
                "    \"primaryColorHex\": \"#D4AF37\",\n" +
                "    \"secondaryColorHex\": \"#222222\",\n" +
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
            case 1: // Worker 1: Core Structure & Procedural Environment
                sb.append("\nTASK: WORKER 1 (STRUCTURE & ENVIRONMENT)\n")
                  .append("- If an imported 3D asset is in 'inputs/', import it using `bpy.ops.import_scene.gltf(filepath='inputs/input_model.glb')` (or 'input_model.glb').\n")
                  .append("- Generate primary structural geometry or procedural road spline with curbs, barrier guardrail, and lamp poles.\n")
                  .append("- If car: build aerodynamic chassis envelope or position imported asset at origin.\n")
                  .append("- Always add BEVEL modifier (width=0.04, segments=3) and enable smooth shading (`bpy.ops.object.shade_smooth()`).\n")
                  .append("- Output raw Blender Python code inside ```python.");
                break;
            case 2: // Worker 2: Details, Hardware & Autonomous Rigging
                sb.append("\nTASK: WORKER 2 (DETAILS, HARDWARE & RIGGING)\n")
                  .append("- Generate detailed sub-assemblies (e.g. 4 wheels rotated 90 deg, brake calipers, rims, glass, road barriers).\n")
                  .append("- For driving shots: automatically calculate wheel radius and add rotational drivers (rotation = distance / radius).\n")
                  .append("- Add ground shrinkwrap constraint to project wheel axles onto the road surface.\n")
                  .append("- Output raw Blender Python code inside ```python.");
                break;
            case 3: // Worker 3: PBR Materials & Shaders
                sb.append("\nTASK: WORKER 3 (PBR MATERIALS)\n")
                  .append("- Configure Principled BSDF materials using Blender 4.2+ socket names (e.g. 'Transmission Weight', 'Roughness', 'Metallic').\n")
                  .append("- Road: 4K asphalt procedural texture with pebble bump and roughness variations.\n")
                  .append("- Vehicle: Metallic car paint with clearcoat, matte rubber on tires, and chrome on rims.\n")
                  .append("- Output raw Blender Python code inside ```python.");
                break;
            case 4: // Worker 4: Cinematics, Optical Motion Blur & Lighting
            default:
                sb.append("\nTASK: WORKER 4 (CINEMATICS, MOTION BLUR & LIGHTING)\n")
                  .append("- Position wide-angle camera (18mm - 24mm) low to the ground (0.2m) beside rear-left wheel, looking forward.\n")
                  .append("- Parent camera to vehicle chassis so it tracks perfectly with movement.\n")
                  .append("- Configure Depth of Field (f/2.8) locked on the rear wheel.\n")
                  .append("- CRITICAL: Enable Motion Blur in render settings (`scene.render.use_motion_blur = True`, shutter=0.5) to produce authentic speed streaks.\n")
                  .append("- Set color management look using Blender 4.2 AgX enums: `scene.view_settings.look = 'AgX - High Contrast'`. NEVER use legacy 'High Contrast'.\n")
                  .append("- Add low-elevation Sun light (18-25 deg) for golden rim highlights and render MP4 preview.\n")
                  .append("- Output raw Blender Python code inside ```python.");
                break;
        }

        return sb.toString();
    }

    /**
     * Determines whether a path or URI points to a 3D model rather than a 2D image.
     */
    private static boolean is3DModelUri(String uriOrPath) {
        if (uriOrPath == null || uriOrPath.trim().isEmpty()) return false;
        String lower = uriOrPath.toLowerCase(Locale.US);
        return lower.startsWith("model:")
                || lower.endsWith(".fbx")
                || lower.endsWith(".glb")
                || lower.endsWith(".gltf")
                || lower.endsWith(".obj")
                || lower.contains("models_cache");
    }

    /**
     * Extracts a human-readable asset filename from a model URI or path.
     */
    private static String extractModelName(String uriOrPath) {
        if (uriOrPath == null) return "Imported Model";
        String clean = uriOrPath;
        if (clean.startsWith("model:")) clean = clean.substring(6);
        int lastSlash = clean.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < clean.length() - 1) {
            clean = clean.substring(lastSlash + 1);
        }
        return clean;
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

            if (options.outWidth <= 0 || options.outHeight <= 0) {
                return null;
            }

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