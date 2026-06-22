package com.whisent.ourthoughtsareone.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.whisent.ourthoughtsareone.entity.FlightLinkEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Renders the flight link beam between two players.
 * Follows guardian-beam-style local-space rendering:
 * - Entity position = beam start (owner link point)
 * - Beam is drawn in local space along the offset vector toward the target
 * - No manual camera subtraction; relies on the entity render pipeline's own translation
 */
public class FlightLinkRenderer extends EntityRenderer<FlightLinkEntity, FlightLinkRenderer.FlightLinkRenderState> {
    private static final Identifier BEAM_TEXTURE = Identifier.withDefaultNamespace("textures/entity/guardian/guardian_beam.png");
    private static final RenderType BEAM_RENDER_TYPE = RenderTypes.entityCutout(BEAM_TEXTURE);

    private static final float BEAM_RADIUS = 0.35F;
    private static final int BEAM_RED = 64;
    private static final int BEAM_GREEN = 200;
    private static final int BEAM_BLUE = 255;
    private static final int BEAM_ALPHA = 255;

    public FlightLinkRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(FlightLinkEntity entity, net.minecraft.client.renderer.culling.Frustum culler, double camX, double camY, double camZ) {
        if (!entity.areEndpointsValid()) {
            return false;
        }
        AABB bounds = entity.getBeamBounds(1.0F);
        return culler.isVisible(bounds);
    }

    @Override
    public FlightLinkRenderState createRenderState() {
        return new FlightLinkRenderState();
    }

    @Override
    public void extractRenderState(FlightLinkEntity entity, FlightLinkRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);

        // If endpoints aren't valid this tick, skip rendering entirely
        if (!entity.areEndpointsValid()) {
            state.valid = false;
            return;
        }

        Vec3 beamOffset = entity.getBeamOffset(partialTicks);
        if (beamOffset == null) {
            state.valid = false;
            return;
        }

        float length = (float) beamOffset.length();
        if (!Float.isFinite(length) || length < 0.1F) {
            state.valid = false;
            return;
        }

        Vec3 direction = beamOffset.normalize();
        if (!isFiniteVec(direction)) {
            state.valid = false;
            return;
        }

        state.valid = true;
        state.beamLength = length;
        state.dirX = direction.x;
        state.dirY = direction.y;
        state.dirZ = direction.z;
        state.time = entity.tickCount + partialTicks;
    }

    @Override
    public void submit(FlightLinkRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        super.submit(state, poseStack, submitNodeCollector, camera);
        if (!state.valid) return;

        float length = state.beamLength;
        double dirY = state.dirY;

        // Compute rotation angles from the direction vector
        float xRot = (float) Math.acos(Mth.clamp(dirY, -1.0, 1.0));
        float yRot = (float) (Math.PI / 2) - (float) Math.atan2(state.dirZ, state.dirX);

        poseStack.pushPose();

        // Rotate the local coordinate system so +Y axis points along the beam direction
        poseStack.mulPose(Axis.YP.rotationDegrees(yRot * Mth.RAD_TO_DEG));
        poseStack.mulPose(Axis.XP.rotationDegrees(xRot * Mth.RAD_TO_DEG));

        // UV scrolling animation
        float time = state.time;
        float rot = time * 0.05F * -1.5F;
        float texVOffset = time * 0.5F % 1.0F;
        float minV = -1.0F + texVOffset;
        float maxV = minV + length * 2.5F;

        // Generate 4 quad corners rotated around the beam axis
        float wx = Mth.cos(rot + (float) Math.PI) * BEAM_RADIUS;
        float wz = Mth.sin(rot + (float) Math.PI) * BEAM_RADIUS;
        float ex = Mth.cos(rot) * BEAM_RADIUS;
        float ez = Mth.sin(rot) * BEAM_RADIUS;
        float nx = Mth.cos(rot + Mth.HALF_PI) * BEAM_RADIUS;
        float nz = Mth.sin(rot + Mth.HALF_PI) * BEAM_RADIUS;
        float sx = Mth.cos(rot + Mth.HALF_PI * 3.0F) * BEAM_RADIUS;
        float sz = Mth.sin(rot + Mth.HALF_PI * 3.0F) * BEAM_RADIUS;

        submitNodeCollector.submitCustomGeometry(poseStack, BEAM_RENDER_TYPE, (pose, buffer) -> {
            // Face 1
            vertex(buffer, pose, wx, length, wz, 0.4999F, maxV);
            vertex(buffer, pose, wx, 0.0F, wz, 0.4999F, minV);
            vertex(buffer, pose, ex, 0.0F, ez, 0.0F, minV);
            vertex(buffer, pose, ex, length, ez, 0.0F, maxV);
            // Face 2
            vertex(buffer, pose, nx, length, nz, 0.4999F, maxV);
            vertex(buffer, pose, nx, 0.0F, nz, 0.4999F, minV);
            vertex(buffer, pose, sx, 0.0F, sz, 0.0F, minV);
            vertex(buffer, pose, sx, length, sz, 0.0F, maxV);
        });

        poseStack.popPose();
    }

    private static void vertex(VertexConsumer builder, PoseStack.Pose pose, float x, float y, float z, float u, float v) {
        builder.addVertex(pose, x, y, z)
                .setColor(BEAM_RED, BEAM_GREEN, BEAM_BLUE, BEAM_ALPHA)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    private static boolean isFiniteVec(Vec3 v) {
        return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }

    public static class FlightLinkRenderState extends EntityRenderState {
        public boolean valid;
        public float beamLength;
        public double dirX;
        public double dirY;
        public double dirZ;
        public float time;
    }
}
