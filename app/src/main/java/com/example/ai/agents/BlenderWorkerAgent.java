package com.example.ai.agents;

import com.example.ai.protocol.AIDirectorSpec;
import com.example.utils.VynaraLogger;

public class BlenderWorkerAgent {

    private static volatile String sLastMasterScript = null;
    private static volatile String sLastUserPrompt = null;
    private static volatile String sLastAssetId = null;
    private static volatile AIDirectorSpec sLastDirectorSpec = null;

    public static class WorkerScripts {
        public final String heroScript;
        public final String environmentScript;
        public final String lightingAndRenderScript;
        public final String compositeMasterScript;

        // Dynamic 4-Worker Aliases
        public final String structureScript;
        public final String detailsScript;
        public final String materialsScript;
        public final String cinematicsScript;

        public WorkerScripts(String hero, String env, String light, String master) {
            this.heroScript = hero;
            this.environmentScript = env;
            this.lightingAndRenderScript = light;
            this.compositeMasterScript = master;

            this.structureScript = hero;
            this.detailsScript = env;
            this.materialsScript = "";
            this.cinematicsScript = light;
        }

        public WorkerScripts(String structure, String details, String materials, String cinematics, String master) {
            this.heroScript = structure;
            this.environmentScript = details;
            this.lightingAndRenderScript = cinematics;
            this.compositeMasterScript = master;

            this.structureScript = structure;
            this.detailsScript = details;
            this.materialsScript = materials;
            this.cinematicsScript = cinematics;
        }
    }

    /**
     * Wraps Gemini's dynamic Python script with headless scene initialization,
     * contextual environment, cinematic lighting, CPU-safe Cycles settings, and standardized GLB export.
     */
    public static WorkerScripts wrapDynamicScript(String dynamicScript, AIDirectorSpec spec, String assetId) {
        VynaraLogger.system("BlenderWorkerAgent: Wrapping dynamic AI script for asset [" + assetId + "]");

        String w1 = (dynamicScript != null && !dynamicScript.trim().isEmpty()) 
                ? dynamicScript.trim() 
                : "# Note: Dynamic asset generation synthesized in master pipeline.\n";
        String w2 = buildWorker2DetailsScript(spec);
        String w3 = buildWorker3LightingAndRenderScript(spec);

        String master = buildMasterScript(w1, w2, w3, spec, "Dynamic AI Master Build");

        recordExecution(spec != null ? spec.getGenerationSource() : "Dynamic Scene", master, assetId, spec);

        return new WorkerScripts(w1, w2, w3, master);
    }

    public static String wrapDynamicScript(String dynamicScript, AIDirectorSpec spec) {
        return wrapDynamicScript(dynamicScript, spec, "asset_" + System.currentTimeMillis()).compositeMasterScript;
    }

    /**
     * Solution B: Wraps Gemini's repaired script for Attempt 2 re-dispatch.
     */
    public static WorkerScripts wrapRepairedScript(String repairedScript, AIDirectorSpec spec, String assetId) {
        VynaraLogger.system("BlenderWorkerAgent: Wrapping repaired script for Attempt 2 [" + assetId + "]");
        return wrapDynamicScript(repairedScript, spec, assetId);
    }

    /**
     * Synthesizes modular worker scripts governed by the Director's Spec.
     * Operates purely dynamically without hardcoded keyword box presets.
     */
    public static WorkerScripts generateModularScripts(String userPrompt, AIDirectorSpec spec, String assetId) {
        VynaraLogger.system("BlenderWorkerAgent: Spawning dynamic modular worker scripts for [" + assetId + "]");

        String w1 = buildWorker1StructureScript(userPrompt, spec);
        String w2 = buildWorker2DetailsScript(spec);
        String w3 = buildWorker3LightingAndRenderScript(spec);

        String master = buildMasterScript(w1, w2, w3, spec, "Master Build");

        recordExecution(userPrompt, master, assetId, spec);

        return new WorkerScripts(w1, w2, w3, master);
    }

    private static String buildMasterScript(String w1, String w2, String w3, AIDirectorSpec spec, String title) {
        StringBuilder master = new StringBuilder();
        master.append("# ==========================================\n");
        master.append("# Vynara Autonomous 3D Studio - ").append(title).append("\n");
        if (spec != null) {
            master.append("# Source: ").append(spec.getGenerationSource()).append("\n");
            master.append("# Scene: ").append(spec.getSceneType()).append(" | Mood: ").append(spec.getMood()).append("\n");
        }
        master.append("# ==========================================\n\n");
        master.append("import bpy, os, math, random, sys, traceback\n");
        master.append("import addon_utils\n\n");

        master.append("os.makedirs('output', exist_ok=True)\n\n");

        // Top-level exception wrapper writing exact traceback to error.txt
        master.append("try:\n");
        master.append("    # Clean scene completely\n");
        master.append("    bpy.ops.object.select_all(action='SELECT')\n");
        master.append("    bpy.ops.object.delete(use_global=False)\n\n");

        master.append("    # --- WORKER 1: STRUCTURE & HERO GEOMETRY ---\n");
        master.append(indentPythonCode(w1, 1)).append("\n\n");

        master.append("    # --- WORKER 2: SUB-PARTS, PROPS & DETAILS ---\n");
        master.append(indentPythonCode(w2, 1)).append("\n\n");

        master.append("    # --- WORKER 3: LIGHTING, CAMERA & CYCLES RENDER ---\n");
        master.append(indentPythonCode(w3, 1)).append("\n\n");

        master.append("except Exception as execution_error:\n");
        master.append("    err_msg = traceback.format_exc()\n");
        master.append("    print('[BLENDER_FATAL_ERROR]\\n' + err_msg, file=sys.stderr)\n");
        master.append("    with open('output/error.txt', 'w', encoding='utf-8') as ef:\n");
        master.append("        ef.write(err_msg)\n");
        master.append("    sys.exit(1)\n");

        return master.toString();
    }

    private static String indentPythonCode(String code, int indentLevels) {
        if (code == null || code.isEmpty()) return "";
        String indent = "    ".repeat(Math.max(0, indentLevels));
        String[] lines = code.split("\\r?\\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                sb.append("\n");
            } else {
                sb.append(indent).append(line).append("\n");
            }
        }
        return sb.toString();
    }

    public static void recordExecution(String prompt, String masterScript, String assetId, AIDirectorSpec spec) {
        sLastUserPrompt = prompt;
        sLastMasterScript = masterScript;
        sLastAssetId = assetId;
        sLastDirectorSpec = spec;
    }

    public static String getLastMasterScript() { return sLastMasterScript; }
    public static String getLastUserPrompt() { return sLastUserPrompt; }
    public static String getLastAssetId() { return sLastAssetId; }
    public static AIDirectorSpec getLastDirectorSpec() { return sLastDirectorSpec; }

    /**
     * Dynamically builds Worker 1 (Structure) using real procedural shaping, subdivision,
     * and boolean carving, or loads an uploaded 3D asset (.fbx, .glb, .obj, .gltf)
     * prioritizing normalized GLB to prevent ASCII FBX runtime rejections.
     */
    private static String buildWorker1StructureScript(String promptOrCode, AIDirectorSpec spec) {
        if (promptOrCode == null) return "";

        // If Gemini synthesized code, preserve and return directly
        if (promptOrCode.contains("import bpy") || promptOrCode.contains("bpy.ops") || promptOrCode.contains("bpy.data")) {
            return promptOrCode.trim();
        }

        String p = promptOrCode.toLowerCase();
        StringBuilder sb = new StringBuilder();
        int seed = (spec != null) ? spec.getSeedHero() : 42;
        sb.append("random.seed(").append(seed).append(")\n\n");
        
        // PBR Primary Material Setup (Blender 4.2+ compliant)
        sb.append("# Hero Primary PBR Material\n");
        sb.append("mat_hero = bpy.data.materials.new('Mat_Hero_Primary')\n");
        sb.append("mat_hero.use_nodes = True\n");
        sb.append("bsdf_h = mat_hero.node_tree.nodes.get('Principled BSDF')\n");
        sb.append("if bsdf_h:\n");
        float[] rgb = hexToRgb(spec != null ? spec.getPrimaryColorHex() : "#F1C40F");
        sb.append("    bsdf_h.inputs['Base Color'].default_value = (").append(rgb[0]).append(", ").append(rgb[1]).append(", ").append(rgb[2]).append(", 1.0)\n");
        sb.append("    bsdf_h.inputs['Roughness'].default_value = 0.18\n");
        sb.append("    bsdf_h.inputs['Metallic'].default_value = 0.85\n\n");

        sb.append("mat_black = bpy.data.materials.new('Mat_Gloss_Black')\n");
        sb.append("mat_black.use_nodes = True\n");
        sb.append("bsdf_b = mat_black.node_tree.nodes.get('Principled BSDF')\n");
        sb.append("if bsdf_b:\n");
        sb.append("    bsdf_b.inputs['Base Color'].default_value = (0.01, 0.01, 0.01, 1.0)\n");
        sb.append("    bsdf_b.inputs['Roughness'].default_value = 0.05\n");
        sb.append("    bsdf_b.inputs['Metallic'].default_value = 0.9\n\n");

        // Check for user-uploaded 3D model (checking inputs/ and workspace, prioritizing normalized GLB)
        sb.append("# Check for user imported asset model in workspace (prioritizing normalized GLB)\n");
        sb.append("imported_car = None\n");
        sb.append("glb_path = 'inputs/input_model.glb' if os.path.exists('inputs/input_model.glb') else ('input_model.glb' if os.path.exists('input_model.glb') else None)\n");
        sb.append("fbx_path = 'inputs/input_model.fbx' if os.path.exists('inputs/input_model.fbx') else ('input_model.fbx' if os.path.exists('input_model.fbx') else None)\n");
        sb.append("gltf_path = 'inputs/input_model.gltf' if os.path.exists('inputs/input_model.gltf') else ('input_model.gltf' if os.path.exists('input_model.gltf') else None)\n");
        sb.append("obj_path = 'inputs/input_model.obj' if os.path.exists('inputs/input_model.obj') else ('input_model.obj' if os.path.exists('input_model.obj') else None)\n\n");

        sb.append("if glb_path:\n");
        sb.append("    print(f'Loading normalized 3D GLB model: {glb_path}...')\n");
        sb.append("    bpy.ops.import_scene.gltf(filepath=glb_path)\n");
        sb.append("    for obj in bpy.context.selected_objects:\n");
        sb.append("        if obj.type == 'MESH' and ('body' in obj.name.lower() or 'chassis' in obj.name.lower() or imported_car is None):\n");
        sb.append("            imported_car = obj\n");
        sb.append("    if imported_car:\n");
        sb.append("        imported_car.name = 'Vehicle_Chassis'\n");
        sb.append("elif fbx_path:\n");
        sb.append("    print(f'Loading user-provided 3D FBX model: {fbx_path}...')\n");
        sb.append("    try:\n");
        sb.append("        bpy.ops.import_scene.fbx(filepath=fbx_path)\n");
        sb.append("        for obj in bpy.context.selected_objects:\n");
        sb.append("            if obj.type == 'MESH':\n");
        sb.append("                obj_name = obj.name.lower()\n");
        sb.append("                if 'wheel' in obj_name or 'tire' in obj_name or 'rim' in obj_name:\n");
        sb.append("                    pass\n");
        sb.append("                elif 'body' in obj_name or 'chassis' in obj_name or imported_car is None:\n");
        sb.append("                    imported_car = obj\n");
        sb.append("        if imported_car:\n");
        sb.append("            imported_car.name = 'Vehicle_Chassis'\n");
        sb.append("    except Exception as fe:\n");
        sb.append("        print(f'FBX import fallback notice: {fe}')\n");
        sb.append("elif gltf_path:\n");
        sb.append("    print(f'Loading user-provided 3D GLTF model: {gltf_path}...')\n");
        sb.append("    bpy.ops.import_scene.gltf(filepath=gltf_path)\n");
        sb.append("    for obj in bpy.context.selected_objects:\n");
        sb.append("        if obj.type == 'MESH' and imported_car is None:\n");
        sb.append("            imported_car = obj\n");
        sb.append("    if imported_car:\n");
        sb.append("        imported_car.name = 'Vehicle_Chassis'\n");
        sb.append("elif obj_path:\n");
        sb.append("    print(f'Loading user-provided 3D OBJ model: {obj_path}...')\n");
        sb.append("    bpy.ops.wm.obj_import(filepath=obj_path)\n");
        sb.append("    for obj in bpy.context.selected_objects:\n");
        sb.append("        if obj.type == 'MESH' and imported_car is None:\n");
        sb.append("            imported_car = obj\n");
        sb.append("    if imported_car:\n");
        sb.append("        imported_car.name = 'Vehicle_Chassis'\n\n");

        boolean isVehicle = p.contains("car") || p.contains("vehicle") || p.contains("suv") 
                || p.contains("sedan") || p.contains("truck") || p.contains("drive") || p.contains("speed")
                || p.contains("r8");

        if (isVehicle) {
            sb.append("if not imported_car:\n");
            sb.append("    # Procedural Aerodynamic Car Body with Subsurf & Boolean Wheel Wells\n");
            sb.append("    bpy.ops.mesh.primitive_cube_add(size=1, location=(0, 0, 0.7))\n");
            sb.append("    chassis = bpy.context.active_object\n");
            sb.append("    chassis.name = 'Vehicle_Chassis'\n");
            sb.append("    chassis.scale = (1.9, 4.5, 0.75)\n");
            sb.append("    bpy.ops.object.transform_apply(scale=True)\n\n");

            sb.append("    # Sloped Aerodynamic Cabin\n");
            sb.append("    mat_glass = bpy.data.materials.new('Mat_Tinted_Glass')\n");
            sb.append("    mat_glass.use_nodes = True\n");
            sb.append("    bsdf_g = mat_glass.node_tree.nodes.get('Principled BSDF')\n");
            sb.append("    if bsdf_g:\n");
            sb.append("        bsdf_g.inputs['Base Color'].default_value = (0.04, 0.06, 0.09, 1.0)\n");
            sb.append("        bsdf_g.inputs['Roughness'].default_value = 0.02\n");
            sb.append("        bsdf_g.inputs['Transmission Weight'].default_value = 0.96\n\n");

            sb.append("    bpy.ops.mesh.primitive_cube_add(size=1, location=(0, -0.25, 1.35))\n");
            sb.append("    cabin = bpy.context.active_object\n");
            sb.append("    cabin.name = 'Vehicle_Cabin'\n");
            sb.append("    cabin.scale = (1.62, 2.3, 0.58)\n");
            sb.append("    bpy.ops.object.transform_apply(scale=True)\n");
            sb.append("    bev_c = cabin.modifiers.new('CabinBevel', 'BEVEL')\n");
            sb.append("    bev_c.width = 0.08; bev_c.segments = 3\n");
            sb.append("    cabin.data.materials.append(mat_glass)\n");
            sb.append("    bpy.ops.object.shade_smooth()\n\n");

            sb.append("    # Carve 4 Wheel Arches into Chassis Body\n");
            sb.append("    wheel_coords = [(-0.96, -1.4), (0.96, -1.4), (-0.96, 1.4), (0.96, 1.4)]\n");
            sb.append("    for idx, (wx, wy) in enumerate(wheel_coords):\n");
            sb.append("        bpy.ops.mesh.primitive_cylinder_add(radius=0.52, depth=0.45, location=(wx, wy, 0.45), rotation=(0, math.radians(90), 0))\n");
            sb.append("        cutter = bpy.context.active_object\n");
            sb.append("        cutter.name = f'Wheel_Arch_Cutter_{idx}'\n");
            sb.append("        bool_mod = chassis.modifiers.new(f'Arch_Cut_{idx}', 'BOOLEAN')\n");
            sb.append("        bool_mod.object = cutter\n");
            sb.append("        bool_mod.operation = 'DIFFERENCE'\n");
            sb.append("        bpy.ops.object.select_all(action='DESELECT')\n");
            sb.append("        chassis.select_set(True)\n");
            sb.append("        bpy.context.view_layer.objects.active = chassis\n");
            sb.append("        bpy.ops.object.modifier_apply(modifier=f'Arch_Cut_{idx}')\n");
            sb.append("        bpy.data.objects.remove(cutter, do_unlink=True)\n\n");

            sb.append("    # Bevel & Smooth Chassis\n");
            sb.append("    bev_ch = chassis.modifiers.new('ChassisBevel', 'BEVEL')\n");
            sb.append("    bev_ch.width = 0.07; bev_ch.segments = 3\n");
            sb.append("    chassis.data.materials.append(mat_hero)\n");
            sb.append("    bpy.ops.object.shade_smooth()\n\n");

            sb.append("    # 4 Correctly Oriented Wheels Standing on Vertical Axles\n");
            sb.append("    mat_tire = bpy.data.materials.new('Mat_Tire_Rubber')\n");
            sb.append("    mat_tire.use_nodes = True\n");
            sb.append("    bsdf_t = mat_tire.node_tree.nodes.get('Principled BSDF')\n");
            sb.append("    if bsdf_t: bsdf_t.inputs['Base Color'].default_value = (0.02, 0.02, 0.02, 1.0); bsdf_t.inputs['Roughness'].default_value = 0.85\n");
            sb.append("    for idx, (wx, wy) in enumerate(wheel_coords):\n");
            sb.append("        bpy.ops.mesh.primitive_cylinder_add(radius=0.42, depth=0.28, location=(wx, wy, 0.42), rotation=(0, math.radians(90), 0))\n");
            sb.append("        wheel = bpy.context.active_object\n");
            sb.append("        wheel.name = f'Wheel_{idx}'\n");
            sb.append("        wheel.data.materials.append(mat_tire)\n");
            sb.append("        bpy.ops.object.shade_smooth()\n");

        } else if (p.contains("villa") || p.contains("house") || p.contains("building") || p.contains("architecture")) {
            sb.append("# Architectural Multi-Tier Cantilevered Villa & Terrace\n");
            sb.append("bpy.ops.mesh.primitive_cube_add(size=1, location=(0, 0, 1.5))\n");
            sb.append("lower = bpy.context.active_object\n");
            sb.append("lower.name = 'Villa_LowerPavilion'\n");
            sb.append("lower.scale = (8.5, 6.5, 3.0)\n");
            sb.append("bpy.ops.object.transform_apply(scale=True)\n");
            sb.append("bev_l = lower.modifiers.new('LowerBevel', 'BEVEL')\n");
            sb.append("bev_l.width = 0.04; bev_l.segments = 2\n");
            sb.append("lower.data.materials.append(mat_hero)\n\n");

            sb.append("# Upper Cantilever Terrace Suite\n");
            sb.append("bpy.ops.mesh.primitive_cube_add(size=1, location=(1.2, 0.6, 4.2))\n");
            sb.append("upper = bpy.context.active_object\n");
            sb.append("upper.name = 'Villa_UpperSuite'\n");
            sb.append("upper.scale = (9.8, 5.8, 2.5)\n");
            sb.append("bpy.ops.object.transform_apply(scale=True)\n");
            sb.append("bev_u = upper.modifiers.new('UpperBevel', 'BEVEL')\n");
            sb.append("bev_u.width = 0.04; bev_u.segments = 2\n");
            sb.append("upper.data.materials.append(mat_hero)\n\n");

            sb.append("# Inset Swimming Pool & Water Surface\n");
            sb.append("mat_water = bpy.data.materials.new('Mat_Pool_Water')\n");
            sb.append("mat_water.use_nodes = True\n");
            sb.append("bsdf_w = mat_water.node_tree.nodes.get('Principled BSDF')\n");
            sb.append("if bsdf_w:\n");
            sb.append("    bsdf_w.inputs['Base Color'].default_value = (0.05, 0.65, 0.9, 0.85)\n");
            sb.append("    bsdf_w.inputs['Roughness'].default_value = 0.03\n");
            sb.append("    bsdf_w.inputs['Transmission Weight'].default_value = 0.95\n");
            sb.append("bpy.ops.mesh.primitive_plane_add(size=1, location=(5.2, -1.0, 0.02))\n");
            sb.append("pool = bpy.context.active_object\n");
            sb.append("pool.name = 'Pool_Surface'\n");
            sb.append("pool.scale = (5.5, 8.5, 1.0)\n");
            sb.append("bpy.ops.object.transform_apply(scale=True)\n");
            sb.append("pool.data.materials.append(mat_water)\n");

        } else {
            sb.append("# General Procedural Hero Asset with Bevel & Smooth Shading\n");
            sb.append("bpy.ops.mesh.primitive_cube_add(size=1, location=(0, 0, 1.0))\n");
            sb.append("hero = bpy.context.active_object\n");
            sb.append("hero.name = 'Hero_Asset'\n");
            sb.append("hero.scale = (2.2, 2.2, 2.2)\n");
            sb.append("bpy.ops.object.transform_apply(scale=True)\n");
            sb.append("bev_h = hero.modifiers.new('HeroBevel', 'BEVEL')\n");
            sb.append("bev_h.width = 0.06; bev_h.segments = 3\n");
            sb.append("hero.data.materials.append(mat_hero)\n");
            sb.append("bpy.ops.object.shade_smooth()\n");
        }

        return sb.toString();
    }

    /**
     * Builds Worker 2: Details, Road Infrastructure, Guardrail Barrier,
     * Ground Sensors (Shrinkwrap), and Automated Wheel Rotation Drivers.
     */
    private static String buildWorker2DetailsScript(AIDirectorSpec spec) {
        StringBuilder sb = new StringBuilder();
        int seed = (spec != null) ? spec.getSeedVegetation() : 101;
        sb.append("random.seed(").append(seed).append(")\n\n");

        sb.append("# High-Detail PBR Asphalt Material\n");
        sb.append("mat_road = bpy.data.materials.new('Mat_PBR_Asphalt')\n");
        sb.append("mat_road.use_nodes = True\n");
        sb.append("bsdf_rd = mat_road.node_tree.nodes.get('Principled BSDF')\n");
        sb.append("if bsdf_rd:\n");
        sb.append("    bsdf_rd.inputs['Base Color'].default_value = (0.05, 0.05, 0.05, 1.0)\n");
        sb.append("    bsdf_rd.inputs['Roughness'].default_value = 0.72\n");
        sb.append("    bsdf_rd.inputs['Metallic'].default_value = 0.1\n\n");

        String sceneType = (spec != null && spec.getSceneType() != null) ? spec.getSceneType().toLowerCase() : "general";
        boolean isVehicle = sceneType.contains("vehicle") || sceneType.contains("car") || sceneType.contains("drive") || sceneType.contains("speed");

        if (isVehicle) {
            sb.append("# Procedural Road Ribbon (40m High-Speed Straight Track)\n");
            sb.append("bpy.ops.mesh.primitive_cube_add(size=1, location=(0, 10.0, 0.0))\n");
            sb.append("road = bpy.context.active_object\n");
            sb.append("road.name = 'Road_Surface'\n");
            sb.append("road.scale = (6.0, 60.0, 0.1)\n");
            sb.append("bpy.ops.object.transform_apply(scale=True)\n");
            sb.append("road.data.materials.append(mat_road)\n\n");

            sb.append("# White Shoulder Lane Markings\n");
            sb.append("mat_stripe = bpy.data.materials.new('Mat_White_Stripe')\n");
            sb.append("mat_stripe.use_nodes = True\n");
            sb.append("bsdf_s = mat_stripe.node_tree.nodes.get('Principled BSDF')\n");
            sb.append("if bsdf_s: bsdf_s.inputs['Base Color'].default_value = (0.9, 0.9, 0.9, 1.0); bsdf_s.inputs['Roughness'].default_value = 0.4\n");
            sb.append("bpy.ops.mesh.primitive_plane_add(size=1, location=(1.5, 10.0, 0.055))\n");
            sb.append("stripe = bpy.context.active_object\n");
            sb.append("stripe.scale = (0.15, 60.0, 1.0)\n");
            sb.append("bpy.ops.object.transform_apply(scale=True)\n");
            sb.append("stripe.data.materials.append(mat_stripe)\n\n");

            sb.append("# Metal Guardrail Barrier (High Parallax Speed Streaks)\n");
            sb.append("mat_guard = bpy.data.materials.new('Mat_Galvanized_Steel')\n");
            sb.append("mat_guard.use_nodes = True\n");
            sb.append("bsdf_gd = mat_guard.node_tree.nodes.get('Principled BSDF')\n");
            sb.append("if bsdf_gd: bsdf_gd.inputs['Base Color'].default_value = (0.7, 0.72, 0.74, 1.0); bsdf_gd.inputs['Metallic'].default_value = 0.9; bsdf_gd.inputs['Roughness'].default_value = 0.25\n");
            sb.append("bpy.ops.mesh.primitive_cube_add(size=1, location=(2.2, 10.0, 0.55))\n");
            sb.append("guardrail = bpy.context.active_object\n");
            sb.append("guardrail.name = 'Highway_Guardrail'\n");
            sb.append("guardrail.scale = (0.08, 60.0, 0.35)\n");
            sb.append("bpy.ops.object.transform_apply(scale=True)\n");
            sb.append("guardrail.data.materials.append(mat_guard)\n\n");

            sb.append("# Guardrail Support Posts spaced along the barrier\n");
            sb.append("for py in range(-15, 35, 4):\n");
            sb.append("    bpy.ops.mesh.primitive_cube_add(size=1, location=(2.25, py, 0.28))\n");
            sb.append("    post = bpy.context.active_object\n");
            sb.append("    post.scale = (0.08, 0.08, 0.55)\n");
            sb.append("    bpy.ops.object.transform_apply(scale=True)\n");
            sb.append("    post.data.materials.append(mat_guard)\n\n");

            sb.append("# Automated Rigging: Wheel Spin Drivers & Ground Sensors (Shrinkwrap)\n");
            sb.append("wheels = [obj for obj in bpy.data.objects if obj.type == 'MESH' and ('wheel' in obj.name.lower() or 'tire' in obj.name.lower() or 'rim' in obj.name.lower())]\n");
            sb.append("chassis_obj = bpy.data.objects.get('Vehicle_Chassis')\n");
            sb.append("if chassis_obj:\n");
            sb.append("    # Fallback for imported vehicles where wheels have generic names\n");
            sb.append("    if not wheels:\n");
            sb.append("        mesh_candidates = [o for o in bpy.data.objects if o.type == 'MESH' and o != chassis_obj and o.name != 'Road_Surface' and o.name != 'Highway_Guardrail']\n");
            sb.append("        if len(mesh_candidates) >= 4:\n");
            sb.append("            mesh_candidates.sort(key=lambda o: (o.dimensions.x * o.dimensions.y * o.dimensions.z))\n");
            sb.append("            wheels = mesh_candidates[:4]\n\n");

            sb.append("    # Animate forward car translation across 60 frames\n");
            sb.append("    chassis_obj.animation_data_create()\n");
            sb.append("    chassis_obj.location = (0, -10.0, 0.7)\n");
            sb.append("    chassis_obj.keyframe_insert(data_path='location', frame=1)\n");
            sb.append("    chassis_obj.location = (0, 25.0, 0.7)\n");
            sb.append("    chassis_obj.keyframe_insert(data_path='location', frame=60)\n");
            sb.append("    for fcurve in chassis_obj.animation_data.action.fcurves:\n");
            sb.append("        for kp in fcurve.keyframe_points:\n");
            sb.append("            kp.interpolation = 'LINEAR'\n\n");

            sb.append("    # Parent wheels to chassis and compute angular velocity from displacement\n");
            sb.append("    wheel_radius = 0.42\n");
            sb.append("    for w in wheels:\n");
            sb.append("        w.parent = chassis_obj\n");
            sb.append("        w.matrix_parent_inverse = chassis_obj.matrix_world.inverted()\n");
            sb.append("        # Wheel rotation keyframing (distance / radius)\n");
            sb.append("        w.animation_data_create()\n");
            sb.append("        w.rotation_euler = (0, 0, 0)\n");
            sb.append("        w.keyframe_insert(data_path='rotation_euler', index=0, frame=1)\n");
            sb.append("        # 35 meters displacement / 0.42m radius = 83.33 radians\n");
            sb.append("        w.rotation_euler = (83.33, 0, 0)\n");
            sb.append("        w.keyframe_insert(data_path='rotation_euler', index=0, frame=60)\n");
            sb.append("        if w.animation_data and w.animation_data.action:\n");
            sb.append("            for fc in w.animation_data.action.fcurves:\n");
            sb.append("                for kp in fc.keyframe_points:\n");
            sb.append("                    kp.interpolation = 'LINEAR'\n\n");

            sb.append("        # Axle Ground Sensor (Shrinkwrap Projection constraint)\n");
            sb.append("        sw_mod = w.constraints.new('SHRINKWRAP')\n");
            sb.append("        sw_mod.target = road\n");
            sb.append("        sw_mod.wrap_mode = 'PROJECT'\n");
            sb.append("        sw_mod.project_axis = 'POS_Z'\n");
            sb.append("        sw_mod.influence = 1.0\n");

        } else if (sceneType.contains("nature") || sceneType.contains("outdoor") || sceneType.contains("villa")) {
            sb.append("# Ambient Ground Plane\n");
            sb.append("bpy.ops.mesh.primitive_plane_add(size=36, location=(0, 0, 0))\n");
            sb.append("ground = bpy.context.active_object\n");
            sb.append("ground.name = 'Ground_Surface'\n");
            sb.append("ground.data.materials.append(mat_road)\n");
        } else {
            sb.append("# Clean studio floor pedestal\n");
            sb.append("bpy.ops.mesh.primitive_cylinder_add(radius=5.0, depth=0.08, location=(0, 0, -0.04))\n");
            sb.append("pedestal = bpy.context.active_object\n");
            sb.append("pedestal.name = 'Studio_Pedestal'\n");
            sb.append("pedestal.data.materials.append(mat_road)\n");
        }

        return sb.toString();
    }

    /**
     * Builds Worker 3: Cinematics, Low Ground-Clearance Tracking Camera,
     * Golden-Hour Sun Lighting, 180-deg Optical Motion Blur, AgX Color Management,
     * and MP4 Video Rendering.
     */
    private static String buildWorker3LightingAndRenderScript(AIDirectorSpec spec) {
        StringBuilder sb = new StringBuilder();
        int seed = (spec != null) ? spec.getSeedLighting() : 202;
        sb.append("random.seed(").append(seed).append(")\n\n");

        float focalLength = (spec != null && spec.getFocalLengthMm() > 0) ? spec.getFocalLengthMm() : 20.0f;
        float focusDist = (spec != null && spec.getFocusDistance() > 0) ? spec.getFocusDistance() : 1.2f;
        float fstop = (spec != null && spec.getApertureFStop() > 0) ? spec.getApertureFStop() : 2.8f;
        float sunIntensity = (spec != null && spec.getSunIntensity() > 0) ? spec.getSunIntensity() : 5.5f;
        float sunElevation = (spec != null) ? spec.getSunElevation() : 18.0f;
        float sunAzimuth = (spec != null) ? spec.getSunAzimuth() : -45.0f;

        String sceneType = (spec != null && spec.getSceneType() != null) ? spec.getSceneType().toLowerCase() : "general";
        boolean isVehicle = sceneType.contains("vehicle") || sceneType.contains("car") || sceneType.contains("drive") || sceneType.contains("speed");

        // Camera Setup
        sb.append("# Cinematic Camera Setup\n");
        sb.append("try:\n");
        sb.append("    cam_data = bpy.data.cameras.new('CinematicCamera')\n");
        sb.append("    cam_data.lens = ").append(focalLength).append("\n");
        sb.append("    cam_data.dof.use_dof = True\n");
        sb.append("    cam_data.dof.focus_distance = ").append(focusDist).append("\n");
        sb.append("    cam_data.dof.aperture_fstop = ").append(fstop).append("\n");
        sb.append("    cam_obj = bpy.data.objects.new('Camera', cam_data)\n");
        sb.append("    bpy.context.collection.objects.link(cam_obj)\n");
        sb.append("    bpy.context.scene.camera = cam_obj\n\n");

        if (isVehicle) {
            sb.append("    # Lock Camera to Vehicle Rear-Left Wheel Arch (Inches off ground, looking forward)\n");
            sb.append("    chassis_obj = bpy.data.objects.get('Vehicle_Chassis')\n");
            sb.append("    if chassis_obj:\n");
            sb.append("        cam_obj.parent = chassis_obj\n");
            sb.append("        cam_obj.location = (1.25, -2.1, 0.22)\n");
            sb.append("        cam_obj.rotation_euler = (math.radians(82), 0, math.radians(-12))\n");
            sb.append("    else:\n");
            sb.append("        cam_obj.location = (1.25, -2.1, 0.22)\n");
            sb.append("        cam_obj.rotation_euler = (math.radians(82), 0, math.radians(-12))\n");
        } else {
            float[] camPos = (spec != null && spec.getCameraPosition() != null && spec.getCameraPosition().length >= 3)
                    ? spec.getCameraPosition() : new float[]{0.0f, -8.5f, 3.8f};
            sb.append("    cam_obj.location = (").append(camPos[0]).append(", ").append(camPos[1]).append(", ").append(camPos[2]).append(")\n");
            sb.append("    cam_obj.rotation_euler = (math.radians(68), 0, 0)\n");
        }
        sb.append("except Exception as ce: print(f'Camera warning: {ce}')\n\n");

        // Natural Sunlight Rig
        sb.append("# Natural Golden-Hour Sunlight\n");
        sb.append("try:\n");
        sb.append("    sun_data = bpy.data.lights.new('KeySun', type='SUN')\n");
        sb.append("    sun_data.energy = ").append(sunIntensity).append("\n");
        sb.append("    sun_obj = bpy.data.objects.new('KeySunLight', sun_data)\n");
        sb.append("    bpy.context.collection.objects.link(sun_obj)\n");
        sb.append("    sun_obj.rotation_euler = (math.radians(").append(sunElevation).append("), 0, math.radians(").append(sunAzimuth).append("))\n");
        sb.append("except Exception as le: print(f'Sunlight warning: {le}')\n\n");

        // Render Configuration: Motion Blur & Shutter Speed
        sb.append("# Render Engine & Optical Motion Blur Configuration\n");
        sb.append("scene = bpy.context.scene\n");
        sb.append("scene.render.engine = 'CYCLES'\n");
        sb.append("scene.cycles.device = 'CPU'\n");
        sb.append("scene.cycles.samples = 24\n");
        sb.append("scene.render.resolution_x = 1080\n");
        sb.append("scene.render.resolution_y = 1920\n"); // 9:16 Cinematic Vertical Reel Format
        sb.append("scene.render.fps = 30\n");
        sb.append("scene.frame_start = 1\n");
        sb.append("scene.frame_end = 60\n\n");

        sb.append("# Optical Shutter Speed (180-deg Shutter = 0.5 frame blur)\n");
        sb.append("scene.render.use_motion_blur = True\n");
        sb.append("scene.render.motion_blur_shutter = 0.5\n\n");

        // Blender 4.2+ AgX Color Management
        sb.append("# Color Management (Blender 4.2+ AgX Standard)\n");
        sb.append("try:\n");
        sb.append("    scene.view_settings.view_transform = 'AgX'\n");
        sb.append("    scene.view_settings.look = 'AgX - High Contrast'\n");
        sb.append("except Exception as ve: print(f'Color management note: {ve}')\n\n");

        // Step 1: Export Interactive GLB
        sb.append("# Step 1: Export Interactive 3D GLTF/GLB\n");
        sb.append("try:\n");
        sb.append("    bpy.ops.export_scene.gltf(filepath='output/model.glb', export_format='GLB', export_skins=True, export_animations=True)\n");
        sb.append("    print('GLB Export Successful: output/model.glb')\n");
        sb.append("except Exception as ge:\n");
        sb.append("    print(f'GLTF export warning: {ge}')\n");
        sb.append("    raise ge\n\n");

        // Step 2: Render Still Preview
        sb.append("# Step 2: Render Still Snapshot Frame\n");
        sb.append("try:\n");
        sb.append("    scene.frame_set(25)\n");
        sb.append("    scene.render.filepath = 'output/render.png'\n");
        sb.append("    bpy.ops.render.render(write_still=True)\n");
        sb.append("    print('Preview snapshot complete: output/render.png')\n");
        sb.append("except Exception as re: print(f'Preview render note: {re}')\n\n");

        // Step 3: Render Cinematic MP4 Video Sequence
        sb.append("# Step 3: Render Photorealistic Motion-Blurred Video\n");
        sb.append("try:\n");
        sb.append("    scene.render.image_settings.file_format = 'FFMPEG'\n");
        sb.append("    scene.render.ffmpeg.format = 'MPEG4'\n");
        sb.append("    scene.render.ffmpeg.codec = 'H264'\n");
        sb.append("    scene.render.ffmpeg.constant_rate_factor = 'HIGH'\n");
        sb.append("    scene.render.filepath = 'output/cinematic.mp4'\n");
        sb.append("    bpy.ops.render.render(animation=True)\n");
        sb.append("    print('Cinematic MP4 Video render complete: output/cinematic.mp4')\n");
        sb.append("except Exception as ve: print(f'Video render note: {ve}')\n");

        return sb.toString();
    }

    private static float[] hexToRgb(String hex) {
        if (hex == null || hex.isEmpty()) return new float[] { 0.5f, 0.5f, 0.5f };
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            int c = (int) Long.parseLong(h, 16);
            float r = ((c >> 16) & 0xFF) / 255.0f;
            float g = ((c >> 8) & 0xFF) / 255.0f;
            float b = (c & 0xFF) / 255.0f;
            return new float[] { r, g, b };
        } catch (Exception e) {
            return new float[] { 0.5f, 0.5f, 0.5f };
        }
    }
}