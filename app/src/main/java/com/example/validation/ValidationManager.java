package com.example.validation;

import com.example.engine.Mesh;
import com.example.engine.Scene;
import com.example.engine.SceneObject;
import com.example.validation.validators.MaterialValidator;
import com.example.validation.validators.MeshValidator;
import com.example.validation.validators.SceneValidator;

import java.util.ArrayList;
import java.util.List;

public class ValidationManager {

    private final MeshValidator meshValidator;
    private final MaterialValidator materialValidator;
    private final SceneValidator sceneValidator;

    public ValidationManager() {
        this.meshValidator = new MeshValidator();
        this.materialValidator = new MaterialValidator();
        this.sceneValidator = new SceneValidator();
    }

    /**
     * Phase 11 Alignment: Performs comprehensive scene validation by coordinating 
     * specialized scene, hierarchy, light, and camera validators.
     */
    public List<ValidationResult> validateScene(Scene scene) {
        List<ValidationResult> results = new ArrayList<>();
        if (scene == null) {
            results.add(new ValidationResult(ValidationResult.Severity.CRITICAL, "Active scene is null.", "Initialize 3D scene."));
            return results;
        }

        // 1. Delegate overall scene layout & hierarchy validation
        results.addAll(sceneValidator.validate(scene));

        if (scene.getObjects().isEmpty()) {
            results.add(new ValidationResult(ValidationResult.Severity.WARNING, "Scene is currently empty.", "Generate geometry using Create workspace."));
            return results;
        }

        // 2. Recursively validate every object inside the scene graph
        for (SceneObject obj : scene.getFlatObjectList()) {
            results.addAll(validateObject(obj));
        }

        if (results.isEmpty()) {
            results.add(new ValidationResult(ValidationResult.Severity.PASS, "Scene validation passed cleanly with 0 errors.", null));
        }

        return results;
    }

    /**
     * Phase 11 Alignment: Coordinates detailed mesh topology, bounding box, 
     * and PBR material parameter checks on individual scene graph nodes.
     * Accurately distinguishes between geometry meshes and parent hierarchy/transform containers.
     */
    public List<ValidationResult> validateObject(SceneObject obj) {
        List<ValidationResult> results = new ArrayList<>();
        if (obj == null) return results;

        boolean isTransformContainer = (obj.getChildren() != null && !obj.getChildren().isEmpty());
        Mesh mesh = obj.getMesh();

        // 1. Validate Mesh Topology & Vertex counts
        if (mesh == null) {
            // Only flag an error if this is a leaf node intended to render geometry.
            // Empty parent/rig transform nodes (e.g. Car_Rig, CarRoot) host children and do not hold mesh buffers directly.
            if (!isTransformContainer) {
                results.add(new ValidationResult(
                    ValidationResult.Severity.ERROR,
                    "Object " + obj.getName() + " has missing 3D mesh.",
                    "Call geometry generator to rebuild mesh."
                ));
            }
        } else {
            results.addAll(meshValidator.validate(mesh, obj.getName()));
        }

        // 2. Validate Material Shading parameters
        // Transform containers without geometry meshes do not require material assignment.
        if (mesh != null) {
            if (obj.getMaterial() == null) {
                results.add(new ValidationResult(
                    ValidationResult.Severity.WARNING,
                    "Object " + obj.getName() + " has no material assigned.",
                    "Assign default material properties."
                ));
            } else {
                results.addAll(materialValidator.validate(obj.getMaterial(), obj.getName()));
            }
        }

        return results;
    }
}