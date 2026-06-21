package com.whisent.ourthoughtsareone.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.whisent.ourthoughtsareone.entity.FlightLinkEntity;
import net.minecraft.client.Minecraft;
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

public class FlightLinkRenderer extends EntityRenderer<FlightLinkEntity, FlightLinkRenderer.FlightLinkRenderState> {
    private static final Identifier BEAM_TEXTURE = Identifier.withDefaultNamespace("textures/entity/guardian/guardian_beam.png");
    private static final RenderType BEAM_RENDER_TYPE = RenderTypes.entityCutout(BEAM_TEXTURE);

    public FlightLinkRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(FlightLinkEntity entity, net.minecraft.client.renderer.culling.Frustum culler, double camX, double camY, double camZ) {
        if (!entity.hasUsableLinkPoints()) {
            return super.shouldRender(entity, culler, camX, camY, camZ);
        }
        Vec3 owner = entity.getOwnerLinkPoint(1.0F);
        Vec3 target = entity.getTargetLinkPoint(1.0F);
        if (!isValid(owner) || !isValid(target)) {
            return false;
        }
        return culler.isVisible(new AABB(owner, target).inflate(1.0));
    }

    @Override
    public FlightLinkRenderState createRenderState() {
        return new FlightLinkRenderState();
    }

    @Override
    public void extractRenderState(FlightLinkEntity entity, FlightLinkRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.ownerPos = entity.getOwnerLinkPoint(partialTicks);
        state.targetPos = entity.getTargetLinkPoint(partialTicks);
        state.time = entity.tickCount + partialTicks;
        if (!entity.hasUsableLinkPoints() || !isValid(state.ownerPos) || !isValid(state.targetPos)) {
            state.ownerPos = null;
            state.targetPos = null;
        }
    }

    private static boolean isValid(Vec3 point) {
        return point != null && Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }

    @Override
    public void submit(FlightLinkRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        super.submit(state, poseStack, submitNodeCollector, camera);
        if (state.ownerPos == null || state.targetPos == null) return;

        Vec3 beamVector = state.targetPos.subtract(state.ownerPos);
        float length = (float) beamVector.length();
        if (!Float.isFinite(length) || length < 0.1F) return;
        beamVector = beamVector.normalize();
        if (!isValid(beamVector)) return;

        poseStack.pushPose();

        Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        Vec3 renderOffset = state.ownerPos.subtract(cameraPos);
        poseStack.translate(renderOffset.x, renderOffset.y, renderOffset.z);

        float xRot = (float) Math.acos(Mth.clamp(beamVector.y, -1.0, 1.0));
        float yRot = (float) (Math.PI / 2) - (float) Math.atan2(beamVector.z, beamVector.x);
        poseStack.mulPose(Axis.YP.rotationDegrees(yRot * (180.0F / (float) Math.PI)));
        poseStack.mulPose(Axis.XP.rotationDegrees(xRot * (180.0F / (float) Math.PI)));

        float time = state.time;
        float rot = time * 0.05F * -1.5F;
        float texVOff = time * 0.5F % 1.0F;
        float minV = -1.0F + texVOff;
        float maxV = minV + length * 2.5F;

        int red = 64;
        int green = 200;
        int blue = 255;

        float wx = Mth.cos(rot + (float) Math.PI) * 0.35F;
        float wz = Mth.sin(rot + (float) Math.PI) * 0.35F;
        float ex = Mth.cos(rot + 0.0F) * 0.35F;
        float ez = Mth.sin(rot + 0.0F) * 0.35F;
        float nx = Mth.cos(rot + (float) (Math.PI / 2)) * 0.35F;
        float nz = Mth.sin(rot + (float) (Math.PI / 2)) * 0.35F;
        float sx = Mth.cos(rot + (float) (Math.PI * 3.0 / 2.0)) * 0.35F;
        float sz = Mth.sin(rot + (float) (Math.PI * 3.0 / 2.0)) * 0.35F;

        submitNodeCollector.submitCustomGeometry(poseStack, BEAM_RENDER_TYPE, (pose, buffer) -> {
            vertex(buffer, pose, wx, length, wz, red, green, blue, 0.4999F, maxV);
            vertex(buffer, pose, wx, 0.0F, wz, red, green, blue, 0.4999F, minV);
            vertex(buffer, pose, ex, 0.0F, ez, red, green, blue, 0.0F, minV);
            vertex(buffer, pose, ex, length, ez, red, green, blue, 0.0F, maxV);
            vertex(buffer, pose, nx, length, nz, red, green, blue, 0.4999F, maxV);
            vertex(buffer, pose, nx, 0.0F, nz, red, green, blue, 0.4999F, minV);
            vertex(buffer, pose, sx, 0.0F, sz, red, green, blue, 0.0F, minV);
            vertex(buffer, pose, sx, length, sz, red, green, blue, 0.0F, maxV);
        });

        poseStack.popPose();
    }

    private static void vertex(VertexConsumer builder, PoseStack.Pose pose, float x, float y, float z, int red, int green, int blue, float u, float v) {
        builder.addVertex(pose, x, y, z)
                .setColor(red, green, blue, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    public static class FlightLinkRenderState extends EntityRenderState {
        public Vec3 ownerPos;
        public Vec3 targetPos;
        public float time;
    }
}
